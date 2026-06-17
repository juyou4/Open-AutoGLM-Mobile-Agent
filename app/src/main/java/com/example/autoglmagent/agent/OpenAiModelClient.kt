package com.example.autoglmagent.agent

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiModelClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun request(settings: ModelSettings, messages: JSONArray): ModelResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", settings.model)
                .put("stream", false)
                .put("max_tokens", 3000)
                .put("temperature", 0.0)
                .put("top_p", 0.85)
                .put("frequency_penalty", 0.2)
                .put("messages", messages)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(settings.baseUrl.trimEnd('/') + "/chat/completions")
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("模型请求失败：HTTP ${response.code} ${payload.take(300)}")
                }

                val content = JSONObject(payload)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                ActionParser.splitResponse(content)
            }
        }
}
