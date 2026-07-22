package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.addTextChangedListener
import com.xrdesk.databinding.ActivityDiagnosticsBinding

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private var isPaused = false
    private var currentFilterTag: String? = null
    private var currentQuery: String? = null

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                updateLogs()
            }
            refreshHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.applyTheme(this)
        applyEdgeToEdgePadding(binding.root)
        
        binding.diagnosticsToolbar.setNavigationOnClickListener { finish() }
        binding.diagnosticsToolbar.inflateMenu(R.menu.diagnostics_menu)
        binding.diagnosticsToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_copy_logs -> {
                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                    val text = binding.diagnosticsText.text?.toString().orEmpty()
                    val clip = android.content.ClipData.newPlainText("Logs", text)
                    clipboard?.setPrimaryClip(clip)
                    android.widget.Toast.makeText(this, "Logs copied", android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_share_logs -> {
                    val text = binding.diagnosticsText.text?.toString().orEmpty()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(intent, "Share Logs"))
                    true
                }
                else -> false
            }
        }

        binding.etSearch.addTextChangedListener {
            currentQuery = it?.toString()?.takeIf { s -> s.isNotBlank() }
            updateLogs()
        }
        
        // Auto-focus search if filter bar is clicked or explicitly entering search mode
        binding.etSearch.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

        setupTagFilter()

        binding.btnPauseResume.setOnClickListener {
            isPaused = !isPaused
            binding.btnPauseResume.text = if (isPaused) "Resume" else "Pause"
            binding.btnPauseResume.setIconResource(if (isPaused) R.drawable.ic_play_pause else R.drawable.ic_play_pause) // Reuse icon for now
        }

        binding.btnClearLogs.setOnClickListener {
            DiagnosticsLog.clear()
            updateLogs()
        }

        binding.fabScrollBottom.setOnClickListener {
            binding.logScrollView.fullScroll(View.FOCUS_DOWN)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.etSearch.hasFocus()) {
                    binding.etSearch.clearFocus()
                    val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        refreshHandler.post(refreshRunnable)
    }

    private fun setupTagFilter() {
        val tags = mutableListOf("All")
        tags.addAll(DiagnosticsLog.getTags())
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tags)
        binding.tagFilterDropdown.setAdapter(adapter)
        binding.tagFilterDropdown.setOnItemClickListener { _, _, position, _ ->
            currentFilterTag = if (position == 0) null else tags[position]
            updateLogs()
        }
    }

    private fun updateLogs() {
        val logs = DiagnosticsLog.snapshot(currentFilterTag, currentQuery)
        val text = if (logs.isEmpty()) "No logs found" else logs.joinToString("\n")
        binding.diagnosticsText.text = text
        
        // Auto-scroll if not paused
        if (!isPaused) {
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }
}
