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
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processPrompt(apiKey: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Fout: API sleutel mist."
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

        try {
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            }
            
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(body).build()
            
            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext "API Fout (${response.code}): $responseData"
                
                val jsonResponse = JSONObject(responseData)
                val text = jsonResponse.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Geen tekst in antwoord."
                
                return@withContext text
            }
        } catch (e: Exception) {
            return@withContext "Fout: ${e.message}"
        }
    }
}
