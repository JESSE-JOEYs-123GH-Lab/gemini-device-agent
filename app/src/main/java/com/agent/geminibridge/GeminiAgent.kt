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

    private val toolsArray = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(JSONObject().apply {
        put("name", "run_shell_command")
        put("description", "Voert een Android shell commando uit.")
        put("parameters", JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("command", JSONObject().apply { put("type", "STRING") })
            })
            put("required", JSONArray().put("command"))
        })
    }))))

    suspend fun processPrompt(apiKey: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Fout: API sleutel mist."
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

        try {
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
                put("tools", toolsArray)
            }
            
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(body).build()
            
            var responseData = ""
            client.newCall(request).execute().use { response ->
                responseData = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext "API Fout (${response.code}): $responseData"
            }
            
            val jsonResponse = JSONObject(responseData)
            val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val functionCall = parts?.optJSONObject(0)?.optJSONObject("functionCall")
            
            if (functionCall != null) {
                val command = functionCall.getJSONObject("args").getString("command")
                val result = ShellBridge.execute(command)
                
                val toolPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt))))
                        put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("functionCall", functionCall))))
                        put(JSONObject().put("role", "function").put("parts", JSONArray().put(JSONObject().put("functionResponse", JSONObject().put("name", "run_shell_command").put("response", JSONObject().put("result", result))))))
                    })
                    put("tools", toolsArray)
                }
                
                val toolBody = toolPayload.toString().toRequestBody("application/json".toMediaType())
                val toolRequest = Request.Builder().url(endpoint).post(toolBody).build()
                
                var finalResponseData = ""
                client.newCall(toolRequest).execute().use { response ->
                    finalResponseData = response.body?.string() ?: ""
                    if (!response.isSuccessful) return@withContext "API Fout Tool (${response.code}): $finalResponseData"
                }
                
                val finalJson = JSONObject(finalResponseData)
                return@withContext finalJson.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: "Commando uitgevoerd:\n$result"
            }
            
            return@withContext parts?.optJSONObject(0)?.optString("text") ?: "Geen tekst in antwoord."
            
        } catch (e: Exception) {
            return@withContext "Fout: ${e.message}"
        }
    }
}
