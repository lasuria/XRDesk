package com.xrdesk

import android.content.Context

class BrowserManager(private val context: Context) {

    fun formatUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return "https://www.google.com"
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://$url"
            } else {
                url = "https://www.google.com/search?q=$url"
            }
        }
        return url
    }

}
