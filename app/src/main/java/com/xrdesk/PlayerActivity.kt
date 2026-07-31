package com.xrdesk

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xrdesk.databinding.ActivityPlayerBinding
import com.xrdesk.databinding.DialogTrackSelectionBinding
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var source: PlayableSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticsLog.add("Crash", "PlayerActivity fatal: ${throwable.message}")
            oldHandler?.uncaughtException(thread, throwable)
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()

        source = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("source", PlayableSource::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("source")
        }

        if (source == null) {
            finish()
            return
        }

        initializeSharedPlayer()
        setupCustomControls()

        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitPlayer()
            }
        })
    }

    private fun setupCustomControls() {
        val playerView = binding.playerView
        playerView.showController()
        
        playerView.findViewById<TextView>(R.id.player_title_text)?.text = 
            source?.title ?: source?.url?.substringAfterLast("/")

        playerView.findViewById<View>(R.id.player_close_btn)?.setOnClickListener { exitPlayer() }
        playerView.findViewById<View>(R.id.player_settings_btn)?.setOnClickListener { showTrackSelectionDialog() }
        
        binding.btnExitError.setOnClickListener { finish() }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initializeSharedPlayer() {
        val currentSource = source ?: return
        
        // Use shared session
        MediaSessionManager.prepare(this, currentSource)
        player = MediaSessionManager.getPlayer(this)
        binding.playerView.player = player
        
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                binding.loadingIndicator.isVisible = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                showError(error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTracksDiagnostics(tracks)
            }
        })
        
        player?.play()
    }

    private fun updateTracksDiagnostics(tracks: Tracks) {
        val videoTrack = tracks.groups.find { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { group -> 
                val format = (0 until group.length).find { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                format?.let { "${it.width}x${it.height} (${it.frameRate.toInt()}fps)" }
            } ?: "None"

        val audioTrack = tracks.groups.find { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group -> 
                val format = (0 until group.length).find { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                format?.let { it.language ?: "Unknown" }
            } ?: "None"

        val subTrack = tracks.groups.find { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            ?.let { group -> 
                val format = (0 until group.length).find { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                format?.let { it.language ?: "Unknown" }
            } ?: "Off"

        SettingsStore.activeVideoTrack = videoTrack
        SettingsStore.activeAudioTrack = audioTrack
        SettingsStore.activeSubtitleTrack = subTrack
    }

    private fun showTrackSelectionDialog() {
        val p = player ?: return
        val dialogBinding = DialogTrackSelectionBinding.inflate(layoutInflater)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        showMainSettingsMenu(dialogBinding, dialog)
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.45).toInt().coerceAtMost(dpToPx(500))
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showMainSettingsMenu(dialogBinding: DialogTrackSelectionBinding, dialog: AlertDialog) {
        val p = player ?: return
        val tracks = p.currentTracks
        
        dialogBinding.dialogHeader.isVisible = false
        dialogBinding.trackSelectionContainer.removeAllViews()
        
        addMenuRow(dialogBinding.trackSelectionContainer, getString(R.string.player_speed), 
            String.format(Locale.getDefault(), "%.2fx", p.playbackParameters.speed)) {
            showSpeedSubMenu(dialogBinding, dialog)
        }
        
        addMenuRow(dialogBinding.trackSelectionContainer, getString(R.string.player_video_quality), MediaPrefsStore.getVideoQuality(this)) {
            showQualitySubMenu(dialogBinding, dialog)
        }
        
        if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
            addMenuRow(dialogBinding.trackSelectionContainer, getString(R.string.player_audio), SettingsStore.activeAudioTrack ?: "Unknown") {
                showAudioSubMenu(dialogBinding, dialog)
            }
        }
        
        if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
            addMenuRow(dialogBinding.trackSelectionContainer, getString(R.string.player_subtitles), SettingsStore.activeSubtitleTrack ?: "Off") {
                showSubtitleSubMenu(dialogBinding, dialog)
            }
        }
    }

    private fun addMenuRow(container: android.widget.LinearLayout, title: String, value: String, onClick: () -> Unit) {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setOnClickListener { onClick() }
        }
        
        val titleText = TextView(this).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        
        val valueText = TextView(this).apply {
            text = value
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline))
        }
        
        row.addView(titleText)
        row.addView(valueText)
        container.addView(row)
    }

    private fun setupSubMenuHeader(dialogBinding: DialogTrackSelectionBinding, title: String, dialog: AlertDialog) {
        dialogBinding.dialogHeader.isVisible = true
        dialogBinding.menuTitle.text = title
        dialogBinding.btnBackMenu.setOnClickListener { showMainSettingsMenu(dialogBinding, dialog) }
        dialogBinding.trackSelectionContainer.removeAllViews()
    }

    private fun showSpeedSubMenu(dialogBinding: DialogTrackSelectionBinding, dialog: AlertDialog) {
        setupSubMenuHeader(dialogBinding, getString(R.string.player_speed), dialog)
        val p = player ?: return
        val group = RadioGroup(this)
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
            val btn = RadioButton(this).apply {
                text = if (speed == 1.0f) getString(R.string.player_speed_normal) else String.format(Locale.getDefault(), "%.2fx", speed)
                isChecked = kotlin.math.abs(p.playbackParameters.speed - speed) < 0.1f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            btn.setOnClickListener { p.setPlaybackSpeed(speed); showMainSettingsMenu(dialogBinding, dialog) }
            group.addView(btn)
        }
        dialogBinding.trackSelectionContainer.addView(group)
    }

    private fun showQualitySubMenu(dialogBinding: DialogTrackSelectionBinding, dialog: AlertDialog) {
        setupSubMenuHeader(dialogBinding, getString(R.string.player_video_quality), dialog)
        val p = player ?: return
        val videoGroup = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO } ?: return
        val group = RadioGroup(this)
        
        val autoBtn = RadioButton(this).apply {
            text = getString(R.string.player_auto)
            isChecked = p.trackSelectionParameters.maxVideoWidth == Int.MAX_VALUE
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        autoBtn.setOnClickListener {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearVideoSizeConstraints().build()
            MediaPrefsStore.saveVideoQuality(this, "Auto")
            showMainSettingsMenu(dialogBinding, dialog)
        }
        group.addView(autoBtn)

        for (i in 0 until videoGroup.length) {
            val format = videoGroup.getTrackFormat(i)
            val btn = RadioButton(this).apply {
                text = String.format(Locale.getDefault(), "%dp", format.height)
                isChecked = !autoBtn.isChecked && videoGroup.isTrackSelected(i)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            btn.setOnClickListener {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(format.width, format.height).setMinVideoSize(format.width, format.height).build()
                MediaPrefsStore.saveVideoQuality(this, format.height.toString())
                showMainSettingsMenu(dialogBinding, dialog)
            }
            group.addView(btn)
        }
        dialogBinding.trackSelectionContainer.addView(group)
    }

    private fun showAudioSubMenu(dialogBinding: DialogTrackSelectionBinding, dialog: AlertDialog) {
        setupSubMenuHeader(dialogBinding, getString(R.string.player_audio), dialog)
        val p = player ?: return
        val group = RadioGroup(this)
        p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                val btn = RadioButton(this).apply {
                    text = format.language?.let { code -> when(code.lowercase()){"ru","rus"->getString(R.string.player_lang_ru);"en","eng"->getString(R.string.player_lang_en);else->Locale(code).getDisplayLanguage(Locale.getDefault())} } ?: "Unknown"
                    isChecked = trackGroup.isTrackSelected(i)
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }
                btn.setOnClickListener {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(format.language).build()
                    MediaPrefsStore.saveAudioLanguage(this@PlayerActivity, format.language)
                    showMainSettingsMenu(dialogBinding, dialog)
                }
                group.addView(btn)
            }
        }
        dialogBinding.trackSelectionContainer.addView(group)
    }

    private fun showSubtitleSubMenu(dialogBinding: DialogTrackSelectionBinding, dialog: AlertDialog) {
        setupSubMenuHeader(dialogBinding, getString(R.string.player_subtitles), dialog)
        val p = player ?: return
        val group = RadioGroup(this)
        val textGroups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        
        val offBtn = RadioButton(this).apply {
            text = getString(R.string.player_off)
            isChecked = textGroups.none { it.isSelected }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        offBtn.setOnClickListener {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).setPreferredTextLanguage(null).build()
            MediaPrefsStore.saveSubtitlePreference(this, false, null)
            showMainSettingsMenu(dialogBinding, dialog)
        }
        group.addView(offBtn)

        textGroups.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                val btn = RadioButton(this).apply {
                    text = format.language?.let { code -> when(code.lowercase()){"ru","rus"->getString(R.string.player_lang_ru);"en","eng"->getString(R.string.player_lang_en);else->Locale(code).getDisplayLanguage(Locale.getDefault())} } ?: "Unknown"
                    isChecked = trackGroup.isTrackSelected(i)
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }
                btn.setOnClickListener {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(format.language).build()
                    MediaPrefsStore.saveSubtitlePreference(this@PlayerActivity, true, format.language)
                    showMainSettingsMenu(dialogBinding, dialog)
                }
                group.addView(btn)
            }
        }
        dialogBinding.trackSelectionContainer.addView(group)
    }

    private fun showError(error: PlaybackException) {
        binding.errorDetail.text = error.localizedMessage
        binding.errorContainer.isVisible = true
    }

    private fun exitPlayer() {
        MediaSessionManager.closePlayer(this)
        finish()
    }

    override fun onDestroy() {
        binding.playerView.player = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            MediaSessionManager.closePlayer(this)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
