package com.example.autoglmagent.agent

import org.json.JSONArray
import org.json.JSONObject

class PromptContext {
    private val messages = mutableListOf<JSONObject>()

    fun reset() {
        messages.clear()
    }

    fun buildForStep(task: String?, observation: ScreenObservation): JSONArray {
        if (messages.isEmpty()) {
            messages.add(
                JSONObject()
                    .put("role", "system")
                    .put("content", SystemPrompt.zh)
            )
        }

        val text = if (task != null) {
            "$task\n\n${screenInfo(observation.currentApp)}"
        } else {
            "** Screen Info **\n\n${screenInfo(observation.currentApp)}"
        }

        messages.add(createUserMessage(text, observation.base64Png))
        return JSONArray(messages.map { JSONObject(it.toString()) })
    }

    fun removeLatestImage() {
        val latest = messages.lastOrNull() ?: return
        val content = latest.optJSONArray("content") ?: return
        val textOnly = JSONArray()
        for (i in 0 until content.length()) {
            val item = content.getJSONObject(i)
            if (item.optString("type") == "text") {
                textOnly.put(item)
            }
        }
        latest.put("content", textOnly)
    }

    fun addAssistant(thinking: String, action: String) {
        messages.add(
            JSONObject()
                .put("role", "assistant")
                .put("content", "<think>$thinking</think><answer>$action</answer>")
        )
    }

    private fun createUserMessage(text: String, base64Png: String): JSONObject {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/png;base64,$base64Png"),
                    )
            )
            .put(
                JSONObject()
                    .put("type", "text")
                    .put("text", text)
            )
        return JSONObject()
            .put("role", "user")
            .put("content", content)
    }

    private fun screenInfo(currentApp: String): String =
        JSONObject().put("current_app", currentApp).toString()
}
