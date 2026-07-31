package com.xrdesk

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class ResourceInterceptor(context: Context) {

    init {
        AdBlockEngine.initialize(context)
    }

    private val client: OkHttpClient by lazy {
        val cacheDir = File(context.cacheDir, "browser_resource_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        OkHttpClient.Builder()
            .cache(Cache(cacheDir, 50L * 1024L * 1024L)) // 50 MB
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun shouldIntercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val referer = request.requestHeaders["Referer"] ?: ""
        val isTarget = url.contains("jut-su.net") || url.contains("jut.su") || 
                       referer.contains("jut-su.net") || referer.contains("jut.su")
        
        // 1. AdBlock check (First Priority)
        if (AdBlockEngine.isBlocked(url, isTarget)) {
            if (isTarget) DiagnosticsLog.add("Browser", "Resource blocked by AdBlock: $url")
            return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), null)
        }

        // 2. Legacy HTTP images check
        if (url.startsWith("http://www.world-art.ru/") && 
            (url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".gif") || url.endsWith(".webp") || url.contains("/img/"))) {
            
            DiagnosticsLog.add("Browser", "Intercepting resource: $url")
            return try {
                val okRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", WebViewSettings.getDefaultUserAgent())
                    .build()
                
                val response = client.newCall(okRequest).execute()
                val body = response.body
                
                if (response.isSuccessful && body != null) {
                    val contentType = response.header("Content-Type", "image/jpeg") ?: "image/jpeg"
                    val mimeType = contentType.split(";")[0].trim()
                    val encoding = if (contentType.contains("charset=")) {
                        contentType.split("charset=")[1].split(";")[0].trim()
                    } else {
                        "UTF-8"
                    }
                    
                    WebResourceResponse(
                        mimeType,
                        encoding,
                        response.code,
                        response.message.ifBlank { "OK" },
                        response.headers.toMap(),
                        body.byteStream()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                DiagnosticsLog.add("Browser", "Failed to intercept $url: ${e.message}")
                null
            }
        }
        
        return null
    }
}
