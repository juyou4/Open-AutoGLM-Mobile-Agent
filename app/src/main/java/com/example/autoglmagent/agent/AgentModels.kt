package com.example.autoglmagent.agent

import android.graphics.Bitmap

data class ModelSettings(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val model: String = "autoglm-phone",
    val apiKey: String = "",
    val maxSteps: Int = 12,
)

data class ScreenObservation(
    val bitmap: Bitmap,
    val base64Png: String,
    val width: Int,
    val height: Int,
    val currentApp: String,
    val isSensitive: Boolean = false,
)

data class ModelResponse(
    val thinking: String,
    val actionText: String,
    val rawContent: String,
)

data class TraceStep(
    val index: Int,
    val currentApp: String,
    val thinking: String,
    val rawAction: String,
    val parsedAction: String,
    val result: String,
)

data class AgentUiState(
    val isRunning: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val deviceLocked: Boolean = false,
    val status: String = "待机",
    val currentApp: String = "未知",
    val lastScreenshot: Bitmap? = null,
    val trace: List<TraceStep> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null,
)

data class PendingConfirmation(
    val id: Long,
    val message: String,
    val actionLabel: String,
)

sealed interface AgentAction {
    data class Launch(val app: String) : AgentAction
    data class Tap(val x: Int, val y: Int, val sensitiveMessage: String? = null) : AgentAction
    data class TypeText(val text: String) : AgentAction
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : AgentAction
    data class LongPress(val x: Int, val y: Int) : AgentAction
    data class DoubleTap(val x: Int, val y: Int) : AgentAction
    data class Wait(val seconds: Double) : AgentAction
    data class TakeOver(val message: String) : AgentAction
    data class Finish(val message: String) : AgentAction
    data class Unknown(val raw: String) : AgentAction
    data object Back : AgentAction
    data object Home : AgentAction
}

data class ActionResult(
    val success: Boolean,
    val shouldFinish: Boolean = false,
    val message: String = "",
)

fun AgentAction.shortLabel(): String = when (this) {
    is AgentAction.Launch -> "Launch($app)"
    is AgentAction.Tap -> "Tap($x,$y)"
    is AgentAction.TypeText -> "Type(${text.take(12)})"
    is AgentAction.Swipe -> "Swipe($x1,$y1->$x2,$y2)"
    is AgentAction.LongPress -> "LongPress($x,$y)"
    is AgentAction.DoubleTap -> "DoubleTap($x,$y)"
    is AgentAction.Wait -> "Wait(${seconds}s)"
    is AgentAction.TakeOver -> "TakeOver"
    is AgentAction.Finish -> "Finish"
    is AgentAction.Unknown -> "Unknown"
    AgentAction.Back -> "Back"
    AgentAction.Home -> "Home"
}
