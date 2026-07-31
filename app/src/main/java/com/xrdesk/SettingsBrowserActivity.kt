package com.xrdesk

import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.xrdesk.databinding.ActivitySettingsBrowserBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class SettingsBrowserActivity : BaseSettingsActivity() {

    private lateinit var binding: ActivitySettingsBrowserBinding
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_browser_title))
        applyEdgeToEdge(binding.settingsBrowserRoot)

        binding.switchAdBlockEnabled.isChecked = SettingsStore.adBlockEnabled
        binding.switchAdBlockEnabled.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setAdBlockEnabled(this, isChecked)
        }

        binding.btnCheckUpdates.setOnClickListener {
            AdBlockEngine.updateFiltersAsync(this, force = true)
        }

        AdBlockEngine.setUpdateStatusListener { status ->
            runOnUiThread {
                when (status) {
                    AdBlockEngine.UpdateStatus.CHECKING -> {
                        binding.btnCheckUpdates.isEnabled = false
                        binding.btnCheckUpdates.text = getString(R.string.settings_adblock_checking)
                    }
                    AdBlockEngine.UpdateStatus.UP_TO_DATE -> {
                        binding.btnCheckUpdates.isEnabled = true
                        binding.btnCheckUpdates.text = getString(R.string.settings_adblock_check_updates)
                        Toast.makeText(this, R.string.settings_adblock_up_to_date, Toast.LENGTH_SHORT).show()
                    }
                    AdBlockEngine.UpdateStatus.UPDATED -> {
                        binding.btnCheckUpdates.isEnabled = true
                        binding.btnCheckUpdates.text = getString(R.string.settings_adblock_check_updates)
                        Toast.makeText(this, R.string.settings_adblock_updated, Toast.LENGTH_SHORT).show()
                    }
                    AdBlockEngine.UpdateStatus.ERROR -> {
                        binding.btnCheckUpdates.isEnabled = true
                        binding.btnCheckUpdates.text = getString(R.string.settings_adblock_check_updates)
                        Toast.makeText(this, R.string.settings_adblock_error, Toast.LENGTH_SHORT).show()
                    }
                    AdBlockEngine.UpdateStatus.IDLE -> {
                        binding.btnCheckUpdates.isEnabled = true
                        binding.btnCheckUpdates.text = getString(R.string.settings_adblock_check_updates)
                    }
                }
            }
        }

        lifecycleScope.launch {
            SettingsStore.adBlockEnabledFlow.collectLatest { enabled ->
                binding.adBlockInfoContainer.isVisible = enabled
                binding.dividerAdBlock.isVisible = enabled
            }
        }

        lifecycleScope.launch {
            SettingsStore.adBlockInfoFlow.collectLatest {
                updateInfo()
            }
        }
        
        updateInfo()
    }

    private fun updateInfo() {
        binding.tvAdBlockVersion.text = SettingsStore.adBlockFilterVersion ?: "—"
        binding.tvAdBlockPublished.text = formatAdBlockDate(SettingsStore.adBlockFilterPublished)
        
        val lastUpdate = SettingsStore.adBlockLastUpdateTimestamp
        binding.tvAdBlockLastUpdate.text = if (lastUpdate > 0) {
            dateFormat.format(Date(lastUpdate))
        } else "—"
    }

    private fun formatAdBlockDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val odt = OffsetDateTime.parse(raw)
            val base = odt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val offset = odt.offset.id
            val suffix = if (offset == "Z" || offset == "+00:00" || offset == "-00:00") "UTC±0" else offset
            "$base $suffix"
        } catch (ignored: Exception) {
            raw
        }
    }

    override fun onDestroy() {
        AdBlockEngine.setUpdateStatusListener { }
        super.onDestroy()
    }
}
