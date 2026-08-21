package com.agent.geminibridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiAgent {
    private val client = OkHttpClient()

    suspend fun processPrompt(apiKey: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Fout: Geen API key ingevuld!"

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

        try {
            val payload = buildRequestPayload(userPrompt)
            val initialResponse = executeApiCall(endpoint, payload)
            
            if (initialResponse.startsWith("HTTP_ERROR_")) {
                return@withContext "Serverfout van Google:\n$initialResponse"
            }

            val jsonResponse = JSONObject(initialResponse)

            if (jsonResponse.has("error")) {
                val errObj = jsonResponse.getJSONObject("error")
                return@withContext "API Fout (${errObj.optInt("code")}): ${errObj.optString("message")}"
            }

            val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)

            val functionCall = firstPart?.optJSONObject("functionCall")
            if (functionCall != null && functionCall.optString("name") == "run_shell_command") {
                val commandToRun = functionCall.getJSONObject("args").getString("command")
                val executionResult = ShellBridge.execute(commandToRun)
                val toolResponsePayload = buildToolResponsePayload(userPrompt, functionCall, executionResult)
                val finalApiResponse = executeApiCall(endpoint, toolResponsePayload)
                val finalJson = JSONObject(finalApiResponse)
                
                if (finalJson.has("error")) {
                    val errObj = finalJson.getJSONObject("error")
                    return@withContext "Tool API Fout: ${errObj.optString("message")}"
                }
                
                val finalParts = finalJson.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                return@withContext finalParts?.optJSONObject(0)?.optString("text") ?: "Commando uitgevoerd:\n$executionResult"
            } else {
                return@withContext firstPart?.optString("text") ?: "Antwoord:\n$initialResponse"
            }
        } catch (e: Exception) {
            return@withContext "App Fout: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun buildRequestPayload(prompt: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("tools", JSONArray().put(
                JSONObject().put("functionDeclarations", JSONArray().put(
                    JSONObject().apply {
                        put("name", "run_shell_command")
                        put("description", "Voert een lokaal bash of Android ADB shell commando uit.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("command", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Het bash commando om uit te voeren")
                                })
                            })
                            put("required", JSONArray().put("command"))
                        })
                    }
                ))
            ))
        }
    }

    private fun buildToolResponsePayload(prompt: String, functionCall: JSONObject, toolResult: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
                put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("functionCall", functionCall))))
                put(JSONObject().put("role", "user").put("parts", JSONArray().put(
                    JSONObject().put("functionResponse", JSONObject().apply {
                        put("name", "run_shell_command")
                        put("response", JSONObject().put("result", toolResult))
                    })
                )))
            })
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
