package com.xrdesk

import android.content.res.Resources
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsLog {
    private const val MAX_LINES = 1000
    private val lines = ArrayDeque<LogEntry>()
    private var resources: Resources? = null

    data class LogEntry(val timestamp: String, val tag: String, val message: String)

    fun init(resources: Resources) {
        this.resources = resources
    }

    @Synchronized
    fun add(tag: String, message: String) {
        val timestamp = createFormatter().format(Date())
        if (lines.size >= MAX_LINES) {
            lines.removeFirst()
        }
        lines.addLast(LogEntry(timestamp, tag, message))
    }

    @Deprecated("Use add(tag, message)")
    fun add(message: String) {
        add("General", message)
    }

    @Synchronized
    fun snapshot(filterTag: String? = null, query: String? = null): List<String> {
        return lines.filter { entry ->
            (filterTag == null || entry.tag == filterTag) &&
            (query == null || entry.message.contains(query, ignoreCase = true) || entry.tag.contains(query, ignoreCase = true))
        }.map { formatLine(it.timestamp, "[${it.tag}] ${it.message}") }
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }

    @Synchronized
    fun getTags(): List<String> = lines.map { it.tag }.distinct().sorted()

    private fun createFormatter(): SimpleDateFormat {
        val pattern = requireResources().getString(R.string.diagnostics_log_time_format)
        return SimpleDateFormat(pattern, Locale.getDefault())
    }

    private fun formatLine(timestamp: String, message: String): String {
        return requireResources().getString(
            R.string.diagnostics_log_line_format,
            timestamp,
            message
        )
    }

    private fun requireResources(): Resources {
        return checkNotNull(resources) { "DiagnosticsLog not initialized" }
    }
}
