package com.xrdesk

import android.webkit.JavascriptInterface

class BrowserInputBridge(private val onInputFocused: (Boolean) -> Unit) {
    @JavascriptInterface
    fun onInputFocused(focused: Boolean) {
        onInputFocused.invoke(focused)
    }
}
