package com.xrdesk

import android.content.Context
import android.widget.Toast

/**
 * Single entry point for all Toast notifications in the app.
 * Respects global notification settings from SettingsStore.
 */
object ToastHelper {

    /**
     * Shows a standard Android Toast if notifications are enabled in settings.
     * The duration is determined by the global app setting.
     */
    fun show(context: Context, message: String) {
        if (!SettingsStore.appNotificationsEnabled) return

        val duration = if (SettingsStore.appNotificationDuration == 1) {
            Toast.LENGTH_LONG
        } else {
            Toast.LENGTH_SHORT
        }

        Toast.makeText(context.applicationContext, message, duration).show()
    }

    /**
     * Variant of show that accepts a string resource ID.
     */
    fun show(context: Context, resId: Int) {
        show(context, context.getString(resId))
    }
}
