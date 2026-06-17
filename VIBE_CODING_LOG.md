# Vibe Coding 过程记录

本 MVP 按 README 中的分层方案实现，目标是快速把 Open-AutoGLM 的手机 Agent 闭环复刻到 Android 本机。

## 1. Observation

Prompt：

```text
创建 Android AccessibilityService，提供前台包名、Android 11+ 截图、截图 base64 编码能力。
```

产物：

- `AgentAccessibilityService.observeScreen()`
- `foregroundPackage()`
- `AccessibilityService.takeScreenshot()`

验收：

- Debug 构建通过。
- 服务在 Manifest 中声明 `canRetrieveWindowContent`、`canPerformGestures`、`canTakeScreenshot`。

## 2. Action

Prompt：

```text
把 Open-AutoGLM 的 Launch/Tap/Type/Swipe/Back/Home/Wait 等动作映射到 Android 无障碍 API。
```

产物：

- `ActionExecutor`
- `dispatchGesture` 点击、滑动、长按、双击
- `performGlobalAction` 返回和 Home
- `ACTION_SET_TEXT` 与剪贴板输入兜底

验收：

- 坐标转换保持 Open-AutoGLM 的 0-999 相对坐标。
- 敏感 Tap 由 Orchestrator 暂停确认后再执行。

## 3. Model

Prompt：

```text
用 OkHttp 实现 OpenAI-compatible chat/completions 请求，消息格式与 Open-AutoGLM model/client.py 保持一致。
```

产物：

- `OpenAiModelClient`
- 默认 `https://open.bigmodel.cn/api/paas/v4`
- 默认模型 `autoglm-phone`

验收：

- 非流式 MVP 请求体包含 system prompt、image_url、screen_info。

## 4. Agent Loop

Prompt：

```text
移植 phone_agent/agent.py 的循环：截图、构造消息、调用模型、解析动作、执行动作、保存 assistant 历史，并删除旧截图。
```

产物：

- `AgentOrchestrator`
- `PromptContext`
- `ActionParser`

验收：

- 保留 `do()/finish()` DSL。
- 保留 `<think>...</think><answer>...</answer>` assistant 历史。
- 保留单截图上下文管理。
- System prompt 已按上游 18 条规则高保真迁移，仅替换 ADB Keyboard 相关输入描述为 Android 无障碍输入描述，并动态注入当天日期。

## 5. UI / Trace / Safety

Prompt：

```text
生成 Compose MVP 界面：权限引导、模型配置、任务输入、截图预览、Trace 列表、敏感操作确认和停止按钮。
```

产物：

- `AgentApp`
- DataStore 配置保存
- 敏感确认 AlertDialog
- Trace 列表

验收：

- `./gradlew assembleDebug` 构建成功。
- APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。
