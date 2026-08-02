package com.xrdesk.diagnostics

import android.content.Context
import android.util.Log
import com.xrdesk.SettingsStore
import kotlinx.coroutines.*
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
    
    private const val ENTRY_START = "---ENTRY_START---"
    private const val ENTRY_END = "---ENTRY_END---"

    private var logDir: File? = null
    private var initialized = false
    
    // Internal scope for background file operations
    private val diagScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            // Fatal logs should be immediate and synchronous
            fatal("System", "PREVIOUS SESSION CRASHED")
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
        logAsync(DiagnosticEntry.Level.INFO, tag, message)
    }

    fun warning(tag: String, message: String) {
        if (!SettingsStore.diagnosticsEnabled) return
        logAsync(DiagnosticEntry.Level.WARNING, tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (!SettingsStore.diagnosticsEnabled) return
        logAsync(DiagnosticEntry.Level.ERROR, tag, message, throwable)
    }

    fun fatal(tag: String, message: String, throwable: Throwable? = null) {
        // FATAL is synchronous to ensure it's written before process dies
        logSync(DiagnosticEntry.Level.FATAL, tag, message, throwable)
    }

    private fun logAsync(level: DiagnosticEntry.Level, tag: String, message: String, throwable: Throwable? = null) {
        // Mirror to Logcat immediately
        mirrorToLogcat(level, tag, message, throwable)
        
        // Write to file on IO thread
        diagScope.launch {
            val entry = createEntry(level, tag, message, throwable)
            writeToFile(entry)
        }
    }

    private fun logSync(level: DiagnosticEntry.Level, tag: String, message: String, throwable: Throwable? = null) {
        mirrorToLogcat(level, tag, message, throwable)
        val entry = createEntry(level, tag, message, throwable)
        writeToFile(entry)
    }

    private fun mirrorToLogcat(level: DiagnosticEntry.Level, tag: String, message: String, throwable: Throwable?) {
        val logcatTag = "XRDesk-$tag"
        when (level) {
            DiagnosticEntry.Level.INFO -> Log.i(logcatTag, message)
            DiagnosticEntry.Level.WARNING -> Log.w(logcatTag, message)
            DiagnosticEntry.Level.ERROR -> Log.e(logcatTag, message, throwable)
            DiagnosticEntry.Level.FATAL -> Log.e(logcatTag, "FATAL: $message", throwable)
        }
    }

    private fun createEntry(level: DiagnosticEntry.Level, tag: String, message: String, throwable: Throwable?): DiagnosticEntry {
        val stacktrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        return DiagnosticEntry(level = level, tag = tag, message = message, stacktrace = stacktrace)
    }

    @Synchronized
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
                fos.write("$ENTRY_START\n".toByteArray())
                fos.write(entry.toString().toByteArray())
                fos.write("\n$ENTRY_END\n".toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    suspend fun getLogs(): String = withContext(Dispatchers.IO) {
        val dir = logDir ?: return@withContext ""
        val sb = StringBuilder()
        
        fun readFile(fileName: String, header: String) {
            val file = File(dir, fileName)
            if (file.exists()) {
                sb.append("$header\n")
                sb.append(file.readText())
                sb.append("\n")
            }
        }

        readFile(PREV_LOG_FILE_NAME, "--- PREVIOUS LOG ---")
        readFile(LOG_FILE_NAME, "--- CURRENT LOG ---")
        
        sb.toString()
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDir?.listFiles()?.forEach { it.delete() }
    }

    suspend fun loadEntries(): List<DiagnosticEntry> = withContext(Dispatchers.IO) {
        val dir = logDir ?: return@withContext emptyList()
        val entries = mutableListOf<DiagnosticEntry>()
        
        fun parseFile(file: File) {
            if (!file.exists()) return
            val content = file.readText()
            val blocks = content.split("$ENTRY_START\n")
            blocks.forEach { block ->
                val entryContent = block.split("\n$ENTRY_END").firstOrNull()
                if (!entryContent.isNullOrBlank()) {
                    DiagnosticEntry.parse(entryContent)?.let { entries.add(it) }
                }
            }
        }

        parseFile(File(dir, PREV_LOG_FILE_NAME))
        parseFile(File(dir, LOG_FILE_NAME))
        
        entries.sortedByDescending { it.timestamp }
    }

    suspend fun getEntryCount(): Int = withContext(Dispatchers.IO) {
        val dir = logDir ?: return@withContext 0
        var count = 0
        fun countInFile(file: File) {
            if (!file.exists()) return
            val content = file.readText()
            // Subtract 1 because split on N items gives N+1 blocks
            count += content.split("$ENTRY_START\n").size - 1
        }
        countInFile(File(dir, PREV_LOG_FILE_NAME))
        countInFile(File(dir, LOG_FILE_NAME))
        count
    }

    suspend fun exportLogs(context: Context): File? = withContext(Dispatchers.IO) {
        if (logDir == null) return@withContext null
        val exportFile = File(context.cacheDir, "XRDesk_Diagnostics_${System.currentTimeMillis()}.txt")
        try {
            exportFile.writeText(getLogs())
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            null
        }
    }
}
