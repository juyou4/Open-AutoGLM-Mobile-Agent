# Android AutoGLM Agent MVP 说明

本目录已经从设计文档落成一个 Android 原生 MVP 工程，包名为 `com.example.autoglmagent`。

## 已实现范围

- Kotlin + Jetpack Compose 单 Activity 应用。
- 模型配置页：`Base URL`、`Model`、`API Key`、最大步数，使用 DataStore 本地保存。
- 无障碍服务：`AgentAccessibilityService`，声明读取窗口、执行手势、截图能力。
- 观察层：读取前台包名，Android 11+ 使用 `AccessibilityService.takeScreenshot()` 截图并转 base64 PNG。
- 模型层：OkHttp 调 OpenAI-compatible `/chat/completions`，默认智谱 `autoglm-phone`。
- Agent Loop：保留 Open-AutoGLM 的 system prompt、`do()/finish()` DSL、0-999 坐标系、单截图上下文管理。
- 动作解析：支持 `Launch`、`Tap`、`Type`、`Swipe`、`Back`、`Home`、`Wait`、`Long Press`、`Double Tap`、`Take_over`、`finish`。
- 动作执行：使用 Android `dispatchGesture`、`performGlobalAction`、`ACTION_SET_TEXT`、剪贴板兜底、启动应用 Intent。
- 安全机制：`Tap(..., message="...")` 会弹出敏感操作确认；全黑敏感页会停止自动操作；可随时停止任务。
- 前台服务：任务运行时显示“Agent 运行中”常驻通知，通知内可停止任务。
- Trace：展示每一步当前 App、模型思考、原始动作、解析动作和执行结果。

## 演示步骤

1. 用 Android Studio 打开 `android-autoglm-agent` 目录。
2. 等待 Gradle 同步，连接 Android 11+ 真机或模拟器。
3. 安装 App 后点击“权限设置”，在系统无障碍设置中开启“AutoGLM 手机 Agent”。
4. 回到 App，填写模型 API Key。
5. 使用低风险任务演示，例如：
   - `打开设置，进入 Wi-Fi 页面`
   - `打开浏览器搜索 Open-AutoGLM GitHub`
6. 点击“开始”，观察截图、模型动作和 Trace。

## 限制

- 当前 MVP 使用云端模型，不做端侧推理。
- 截图依赖 Android 11/API 30+ 的无障碍截图能力。
- 输入文本优先使用 `ACTION_SET_TEXT`，失败后尝试剪贴板粘贴；部分 App 仍可能需要人工接管。
- 未实现悬浮停止按钮；停止入口包括 App 页面按钮和前台通知按钮。
- 未实现 APK 签名发布配置，Debug APK 可用于课程演示。
