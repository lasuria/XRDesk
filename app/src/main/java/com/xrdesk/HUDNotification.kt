package com.xrdesk

import android.graphics.drawable.Drawable

data class HUDNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val icon: Drawable?
)
