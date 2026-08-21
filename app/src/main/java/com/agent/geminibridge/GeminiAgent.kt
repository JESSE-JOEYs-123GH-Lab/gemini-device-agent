package com.agent.geminibridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAgent {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processPrompt(apiKey: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"
        try {
            val payload = buildRequestPayload(userPrompt)
            val response = executeApiCall(endpoint, payload)
            if (response.startsWith("HTTP_ERROR_")) return@withContext "Netwerkfout: $response"
            val jsonResponse = JSONObject(response)
            val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val functionCall = parts?.optJSONObject(0)?.optJSONObject("functionCall")
            if (functionCall != null) {
                val command = functionCall.getJSONObject("args").getString("command")
                val result = ShellBridge.execute(command)
                val toolPayload = buildToolResponsePayload(userPrompt, functionCall, result)
                val finalResponse = executeApiCall(endpoint, toolPayload)
                return@withContext JSONObject(finalResponse).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: "Commando uitgevoerd: $result"
            }
            return@withContext parts?.optJSONObject(0)?.optString("text") ?: "Geen antwoord."
        } catch (e: Exception) { return@withContext "Fout: ${e.message}" }
    }

    private fun buildRequestPayload(prompt: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(JSONObject().apply {
                put("name", "run_shell_command"); put("description", "Voert een Android shell commando uit."); put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", JSONObject().apply { put("command", JSONObject().apply { put("type", "STRING") }) }); put("required", JSONArray().put("command")) })
            }))))
        }
    }

    private fun buildToolResponsePayload(prompt: String, functionCall: JSONObject, result: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
                put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("functionCall", functionCall))))
                put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("functionResponse", JSONObject().put("name", "run_shell_command").put("response", JSONObject().put("result", result))))))
            })
        }
    }

    private fun executeApiCall(url: String, payload: JSONObject): String {
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response -> return if (!response.isSuccessful) "HTTP_ERROR_${response.code}" else response.body?.string() ?: "" }
    }
}
