package com.agent.geminibridge

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val agent = GeminiAgent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtLog = findViewById<TextView>(R.id.txtLog)
        val edtApiKey = findViewById<EditText>(R.id.edtApiKey)
        val edtInput = findViewById<EditText>(R.id.edtInput)
        val btnSend = findViewById<Button>(R.id.btnSend)

        val prefs = getSharedPreferences("GeminiAgentPrefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "")
        edtApiKey.setText(savedKey)

        if (Shizuku.pingBinder()) {
            if (!ShellBridge.hasShizukuPermission()) {
                Shizuku.requestPermission(0)
            } else {
                txtStatus.text = "Shizuku Status: Gekoppeld"
            }
        } else {
            txtStatus.text = "Shizuku Status: Niet actief"
        }

        btnSend.setOnClickListener {
            val key = edtApiKey.text.toString().trim()
            val query = edtInput.text.toString().trim()

            if (key.isNotBlank()) {
                prefs.edit().putString("api_key", key).apply()
            }

            if (query.isNotBlank()) {
                txtLog.append("\n\nJij: $query\nGemini denkt na...")
                CoroutineScope(Dispatchers.Main).launch {
                    val reply = agent.processPrompt(key, query)
                    txtLog.append("\nGemini:\n$reply")
                    edtInput.text.clear()
                }
            }
        }
    }
}
