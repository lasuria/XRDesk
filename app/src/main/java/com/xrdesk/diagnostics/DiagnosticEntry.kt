package com.xrdesk.diagnostics

import android.os.Build
import com.xrdesk.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String,
    val stacktrace: String? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
) {
    enum class Level {
        INFO, WARNING, ERROR, FATAL
    }

    override fun toString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("${sdf.format(Date(timestamp))} | ${level.name} | $tag | $message")
        // Metadata on the next line to make parsing easier if needed, but for now we keep it simple
        sb.append("\n[META] Device: $deviceModel | Android: $androidVersion | Version: $appVersion")
        if (!stacktrace.isNullOrEmpty()) {
            sb.append("\n[STACK]\n$stacktrace")
        }
        return sb.toString()
    }

    companion object {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        fun parse(block: String): DiagnosticEntry? {
            try {
                val lines = block.trim().split("\n")
                if (lines.isEmpty()) return null

                val headerParts = lines[0].split(" | ")
                if (headerParts.size < 4) return null

                val date = sdf.parse(headerParts[0]) ?: return null
                val level = Level.valueOf(headerParts[1])
                val tag = headerParts[2]
                val message = headerParts[3]

                var deviceModel = "Unknown"
                var androidVersion = 0
                var appVersion = "Unknown"
                var stacktrace: String? = null

                if (lines.size > 1 && lines[1].startsWith("[META]")) {
                    val meta = lines[1].removePrefix("[META] ").split(" | ")
                    meta.forEach { part ->
                        when {
                            part.startsWith("Device: ") -> deviceModel = part.removePrefix("Device: ")
                            part.startsWith("Android: ") -> androidVersion = part.removePrefix("Android: ").toIntOrNull() ?: 0
                            part.startsWith("Version: ") -> appVersion = part.removePrefix("Version: ")
                        }
                    }
                }

                val stackIdx = lines.indexOf("[STACK]")
                if (stackIdx != -1 && stackIdx < lines.size - 1) {
                    stacktrace = lines.subList(stackIdx + 1, lines.size).joinToString("\n")
                }

                return DiagnosticEntry(
                    timestamp = date.time,
                    level = level,
                    tag = tag,
                    message = message,
                    stacktrace = stacktrace,
                    appVersion = appVersion,
                    androidVersion = androidVersion,
                    deviceModel = deviceModel
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}
