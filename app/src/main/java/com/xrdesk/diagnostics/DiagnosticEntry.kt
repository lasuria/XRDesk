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
        // Use unique field delimiters that are unlikely to appear in logs
        sb.append("[T]${sdf.format(Date(timestamp))}[/T]")
        sb.append("[L]${level.name}[/L]")
        sb.append("[TAG]$tag[/TAG]")
        sb.append("[MSG]$message[/MSG]")
        sb.append("[META]Device: $deviceModel | Android: $androidVersion | Version: $appVersion[/META]")
        if (!stacktrace.isNullOrEmpty()) {
            sb.append("[STACK]\n$stacktrace\n[/STACK]")
        }
        return sb.toString()
    }

    companion object {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        fun parse(block: String): DiagnosticEntry? {
            try {
                // Extracting using tags
                val timeStr = block.substringAfter("[T]", "").substringBefore("[/T]")
                val levelStr = block.substringAfter("[L]", "").substringBefore("[/L]")
                val tag = block.substringAfter("[TAG]", "").substringBefore("[/TAG]")
                val message = block.substringAfter("[MSG]", "").substringBefore("[/MSG]")
                val metaStr = block.substringAfter("[META]", "").substringBefore("[/META]")
                val stacktrace = if (block.contains("[STACK]")) {
                    block.substringAfter("[STACK]\n", "").substringBefore("\n[/STACK]")
                } else null

                if (timeStr.isEmpty() || levelStr.isEmpty()) {
                    // Fallback for older format if needed, but the user said "prefer structured"
                    // and we just started implementation, so we can ignore legacy if it's too messy.
                    // Let's try a basic regex-free parse for speed.
                    return null
                }

                val date = sdf.parse(timeStr) ?: return null
                val level = Level.valueOf(levelStr)

                var deviceModel = "Unknown"
                var androidVersion = 0
                var appVersion = "Unknown"

                if (metaStr.isNotEmpty()) {
                    val metaParts = metaStr.split(" | ")
                    metaParts.forEach { part ->
                        when {
                            part.startsWith("Device: ") -> deviceModel = part.removePrefix("Device: ")
                            part.startsWith("Android: ") -> androidVersion = part.removePrefix("Android: ").toIntOrNull() ?: 0
                            part.startsWith("Version: ") -> appVersion = part.removePrefix("Version: ")
                        }
                    }
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
