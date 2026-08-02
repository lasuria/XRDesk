package com.xrdesk.diagnostics

import android.content.Context
import android.util.Log
import com.xrdesk.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

object DiagnosticsManager {
    private const val TAG = "XRDesk-Diag"
    private const val MAX_LOG_SIZE = 1 * 1024 * 1024 // 1MB
    private const val LOG_FILE_NAME = "latest.log"
    private const val PREV_LOG_FILE_NAME = "previous.log"
    private const val SESSION_MARKER_NAME = "session.marker"

    private var logDir: File? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        
        logDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        
        checkPreviousSession()
        createSessionMarker()
        
        info("System", "SESSION START")
        initialized = true
    }

    private fun checkPreviousSession() {
        val marker = File(logDir, SESSION_MARKER_NAME)
        if (marker.exists()) {
            fatal("System", "PREVIOUS SESSION CRASHED")
            // We don't delete it yet, we'll overwrite it when creating a new one
        }
    }

    private fun createSessionMarker() {
        try {
            File(logDir, SESSION_MARKER_NAME).apply {
                if (!exists()) createNewFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session marker", e)
        }
    }

    fun sessionEnd() {
        info("System", "SESSION END OK")
        try {
            File(logDir, SESSION_MARKER_NAME).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session marker", e)
        }
    }

    fun info(tag: String, message: String) {
        if (!SettingsStore.diagnosticsEnabled) return
        log(DiagnosticEntry.Level.INFO, tag, message)
    }

    fun warning(tag: String, message: String) {
        if (!SettingsStore.diagnosticsEnabled) return
        log(DiagnosticEntry.Level.WARNING, tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (!SettingsStore.diagnosticsEnabled) return
        log(DiagnosticEntry.Level.ERROR, tag, message, throwable)
    }

    fun fatal(tag: String, message: String, throwable: Throwable? = null) {
        // FATAL is always logged
        log(DiagnosticEntry.Level.FATAL, tag, message, throwable)
    }

    @Synchronized
    private fun log(level: DiagnosticEntry.Level, tag: String, message: String, throwable: Throwable? = null) {
        val stacktrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        
        val entry = DiagnosticEntry(level = level, tag = tag, message = message, stacktrace = stacktrace)
        
        // 1. Mirror to Logcat
        val logcatTag = "XRDesk-$tag"
        when (level) {
            DiagnosticEntry.Level.INFO -> Log.i(logcatTag, message)
            DiagnosticEntry.Level.WARNING -> Log.w(logcatTag, message)
            DiagnosticEntry.Level.ERROR -> Log.e(logcatTag, message, throwable)
            DiagnosticEntry.Level.FATAL -> Log.e(logcatTag, "FATAL: $message", throwable)
        }
        
        // 2. Save to file
        writeToFile(entry)
    }

    private fun writeToFile(entry: DiagnosticEntry) {
        val dir = logDir ?: return
        val file = File(dir, LOG_FILE_NAME)
        
        // Size rotation
        if (file.exists() && file.length() > MAX_LOG_SIZE) {
            val prevFile = File(dir, PREV_LOG_FILE_NAME)
            if (prevFile.exists()) prevFile.delete()
            file.renameTo(prevFile)
        }
        
        try {
            FileOutputStream(file, true).use { fos ->
                fos.write("---ENTRY_START---\n".toByteArray())
                fos.write(entry.toString().toByteArray())
                fos.write("\n---ENTRY_END---\n".toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogs(): String {
        val dir = logDir ?: return ""
        val sb = StringBuilder()
        
        val prevFile = File(dir, PREV_LOG_FILE_NAME)
        if (prevFile.exists()) {
            sb.append("--- PREVIOUS LOG ---\n")
            sb.append(prevFile.readText())
            sb.append("\n")
        }
        
        val currentFile = File(dir, LOG_FILE_NAME)
        if (currentFile.exists()) {
            sb.append("--- CURRENT LOG ---\n")
            sb.append(currentFile.readText())
        }
        
        return sb.toString()
    }

    fun clearLogs() {
        logDir?.listFiles()?.forEach { it.delete() }
    }

    fun loadEntries(): List<DiagnosticEntry> {
        val dir = logDir ?: return emptyList()
        val entries = mutableListOf<DiagnosticEntry>()
        
        fun parseFile(file: File) {
            if (!file.exists()) return
            val content = file.readText()
            val blocks = content.split("---ENTRY_START---\n")
            blocks.forEach { block ->
                val entryContent = block.split("\n---ENTRY_END---\n").firstOrNull()
                if (!entryContent.isNullOrBlank()) {
                    DiagnosticEntry.parse(entryContent)?.let { entries.add(it) }
                }
            }
        }

        parseFile(File(dir, PREV_LOG_FILE_NAME))
        parseFile(File(dir, LOG_FILE_NAME))
        
        return entries.sortedByDescending { it.timestamp }
    }

    fun getEntryCount(): Int {
        val dir = logDir ?: return 0
        var count = 0
        fun countInFile(file: File) {
            if (!file.exists()) return
            val content = file.readText()
            count += content.split("---ENTRY_START---\n").size - 1
        }
        countInFile(File(dir, PREV_LOG_FILE_NAME))
        countInFile(File(dir, LOG_FILE_NAME))
        return count
    }

    fun exportLogs(context: Context): File? {
        if (logDir == null) return null
        val exportFile = File(context.cacheDir, "XRDesk_Diagnostics_${System.currentTimeMillis()}.txt")
        try {
            exportFile.writeText(getLogs())
            return exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            return null
        }
    }
}
