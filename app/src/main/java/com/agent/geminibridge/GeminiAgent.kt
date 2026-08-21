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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun processPrompt(apiKey: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Fout: API sleutel mist."
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

        try {
            val payload = buildRequestPayload(userPrompt)
            val response = executeApiCall(endpoint, payload)
            
            if (response.startsWith("HTTP_ERROR_")) {
                return@withContext "Google API weigert:\n$response"
            }
            
            val jsonResponse = JSONObject(response)
            if (jsonResponse.has("error")) {
                val errObj = jsonResponse.getJSONObject("error")
                return@withContext "API Fout: ${errObj.optString("message")}"
            }

            val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            
            val functionCall = parts?.optJSONObject(0)?.optJSONObject("functionCall")
            if (functionCall != null) {
                val command = functionCall.getJSONObject("args").getString("command")
                val result = ShellBridge.execute(command)
                return@withContext "Commando uitgevoerd:\n$result"
            }
            
            return@withContext parts?.optJSONObject(0)?.optString("text") ?: "Geen tekst in respons."
        } catch (e: Exception) {
            return@withContext "App Fout: ${e.message}"
        }
    }

    private fun buildRequestPayload(prompt: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        }
    }

    private fun executeApiCall(url: String, payload: JSONObject): String {
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            val resBody = response.body?.string() ?: ""
            return if (!response.isSuccessful) {
                "HTTP_ERROR_${response.code}: $resBody"
            } else {
                resBody
            }
        }
    }
}
