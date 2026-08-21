package com.agent.geminibridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiAgent(private val apiKey: String) {
    private val client = OkHttpClient()
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

    suspend fun processPrompt(userPrompt: String): String = withContext(Dispatchers.IO) {
        val payload = buildRequestPayload(userPrompt)
        val initialResponse = executeApiCall(payload)
        val jsonResponse = JSONObject(initialResponse)
        val candidate = jsonResponse.optJSONArray("candidates")?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val functionCall = parts?.optJSONObject(0)?.optJSONObject("functionCall")

        if (functionCall != null && functionCall.getString("name") == "run_shell_command") {
            val commandToRun = functionCall.getJSONObject("args").getString("command")
            val executionResult = ShellBridge.execute(commandToRun)
            val toolResponsePayload = buildToolResponsePayload(userPrompt, functionCall, executionResult)
            val finalApiResponse = executeApiCall(toolResponsePayload)
            val finalJson = JSONObject(finalApiResponse)
            return@withContext finalJson.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } else {
            return@withContext parts?.optJSONObject(0)?.optString("text") ?: "Geen response ontvangen."
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

    private fun executeApiCall(payload: JSONObject): String {
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { response ->
            return response.body?.string() ?: "{}"
        }
    }
}
