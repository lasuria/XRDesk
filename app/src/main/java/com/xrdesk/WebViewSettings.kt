package com.xrdesk

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.xrdesk.diagnostics.DiagnosticsManager

object WebViewSettings {

    private var defaultUserAgent: String? = null

    fun getDefaultUserAgent(): String = defaultUserAgent ?: ""

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        val settings = webView.settings

        // Basic settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Media support
        settings.mediaPlaybackRequiresUserGesture = false
        
        // Caching & Storage
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // Zoom support
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // File access
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Mixed content
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Remote debugging
        WebView.setWebContentsDebuggingEnabled(true)

        // Capture default UA if not already captured
        if (defaultUserAgent == null) {
            defaultUserAgent = settings.userAgentString
        }

        // Apply saved UA mode (No reload during initial config)
        applyUserAgentMode(webView, SettingsStore.BROWSER_USER_AGENT_MODE, clearCache = false, triggerReload = false)

        configureCookies(webView)
    }

    private fun configureCookies(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    fun logDiagnostics(webView: WebView, tag: String = "XR-FIRST") {
        val display = webView.display
        val metrics = webView.resources.displayMetrics
        val settings = webView.settings
        
        val script = """
            (function() { 
                return JSON.stringify({ 
                    innerWidth: window.innerWidth, 
                    dpr: window.devicePixelRatio
                }); 
            })()
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            val (innerWidthValue, dprValue) = try {
                val clean = if (result?.startsWith("\"") == true) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"")
                } else result ?: "{}"
                val json = org.json.JSONObject(clean)
                Pair(json.optString("innerWidth", "0"), json.optString("dpr", "0"))
            } catch (e: Exception) {
                Pair("error", "error")
            }

            val log = """
                [XR WebView Diagnostics]
                MODE=$tag
                webViewWidth=${webView.width}
                displayWidth=${display?.mode?.physicalWidth ?: 0}
                density=${metrics.density}
                densityDpi=${metrics.densityDpi}
                innerWidth=$innerWidthValue
                devicePixelRatio=$dprValue
                UA=${settings.userAgentString}
            """.trimIndent()
            
            android.util.Log.d("WebViewMode", log)
            DiagnosticsManager.info("Browser", "Diag: $tag w=${webView.width} inner=$innerWidthValue")
        }
    }

    fun applyUserAgentMode(webView: WebView, mode: Int, clearCache: Boolean, triggerReload: Boolean = true) {
        val settings = webView.settings
        
        settings.userAgentString = defaultUserAgent
        
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false

        if (clearCache) {
            webView.clearCache(true)
        }

        if (triggerReload) {
            webView.reload()
        }
    }
}
