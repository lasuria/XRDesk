package com.xrdesk

import android.content.Context
import android.webkit.WebView
import android.os.Build

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

    fun getExtendedDiagnostics(
        webView: WebView,
        videoDetector: LegacyVideoDetector,
        videoResolver: VideoResolverManager,
        callback: (Map<String, Any>) -> Unit
    ) {
        val diag = mutableMapOf<String, Any>()
        diag["currentUrl"] = webView.url ?: "None"
        diag["version"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        // Add more diagnostics if needed from detector/resolver
        diag["candidates"] = videoResolver.candidatesCount
        
        callback(diag)
    }
}
