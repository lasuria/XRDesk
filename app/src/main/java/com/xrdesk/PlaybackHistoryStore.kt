package com.xrdesk

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Stores and retrieves video playback progress.
 */
object PlaybackHistoryStore {
    private const val PREFS_NAME = "playback_history"

    data class HistoryEntry(
        val url: String,
        val title: String?,
        val positionMs: Long,
        val durationMs: Long,
        val timestamp: Long
    ) {
        fun toJson(): String = JSONObject().apply {
            put("url", url)
            put("title", title)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
            put("timestamp", timestamp)
        }.toString()

        companion object {
            fun fromJson(json: String): HistoryEntry? {
                return try {
                    val obj = JSONObject(json)
                    HistoryEntry(
                        obj.getString("url"),
                        if (obj.has("title") && !obj.isNull("title")) obj.getString("title") else null,
                        obj.getLong("positionMs"),
                        obj.getLong("durationMs"),
                        obj.getLong("timestamp")
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun savePosition(context: Context, url: String, title: String?, positionMs: Long, durationMs: Long) {
        // Only save if watched for at least 10 seconds and not at the very end
        if (positionMs < 10000) return
        if (durationMs > 0 && positionMs > durationMs - 5000) return

        val entry = HistoryEntry(url, title, positionMs, durationMs, System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) { putString(url, entry.toJson()) }
    }

    fun getSavedPosition(context: Context, url: String): HistoryEntry? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(url, null) ?: return null
        val entry = HistoryEntry.fromJson(json) ?: return null
        
        // Only return if it meets the criteria for resumption
        if (entry.positionMs < 10000) return null
        if (entry.durationMs > 0 && entry.positionMs > entry.durationMs - 5000) return null
        
        return entry
    }

    fun clearPosition(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(url) }
    }
}
