package com.xrdesk

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens for system notifications and forwards them to the HUD.
 */
class HUDNotificationService : NotificationListenerService() {

    private val TAG = "HUD-NotificationService"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val hudEnabled = SettingsStore.hudEnabled
        val hudNotifsEnabled = SettingsStore.hudNotificationsEnabled
        Log.d(TAG, "onNotificationPosted: hudEnabled=$hudEnabled, hudNotifsEnabled=$hudNotifsEnabled")
        
        if (!hudEnabled || !hudNotifsEnabled) return

        // Filter out ongoing notifications (media, downloads, etc.) unless desirable
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val appName = packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(sbn.packageName, 0)
        ).toString()

        val icon = sbn.notification.getLargeIcon()?.loadDrawable(this) 
                   ?: packageManager.getApplicationIcon(sbn.packageName)

        val hudNotification = HUDNotification(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            icon = icon
        )

        Log.d(TAG, "Notification received from $appName: $title (HUD Notifs Enabled: ${SettingsStore.hudNotificationsEnabled})")
        HUDManager.postNotification(hudNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: track removal if needed
    }
}
