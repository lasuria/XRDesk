package com.xrdesk.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xrdesk.BaseSettingsActivity
import com.xrdesk.R
import com.google.android.material.textfield.TextInputEditText

class LogViewerActivity : BaseSettingsActivity() {

    private lateinit var adapter: LogAdapter
    private var allEntries = listOf<DiagnosticEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        setupToolbar(R.id.toolbar, getString(R.string.diagnostics_viewer_title))
        applyEdgeToEdge(findViewById(R.id.logViewerRoot))

        adapter = LogAdapter { showDetail(it) }
        val rvLogs = findViewById<RecyclerView>(R.id.rvLogs)
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = adapter

        loadLogs()

        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLogs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadLogs() {
        allEntries = DiagnosticsManager.loadEntries()
        adapter.submitList(allEntries)
    }

    private fun filterLogs(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allEntries)
        } else {
            val filtered = allEntries.filter { 
                it.message.contains(query, ignoreCase = true) || 
                it.tag.contains(query, ignoreCase = true) ||
                it.level.name.contains(query, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }

    private fun showDetail(entry: DiagnosticEntry) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_log_detail, null)
        
        val tvLevel = view.findViewById<TextView>(R.id.tvDetailLevel)
        val tvMetadata = view.findViewById<TextView>(R.id.tvDetailMetadata)
        val tvMessage = view.findViewById<TextView>(R.id.tvDetailMessage)
        val tvStacktrace = view.findViewById<TextView>(R.id.tvDetailStacktrace)
        val layoutStacktrace = view.findViewById<View>(R.id.layoutStacktrace)

        tvLevel.text = entry.level.name
        tvMetadata.text = "Tag: ${entry.tag}\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestamp))}\nDevice: ${entry.deviceModel}\nApp Version: ${entry.appVersion}"
        tvMessage.text = entry.message

        if (entry.stacktrace.isNullOrBlank()) {
            layoutStacktrace.visibility = View.GONE
        } else {
            layoutStacktrace.visibility = View.VISIBLE
            tvStacktrace.text = entry.stacktrace
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()

        view.findViewById<View>(R.id.btnCopyDetail).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Log Detail", entry.toString())
            cm.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}
