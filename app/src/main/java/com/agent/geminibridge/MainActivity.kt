package com.agent.geminibridge

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private lateinit var agent: GeminiAgent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        agent = GeminiAgent(apiKey)

        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtLog = findViewById<TextView>(R.id.txtLog)
        val edtInput = findViewById<EditText>(R.id.edtInput)
        val btnSend = findViewById<Button>(R.id.btnSend)

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
            val query = edtInput.text.toString()
            if (query.isNotBlank()) {
                txtLog.append("\n\nJij: " + query + "\nGemini denkt na...")
                CoroutineScope(Dispatchers.Main).launch {
                    val reply = agent.processPrompt(query)
                    txtLog.append("\nGemini:\n" + reply)
                    edtInput.text.clear()
                }
            }
        }
    }
}
