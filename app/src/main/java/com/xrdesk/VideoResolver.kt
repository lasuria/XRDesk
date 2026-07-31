package com.xrdesk

import android.webkit.JavascriptInterface
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Interface for specific video resolution techniques (e.g. yt-dlp, HLS analysis).
 */
interface VideoResolver {
    val name: String
    fun resolve(url: String, context: ResolveContext, callback: (PlayableSource?) -> Unit)
}

data class ResolveContext(
    val pageUrl: String,
    val userAgent: String,
    val cookies: String? = null
)

/**
 * Manager that coordinates multiple detection methods and maintains the best playable source.
 */
class VideoResolverManager(private val onSourceResolved: (PlayableSource) -> Unit) {

    private var currentBestSource: PlayableSource? = null
    private var currentPriority: Priority = Priority.NONE
    var candidatesCount: Int = 0
        private set
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    enum class Priority(val value: Int) {
        NONE(0),
        MP4(1),
        HLS_MEDIA(2),
        HLS_MASTER(3),
        YTDLP(4)
    }

    fun getCurrentSource(): PlayableSource? = synchronized(this) { currentBestSource }

    fun clear() = synchronized(this) {
        currentBestSource = null
        currentPriority = Priority.NONE
        candidatesCount = 0
    }

    @JavascriptInterface
    fun onVideoFound(json: String) {
        try {
            val obj = JSONObject(json)
            val url = obj.optString("url")
            val pageUrl = obj.optString("pageUrl", "")
            // HTML5 detector context
            onHtml5Video(url, ResolveContext(pageUrl, "", null))
        } catch (e: Exception) {}
    }

    fun onNetworkResource(url: String, context: ResolveContext) {
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains(".m3u8")) {
            analyzeHLS(url, context)
        } else if (isDirectVideo(lowerUrl)) {
            val source = PlayableSource(
                url = url,
                type = "mp4",
                headers = mapOf("Referer" to context.pageUrl, "User-Agent" to context.userAgent),
                cookies = context.cookies,
                resolverName = "Network"
            )
            updateIfBetter(source, Priority.MP4)
        }
    }

    private fun onHtml5Video(url: String, context: ResolveContext) {
        if (url.isEmpty() || url.startsWith("blob:")) return
        
        val priority = if (url.contains(".m3u8")) Priority.HLS_MEDIA else Priority.MP4
        val source = PlayableSource(
            url = url,
            type = if (url.contains(".m3u8")) "hls" else "mp4",
            headers = mapOf("Referer" to context.pageUrl, "User-Agent" to context.userAgent),
            cookies = context.cookies,
            resolverName = "HTML5"
        )
        updateIfBetter(source, priority)
    }

    private fun isDirectVideo(url: String): Boolean {
        return (url.contains(".mp4") || url.contains(".webm") || url.contains(".m4v")) && 
               !url.contains("chunk") && !url.contains("seg-") && !url.contains("init")
    }

    private fun analyzeHLS(url: String, context: ResolveContext) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WebViewSettings.getDefaultUserAgent())
            .header("Referer", context.pageUrl)
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) return
                    val content = response.body?.source()?.let {
                        val buffer = Buffer()
                        it.read(buffer, 8192)
                        buffer.readUtf8()
                    } ?: ""

                    val isMaster = content.contains("#EXT-X-STREAM-INF")
                    val source = PlayableSource(
                        url = url,
                        type = "hls",
                        headers = mapOf("Referer" to context.pageUrl, "User-Agent" to context.userAgent),
                        cookies = context.cookies,
                        resolverName = "HLS Analyzer",
                        isMaster = isMaster
                    )
                    updateIfBetter(source, if (isMaster) Priority.HLS_MASTER else Priority.HLS_MEDIA)
                }
            }
        })
    }

    private fun updateIfBetter(newSource: PlayableSource, priority: Priority) = synchronized(this) {
        candidatesCount++
        if (priority.value >= currentPriority.value) {
            currentBestSource = newSource
            currentPriority = priority
            onSourceResolved(newSource)
            
            // Whitelist the source domain for the current session to prevent segments being blocked
            AdBlockEngine.addToSessionWhitelist(newSource.url)
            
            android.util.Log.i("VideoResolver", "Resolved better source: ${newSource.url} (Priority: ${priority.name})")
        }
    }
}
