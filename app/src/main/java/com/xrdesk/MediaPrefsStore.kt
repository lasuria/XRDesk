package com.xrdesk

import android.content.Context
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi

/**
 * Persists user preferences for media tracks (quality, audio, subtitles).
 */
@UnstableApi
object MediaPrefsStore {
    private const val PREFS_NAME = "media_prefs"
    private const val PREF_VIDEO_QUALITY = "pref_video_quality" // "Auto" or height like "1080"
    private const val PREF_AUDIO_LANG = "pref_audio_lang"
    private const val PREF_SUB_LANG = "pref_sub_lang"
    private const val PREF_SUB_ENABLED = "pref_sub_enabled"

    fun saveVideoQuality(context: Context, quality: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_VIDEO_QUALITY, quality).apply()
    }

    fun getVideoQuality(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_VIDEO_QUALITY, "Auto") ?: "Auto"
    }

    fun saveAudioLanguage(context: Context, lang: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_AUDIO_LANG, lang).apply()
    }

    fun getAudioLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_AUDIO_LANG, null)
    }

    fun saveSubtitlePreference(context: Context, enabled: Boolean, lang: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(PREF_SUB_ENABLED, enabled)
            putString(PREF_SUB_LANG, lang)
        }.apply()
    }

    fun getSubtitleLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(PREF_SUB_ENABLED, false)) {
            prefs.getString(PREF_SUB_LANG, null)
        } else {
            null
        }
    }

    fun isSubtitleEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_SUB_ENABLED, false)
    }

    /**
     * Applies saved preferences to the provided parameters builder.
     */
    fun applyToParameters(context: Context, builder: TrackSelectionParameters.Builder) {
        val audioLang = getAudioLanguage(context)
        if (audioLang != null) {
            builder.setPreferredAudioLanguage(audioLang)
        }

        val subEnabled = isSubtitleEnabled(context)
        val subLang = getSubtitleLanguage(context)
        
        builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subEnabled)
        
        if (subEnabled) {
            if (subLang != null) {
                builder.setPreferredTextLanguage(subLang)
            }
        } else {
            builder.setPreferredTextLanguage(null)
        }

        val quality = getVideoQuality(context)
        if (quality != "Auto") {
            val height = quality.toIntOrNull() ?: 0
            if (height > 0) {
                builder.setMaxVideoSize(Int.MAX_VALUE, height)
                builder.setMinVideoSize(0, height)
            }
        } else {
            builder.clearVideoSizeConstraints()
        }
    }
}
