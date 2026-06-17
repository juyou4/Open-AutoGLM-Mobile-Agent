package com.example.autoglmagent.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autoglmagent.agent.AgentOrchestrator
import com.example.autoglmagent.agent.ModelSettings
import com.example.autoglmagent.agent.TraceStep
import com.example.autoglmagent.data.SettingsRepository
import com.example.autoglmagent.service.AgentAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AgentApp(
    settingsRepository: SettingsRepository,
    orchestrator: AgentOrchestrator,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2DD4BF),
            secondary = Color(0xFF93C5FD),
            surface = Color(0xFF101418),
            background = Color(0xFF0B0F12),
        )
    ) {
        val settings by settingsRepository.settings.collectAsState(initial = ModelSettings())
        val state by orchestrator.state.collectAsStateWithLifecycle()
        val service by AgentAccessibilityService.instanceFlow.collectAsState()
        val currentPackage by AgentAccessibilityService.currentPackageFlow.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var editable by remember(settings) { mutableStateOf(settings) }
        var task by remember { mutableStateOf("打开设置，进入 Wi-Fi 页面") }

        LaunchedEffect(service, currentPackage) {
            orchestrator.refreshServiceStatus()
        }
        LaunchedEffect(Unit) {
            while (true) {
                orchestrator.refreshServiceStatus()
                delay(1_500)
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Header(
                        accessibilityEnabled = state.accessibilityEnabled,
                        serviceConnected = state.accessibilityConnected,
                        deviceLocked = state.deviceLocked,
                        isRunning = state.isRunning,
                        onOpenAccessibility = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            ConfigPanel(
                                settings = editable,
                                onChange = { editable = it },
                                onSave = { scope.launch { settingsRepository.save(editable) } },
                            )
                        }
                        item {
                            TaskPanel(
                                task = task,
                                onTaskChange = { task = it },
                                isRunning = state.isRunning,
                                canRun = state.accessibilityEnabled && state.accessibilityConnected && !state.deviceLocked,
                                status = state.status,
                                currentApp = state.currentApp,
                                onRun = { orchestrator.runTask(task, editable) },
                                onStop = { orchestrator.stop() },
                            )
                        }
                        item {
                            ScreenshotPanel(bitmap = state.lastScreenshot)
                        }
                        item {
                            Text(
                                text = "执行 Trace",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(state.trace) { step ->
                            TraceCard(step)
                        }
                    }
                }

                state.pendingConfirmation?.let { pending ->
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("敏感操作确认") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(pending.message)
                                Text("动作：${pending.actionLabel}", color = Color(0xFFCBD5E1))
                            }
                        },
                        confirmButton = {
                            Button(onClick = { orchestrator.resolveConfirmation(true) }) {
                                Text("允许一次")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { orchestrator.resolveConfirmation(false) }) {
                                Text("拒绝")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    accessibilityEnabled: Boolean,
    serviceConnected: Boolean,
    deviceLocked: Boolean,
    isRunning: Boolean,
    onOpenAccessibility: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "AutoGLM Mobile Agent",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (accessibilityEnabled) "系统无障碍：已启用" else "系统无障碍：未启用",
                    color = if (accessibilityEnabled) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                )
                Text(
                    text = if (serviceConnected) "服务连接：已连接" else "服务连接：等待系统绑定",
                    color = if (serviceConnected) Color(0xFF86EFAC) else Color(0xFFFDE68A),
                )
                Text(
                    text = if (deviceLocked) "屏幕状态：锁屏，请先手动解锁" else "屏幕状态：已解锁",
                    color = if (deviceLocked) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                )
            }
            Button(
                onClick = onOpenAccessibility,
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
            ) {
                Text("权限设置")
            }
        }
    }
}

@Composable
private fun ConfigPanel(
    settings: ModelSettings,
    onChange: (ModelSettings) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("模型配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = { onChange(settings.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.model,
                onValueChange = { onChange(settings.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { onChange(settings.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = settings.maxSteps.toString(),
                    onValueChange = { value ->
                        onChange(settings.copy(maxSteps = value.toIntOrNull()?.coerceIn(1, 50) ?: settings.maxSteps))
                    },
                    modifier = Modifier.width(140.dp),
                    label = { Text("最大步数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onSave) {
                    Text("保存配置")
                }
            }
        }
    }
}

@Composable
private fun TaskPanel(
    task: String,
    onTaskChange: (String) -> Unit,
    isRunning: Boolean,
    canRun: Boolean,
    status: String,
    currentApp: String,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("任务控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = task,
                onValueChange = onTaskChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自然语言任务") },
                minLines = 2,
            )
            Text("状态：$status", color = Color(0xFFCBD5E1))
            Text("当前 App：$currentApp", color = Color(0xFFCBD5E1))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRun,
                    enabled = canRun && !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                ) {
                    Text("开始")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = isRunning,
                ) {
                    Text("停止")
                }
            }
        }
    }
}

@Composable
private fun ScreenshotPanel(bitmap: Bitmap?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("最新截图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F2937)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("运行任务后显示当前手机截图", color = Color(0xFF94A3B8))
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "最新截图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                )
            }
        }
    }
}

@Composable
private fun TraceCard(step: TraceStep) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("#${step.index}  ${step.parsedAction}", fontWeight = FontWeight.SemiBold)
            Text("App：${step.currentApp}", color = Color(0xFFCBD5E1))
            if (step.thinking.isNotBlank()) {
                Text("Think：${step.thinking.take(260)}", color = Color(0xFFE2E8F0))
            }
            Text("Action：${step.rawAction.take(220)}", color = Color(0xFFBAE6FD))
            Text("Result：${step.result}", color = Color(0xFFA7F3D0))
        }
    }
}
