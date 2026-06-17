package com.example.autoglmagent.agent

import com.example.autoglmagent.service.AgentAccessibilityService
import kotlinx.coroutines.delay

class ActionExecutor(private val service: AgentAccessibilityService) {
    suspend fun execute(action: AgentAction, screenWidth: Int, screenHeight: Int): ActionResult {
        return when (action) {
            is AgentAction.Launch -> {
                val success = service.launchApp(action.app)
                delay(1400)
                ActionResult(success, message = if (success) "已启动 ${action.app}" else "未找到应用：${action.app}")
            }
            is AgentAction.Tap -> {
                val (x, y) = toPixel(action.x, action.y, screenWidth, screenHeight)
                val success = service.tap(x, y)
                delay(700)
                ActionResult(success, message = "点击 ($x,$y)")
            }
            is AgentAction.TypeText -> {
                val success = service.typeText(action.text)
                delay(700)
                ActionResult(success, message = if (success) "已输入文本" else "输入失败，请手动接管")
            }
            is AgentAction.Swipe -> {
                val (x1, y1) = toPixel(action.x1, action.y1, screenWidth, screenHeight)
                val (x2, y2) = toPixel(action.x2, action.y2, screenWidth, screenHeight)
                val success = service.swipe(x1, y1, x2, y2)
                delay(900)
                ActionResult(success, message = "滑动 ($x1,$y1) -> ($x2,$y2)")
            }
            is AgentAction.LongPress -> {
                val (x, y) = toPixel(action.x, action.y, screenWidth, screenHeight)
                val success = service.longPress(x, y)
                delay(700)
                ActionResult(success, message = "长按 ($x,$y)")
            }
            is AgentAction.DoubleTap -> {
                val (x, y) = toPixel(action.x, action.y, screenWidth, screenHeight)
                val success = service.doubleTap(x, y)
                delay(700)
                ActionResult(success, message = "双击 ($x,$y)")
            }
            is AgentAction.Wait -> {
                delay((action.seconds * 1000).toLong().coerceIn(300, 10_000))
                ActionResult(true, message = "等待 ${action.seconds} 秒")
            }
            is AgentAction.TakeOver -> ActionResult(
                success = true,
                shouldFinish = true,
                message = "需要人工接管：${action.message}",
            )
            is AgentAction.Finish -> ActionResult(
                success = true,
                shouldFinish = true,
                message = action.message,
            )
            is AgentAction.Unknown -> ActionResult(
                success = true,
                message = "记录占位动作：${action.raw.take(80)}",
            )
            AgentAction.Back -> {
                val success = service.back()
                delay(700)
                ActionResult(success, message = "返回")
            }
            AgentAction.Home -> {
                val success = service.home()
                delay(900)
                ActionResult(success, message = "回到桌面")
            }
        }
    }

    private fun toPixel(rx: Int, ry: Int, width: Int, height: Int): Pair<Int, Int> {
        val x = (rx.coerceIn(0, 999) / 1000f * width).toInt()
        val y = (ry.coerceIn(0, 999) / 1000f * height).toInt()
        return x to y
    }
}
