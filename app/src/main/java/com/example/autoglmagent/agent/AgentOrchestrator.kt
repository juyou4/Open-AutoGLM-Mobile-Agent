package com.example.autoglmagent.agent

import android.content.Context
import android.util.Log
import com.example.autoglmagent.service.AccessibilityServiceStatus
import com.example.autoglmagent.service.AgentAccessibilityService
import com.example.autoglmagent.service.AgentForegroundService
import com.example.autoglmagent.service.DeviceLockStatus
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AgentOrchestrator private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelClient = OpenAiModelClient()
    private val idGenerator = AtomicLong(1)
    private var activeJob: Job? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state

    init {
        scope.launch {
            AgentAccessibilityService.instanceFlow.collect {
                refreshServiceStatus()
            }
        }
        scope.launch {
            AgentAccessibilityService.currentPackageFlow.collect { packageName ->
                val service = AgentAccessibilityService.instanceFlow.value
                if (service != null) {
                    _state.update {
                        it.copy(
                            accessibilityEnabled = true,
                            accessibilityConnected = true,
                            currentApp = packageName,
                        )
                    }
                }
            }
        }
    }

    fun runTask(task: String, settings: ModelSettings) {
        val cleanTask = task.trim()
        Log.i(TAG, "runTask requested, taskBlank=${cleanTask.isEmpty()}, active=${activeJob?.isActive == true}")
        if (cleanTask.isEmpty() || activeJob?.isActive == true) return

        activeJob = scope.launch {
            if (DeviceLockStatus.isLocked(appContext)) {
                Log.w(TAG, "runTask blocked: device is locked")
                _state.update {
                    it.copy(
                        deviceLocked = true,
                        status = "手机仍处于锁屏状态。请先手动解锁，再回到 App 点击开始。",
                    )
                }
                return@launch
            }

            val service = waitForServiceConnection()
            if (service == null) {
                val enabled = AccessibilityServiceStatus.isEnabled(appContext)
                Log.w(TAG, "runTask blocked: accessibility service missing, enabled=$enabled")
                setStatus(
                    if (enabled) {
                        "系统显示无障碍已开启，但服务尚未连接。请返回桌面后再回到 App，或关闭后重新开启该无障碍服务。"
                    } else {
                        "请先在系统设置中开启 AutoGLM 手机 Agent 无障碍服务"
                    }
                )
                return@launch
            }
            if (settings.apiKey.isBlank()) {
                Log.w(TAG, "runTask blocked: API key is blank")
                setStatus("请先填写模型 API Key")
                return@launch
            }

            val context = PromptContext()
            val executor = ActionExecutor(service)
            _state.value = AgentUiState(
                isRunning = true,
                accessibilityEnabled = true,
                accessibilityConnected = true,
                deviceLocked = false,
                status = "开始任务：$cleanTask",
                currentApp = service.foregroundPackage(),
                trace = _state.value.trace,
            )
            val foregroundStarted = AgentForegroundService.startAndWait(appContext)
            if (!foregroundStarted) {
                Log.w(TAG, "runTask blocked: foreground service did not enter foreground")
                setStatus("前台服务启动失败，请检查通知权限后重试")
                _state.update { it.copy(isRunning = false) }
                return@launch
            }

            try {
                for (step in 1..settings.maxSteps.coerceIn(1, 50)) {
                    if (activeJob?.isActive != true) break
                    setStatus("第 $step 步：截图并识别当前界面")
                    Log.i(TAG, "step=$step observeScreen begin")

                    val observation = service.observeScreen().getOrElse { error ->
                        Log.e(TAG, "step=$step observeScreen failed", error)
                        appendTrace(step, "未知", "", "", "截图失败", error.message.orEmpty())
                        setStatus("截图失败：${error.message}")
                        return@launch
                    }
                    Log.i(
                        TAG,
                        "step=$step observeScreen ok app=${observation.currentApp} size=${observation.width}x${observation.height} sensitive=${observation.isSensitive}",
                    )

                    _state.update {
                        it.copy(
                            currentApp = observation.currentApp,
                            lastScreenshot = observation.bitmap,
                        )
                    }

                    if (observation.isSensitive) {
                        appendTrace(step, observation.currentApp, "", "", "敏感页", "截图接近全黑，已停止自动操作")
                        setStatus("检测到可能的敏感页面，已停止并等待人工处理")
                        break
                    }

                    val messages = context.buildForStep(
                        task = if (step == 1) cleanTask else null,
                        observation = observation,
                    )

                    setStatus("第 $step 步：请求 AutoGLM 模型")
                    Log.i(TAG, "step=$step model request begin messages=${messages.length()}")
                    val response = modelClient.request(settings, messages)
                    val action = ActionParser.parse(response.actionText)
                    Log.i(
                        TAG,
                        "step=$step model response action=${action.shortLabel()} raw=${response.actionText.take(180)}",
                    )
                    context.removeLatestImage()
                    context.addAssistant(response.thinking, response.actionText)

                    val allowed = confirmIfNeeded(action)
                    if (!allowed) {
                        appendTrace(step, observation.currentApp, response.thinking, response.actionText, action.shortLabel(), "用户拒绝敏感操作")
                        setStatus("用户拒绝敏感操作，任务停止")
                        break
                    }

                    setStatus("第 $step 步：执行 ${action.shortLabel()}")
                    val result = executor.execute(action, observation.width, observation.height)
                    Log.i(
                        TAG,
                        "step=$step execute result action=${action.shortLabel()} success=${result.success} finish=${result.shouldFinish} message=${result.message}",
                    )
                    appendTrace(step, observation.currentApp, response.thinking, response.actionText, action.shortLabel(), result.message)

                    if (result.shouldFinish) {
                        setStatus(result.message.ifBlank { "任务完成" })
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "runTask failed", e)
                setStatus("任务异常：${e.message}")
            } finally {
                Log.i(TAG, "runTask finished; stopping foreground service")
                pendingDecision = null
                AgentForegroundService.stop(appContext)
                _state.update {
                    it.copy(
                        isRunning = false,
                        accessibilityEnabled = AgentAccessibilityService.instanceFlow.value != null ||
                            AccessibilityServiceStatus.isEnabled(appContext),
                        accessibilityConnected = AgentAccessibilityService.instanceFlow.value != null,
                        deviceLocked = DeviceLockStatus.isLocked(appContext),
                        pendingConfirmation = null,
                    )
                }
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        AgentForegroundService.stop(appContext)
        pendingDecision?.complete(false)
        pendingDecision = null
        _state.update {
            it.copy(
                isRunning = false,
                accessibilityEnabled = AgentAccessibilityService.instanceFlow.value != null ||
                    AccessibilityServiceStatus.isEnabled(appContext),
                accessibilityConnected = AgentAccessibilityService.instanceFlow.value != null,
                deviceLocked = DeviceLockStatus.isLocked(appContext),
                status = "已停止",
                pendingConfirmation = null,
            )
        }
    }

    fun resolveConfirmation(allow: Boolean) {
        pendingDecision?.complete(allow)
        pendingDecision = null
        _state.update { it.copy(pendingConfirmation = null) }
    }

    fun refreshServiceStatus() {
        val service = AgentAccessibilityService.instanceFlow.value
        val enabled = service != null || AccessibilityServiceStatus.isEnabled(appContext)
        val locked = DeviceLockStatus.isLocked(appContext)
        _state.update {
            val nextStatus = when {
                locked -> "手机仍处于锁屏状态。请先手动解锁。"
                !enabled -> "请开启无障碍服务"
                service != null && it.status.needsServiceStatusReset() -> "待机"
                else -> it.status
            }
            it.copy(
                accessibilityEnabled = enabled,
                accessibilityConnected = service != null,
                deviceLocked = locked,
                currentApp = service?.foregroundPackage() ?: if (enabled) "无障碍已开启，等待服务连接" else "无障碍服务未启用",
                status = nextStatus,
            )
        }
    }

    private fun String.needsServiceStatusReset(): Boolean =
        this == "请开启无障碍服务" ||
            contains("等待服务连接") ||
            startsWith("系统显示无障碍已开启") ||
            startsWith("手机仍处于锁屏状态")

    private suspend fun waitForServiceConnection(): AgentAccessibilityService? {
        AgentAccessibilityService.instanceFlow.value?.let { return it }
        return withTimeoutOrNull(3_000) {
            AgentAccessibilityService.instanceFlow.filterNotNull().first()
        }
    }

    private suspend fun confirmIfNeeded(action: AgentAction): Boolean {
        if (action !is AgentAction.Tap || action.sensitiveMessage.isNullOrBlank()) return true

        val decision = CompletableDeferred<Boolean>()
        pendingDecision = decision
        _state.update {
            it.copy(
                pendingConfirmation = PendingConfirmation(
                    id = idGenerator.getAndIncrement(),
                    message = action.sensitiveMessage,
                    actionLabel = action.shortLabel(),
                )
            )
        }
        return decision.await()
    }

    private fun appendTrace(
        index: Int,
        currentApp: String,
        thinking: String,
        rawAction: String,
        parsedAction: String,
        result: String,
    ) {
        _state.update {
            it.copy(
                trace = (listOf(
                    TraceStep(
                        index = index,
                        currentApp = currentApp,
                        thinking = thinking,
                        rawAction = rawAction,
                        parsedAction = parsedAction,
                        result = result,
                    )
                ) + it.trace).take(80)
            )
        }
    }

    private fun setStatus(status: String) {
        _state.update { it.copy(status = status) }
    }

    companion object {
        private const val TAG = "AutoGLMOrchestrator"

        @Volatile
        private var instance: AgentOrchestrator? = null

        fun getInstance(context: Context): AgentOrchestrator =
            instance ?: synchronized(this) {
                instance ?: AgentOrchestrator(context.applicationContext).also { instance = it }
            }
    }
}
