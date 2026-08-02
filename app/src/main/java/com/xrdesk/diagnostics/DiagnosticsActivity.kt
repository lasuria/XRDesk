package com.xrdesk.diagnostics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.xrdesk.BaseSettingsActivity
import com.xrdesk.R
import java.io.OutputStream

class DiagnosticsActivity : BaseSettingsActivity() {

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { saveLogsToUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        setupToolbar(R.id.toolbar, getString(R.string.diagnostics_title))
        applyEdgeToEdge(findViewById(R.id.diagnosticsRoot))

        refreshStats()

        findViewById<android.view.View>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        findViewById<android.view.View>(R.id.btnExportLogs).setOnClickListener {
            showExportOptions()
        }

        findViewById<android.view.View>(R.id.btnClearLogs).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics_action_clear)
                .setMessage(R.string.diagnostics_clear_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DiagnosticsManager.clearLogs()
                    refreshStats()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun refreshStats() {
        findViewById<TextView>(R.id.tvLogCount).text = DiagnosticsManager.getEntryCount().toString()
        val hasCrash = DiagnosticsManager.getLogs().contains("PREVIOUS SESSION CRASHED")
        findViewById<TextView>(R.id.tvLastCrash).apply {
            text = if (hasCrash) "CRASH DETECTED" else getString(R.string.diagnostics_no_crash)
            setTextColor(if (hasCrash) 0xFFF44336.toInt() else 0xFF4CAF50.toInt())
        }
    }

    private fun showExportOptions() {
        val options = arrayOf(
            getString(R.string.diagnostics_action_save),
            getString(R.string.diagnostics_action_share)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics_export_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveLogsToDevice()
                    1 -> shareLogs()
                }
            }
            .show()
    }

    private fun saveLogsToDevice() {
        val date = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val fileName = "XRDesk_Diagnostics_$date.txt"
        saveFileLauncher.launch(fileName)
    }

    private fun saveLogsToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(DiagnosticsManager.getLogs().toByteArray())
            }
            Toast.makeText(this, getString(R.string.diagnostics_save_success, uri.path), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.diagnostics_save_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLogs() {
        val file = DiagnosticsManager.exportLogs(this)
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.diagnostics_action_export)))
        } else {
            Toast.makeText(this, R.string.diagnostics_export_error, Toast.LENGTH_SHORT).show()
        }
    }
}
