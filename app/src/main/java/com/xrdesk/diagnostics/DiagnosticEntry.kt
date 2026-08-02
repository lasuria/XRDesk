package com.xrdesk.diagnostics

import android.os.Build
import com.xrdesk.BuildConfig
import org.json.JSONObject
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

    fun toJson(): String {
        val json = JSONObject()
        json.put("t", timestamp)
        json.put("l", level.name)
        json.put("tag", tag)
        json.put("msg", message)
        json.put("ver", appVersion)
        json.put("and", androidVersion)
        json.put("dev", deviceModel)
        stacktrace?.let { json.put("st", it) }
        return json.toString()
    }

    override fun toString(): String {
        // Fallback for human readable export if needed, 
        // but for log file we will use toJson()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("${sdf.format(Date(timestamp))} | ${level.name} | $tag | $message")
        sb.append("\nDevice: $deviceModel | Android: $androidVersion | Version: $appVersion")
        if (!stacktrace.isNullOrEmpty()) {
            sb.append("\nStacktrace:\n$stacktrace")
        }
        return sb.toString()
    }

    companion object {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        fun parse(line: String): DiagnosticEntry? {
            if (line.isBlank()) return null
            
            // 1. Try JSON (New format)
            if (line.startsWith("{")) {
                try {
                    val json = JSONObject(line)
                    return DiagnosticEntry(
                        timestamp = json.getLong("t"),
                        level = Level.valueOf(json.getString("l")),
                        tag = json.getString("tag"),
                        message = json.getString("msg"),
                        appVersion = json.optString("ver", "Unknown"),
                        androidVersion = json.optInt("and", 0),
                        deviceModel = json.optString("dev", "Unknown"),
                        stacktrace = if (json.has("st")) json.getString("st") else null
                    )
                } catch (e: Exception) {
                    // Fall through to legacy
                }
            }

            // 2. Try Tag-based (Legacy RC4 format from previous refinement)
            if (line.contains("[T]")) {
                try {
                    val timeStr = line.substringAfter("[T]", "").substringBefore("[/T]")
                    val levelStr = line.substringAfter("[L]", "").substringBefore("[/L]")
                    val tag = line.substringAfter("[TAG]", "").substringBefore("[/TAG]")
                    val message = line.substringAfter("[MSG]", "").substringBefore("[/MSG]")
                    val metaStr = line.substringAfter("[META]", "").substringBefore("[/META]")
                    val stacktrace = if (line.contains("[STACK]")) {
                        line.substringAfter("[STACK]\n", "").substringBefore("\n[/STACK]")
                    } else null

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
                    // Fall through
                }
            }
            
            // 3. Try original delimiter-based (Initial v1 format)
            if (line.contains(" | ")) {
                try {
                    val parts = line.split(" | ")
                    if (parts.size >= 4) {
                        val date = sdf.parse(parts[0]) ?: return null
                        return DiagnosticEntry(
                            timestamp = date.time,
                            level = Level.valueOf(parts[1]),
                            tag = parts[2],
                            message = parts[3]
                        )
                    }
                } catch (e: Exception) {}
            }

            return null
        }
    }
}
