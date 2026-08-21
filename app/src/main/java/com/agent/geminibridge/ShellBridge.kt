package com.agent.geminibridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellBridge {
    fun hasShizukuPermission(): Boolean {
        return if (!Shizuku.pingBinder()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun execute(command: String): String {
        return try {
            // Shizuku v13 reflectie/process call
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            if (output.isEmpty()) "Uitgevoerd zonder output." else output.toString().trim()
        } catch (e: Exception) {
            "Fout: ${e.message}"
        }
    }
}
