package com.example.autoglmagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.autoglmagent.agent.ScreenObservation
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.util.Base64
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var lastPackageName: String = "未知"

    override fun onServiceConnected() {
        Log.i(TAG, "AutoGLM accessibility service connected")
        instanceFlow.value = this
        currentPackageFlow.value = foregroundPackage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            lastPackageName = pkg
            currentPackageFlow.value = pkg
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Log.i(TAG, "AutoGLM accessibility service destroyed")
        if (instanceFlow.value === this) {
            instanceFlow.value = null
        }
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

    fun foregroundPackage(): String {
        val rootPackage = rootInActiveWindow?.packageName?.toString()
        return rootPackage?.takeIf { it.isNotBlank() } ?: lastPackageName
    }

    suspend fun observeScreen(): Result<ScreenObservation> {
        val screenshot = takeBitmapScreenshot().getOrElse { return Result.failure(it) }
        val currentApp = foregroundPackage()
        return withContext(Dispatchers.Default) {
            val copy = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            if (copy !== screenshot) {
                screenshot.recycle()
            }
            Result.success(
                ScreenObservation(
                    bitmap = copy,
                    base64Png = copy.toBase64Png(),
                    width = copy.width,
                    height = copy.height,
                    currentApp = currentApp,
                    isSensitive = copy.isMostlyBlack(),
                )
            )
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGestureAwait(gesture)
    }

    suspend fun doubleTap(x: Int, y: Int): Boolean {
        val first = tap(x, y)
        kotlinx.coroutines.delay(140)
        return tap(x, y) && first
    }

    suspend fun longPress(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 900))
            .build()
        return dispatchGestureAwait(gesture)
    }

    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val distance = kotlin.math.hypot((endX - startX).toDouble(), (endY - startY).toDouble())
        val duration = distance.toLong().coerceIn(350, 1200)
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGestureAwait(gesture)
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun launchApp(appName: String): Boolean {
        val packageName = resolvePackageName(appName) ?: return false
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    fun typeText(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocusedNode()
        val setTextSuccess = focused?.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            },
        ) == true
        if (setTextSuccess) return true

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoGLM", text))
        return focused?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
    }

    private suspend fun takeBitmapScreenshot(): Result<Bitmap> = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(Result.failure(IllegalStateException("截图需要 Android 11 / API 30 及以上")))
            return@suspendCancellableCoroutine
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace,
                    )
                    screenshot.hardwareBuffer.close()
                    if (hardwareBitmap == null) {
                        cont.resume(Result.failure(IllegalStateException("系统未返回截图位图")))
                    } else {
                        cont.resume(Result.success(hardwareBitmap))
                    }
                }

                override fun onFailure(errorCode: Int) {
                    cont.resume(Result.failure(IllegalStateException("截图失败，错误码：$errorCode")))
                }
            },
        )
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        cont.resume(false)
                    }
                },
                null,
            )
        }

    private fun resolvePackageName(appName: String): String? {
        val normalized = appName.trim()
        if (normalized.contains(".")) return normalized
        APP_ALIASES[normalized]?.let { return it }

        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launchIntent, 0).firstOrNull { info ->
            val label = info.loadLabel(packageManager)?.toString().orEmpty()
            label.equals(normalized, ignoreCase = true) || label.contains(normalized, ignoreCase = true)
        }?.activityInfo?.packageName
    }

    private fun AccessibilityNodeInfo.findFocusedNode(): AccessibilityNodeInfo? {
        if (isFocused || isAccessibilityFocused) return this
        for (i in 0 until childCount) {
            val found = getChild(i)?.findFocusedNode()
            if (found != null) return found
        }
        return null
    }

    private fun Bitmap.toBase64Png(): String {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun Bitmap.isMostlyBlack(): Boolean {
        val sampleStepX = (width / 20).coerceAtLeast(1)
        val sampleStepY = (height / 20).coerceAtLeast(1)
        var dark = 0
        var total = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                if (r + g + b < 45) dark++
                total++
                x += sampleStepX
            }
            y += sampleStepY
        }
        return total > 0 && dark.toFloat() / total > 0.92f
    }

    companion object {
        private const val TAG = "AutoGLMService"
        val instanceFlow = MutableStateFlow<AgentAccessibilityService?>(null)
        val currentPackageFlow = MutableStateFlow("未知")

        private val APP_ALIASES = mapOf(
            "设置" to "com.android.settings",
            "系统设置" to "com.android.settings",
            "浏览器" to "com.android.chrome",
            "Chrome" to "com.android.chrome",
            "谷歌浏览器" to "com.android.chrome",
            "地图" to "com.google.android.apps.maps",
            "高德地图" to "com.autonavi.minimap",
            "备忘录" to "com.google.android.keep",
            "Keep" to "com.google.android.keep",
        )
    }
}
