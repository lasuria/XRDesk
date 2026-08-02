package com.xrdesk

import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LegacyVideoDetector(private val onVideoDetected: (DetectedVideo) -> Unit) {

    enum class Priority { HIGH, MEDIUM, LOW }
    enum class Category { HTML_VIDEO, BLOB, IFRAME, NETWORK }
    enum class State { DETECTED, ACTIVE, PLAYING, PLAYING_CONFIRMED, PLAYING_PROXY, INACTIVE }

    data class HLSMetadata(
        var isMaster: Boolean = false,
        var isMedia: Boolean = false,
        var variantsCount: Int = 0,
        var maxBandwidth: Long = 0,
        var resolutions: List<String> = emptyList()
    )

    data class DetectedVideo(
        val url: String,
        val type: String,
        val source: String,
        val pageUrl: String,
        val dimensions: String,
        val duration: String,
        val priority: Priority,
        val category: Category,
        var score: Int,
        var originalScore: Int = 0,
        var finalScore: Int = 0,
        var rejectionReason: String? = null,
        var state: State = State.DETECTED,
        var hlsMetadata: HLSMetadata? = null,
        var analysisFailed: Boolean = false,
        var lastActivityTime: Long = 0,
        var segmentCount: Int = 0,
        var parentMasterUrl: String? = null,
        var activeChildUrl: String? = null,
        var confirmedBy: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        var sessionId: String = "",
        var isArchived: Boolean = false
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        
        val isBlob: Boolean = url.startsWith("blob:")

        fun getDisplayUrl(): String {
            return if (url.length > 80) "..." + url.takeLast(77) else url
        }
    }

    private val detectedVideos = mutableListOf<DetectedVideo>()
    private val processedUrls = mutableSetOf<String>()
    private val analyzedUrls = mutableSetOf<String>()
    private var bestCandidate: DetectedVideo? = null

    // Session Management
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentSessionReason: String = "Initial detection"
    private var lastPageUrl: String = ""
    private var lastContentPath: String? = null
    private var lastGlobalActivityTime: Long = System.currentTimeMillis()

    // Pending Session
    private var pendingSessionUrl: String? = null
    private var pendingSessionPageUrl: String? = null
    private var pendingSessionTimestamp: Long = 0
    private var pendingSessionReason: String? = null

    private val httpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val blacklistPatterns = listOf(
        "_blank.mp4", "blank.mp4", "poster", "preview", "thumbnail", "placeholder"
    )

    fun getDetectedVideos(): List<DetectedVideo> = synchronized(detectedVideos) {
        detectedVideos.toList()
    }

    fun getBestCandidate(): DetectedVideo? = synchronized(detectedVideos) {
        bestCandidate
    }

    fun getSessionInfo(): Triple<String, String, PendingInfo?> = synchronized(detectedVideos) {
        checkPendingTimeout()
        val pending = pendingSessionUrl?.let { 
            PendingInfo(it, pendingSessionTimestamp, "Waiting for confirmation")
        }
        Triple(currentSessionId, currentSessionReason, pending)
    }

    data class PendingInfo(val url: String, val startTime: Long, val status: String)

    fun clear() = synchronized(detectedVideos) {
        detectedVideos.clear()
        processedUrls.clear()
        analyzedUrls.clear()
        bestCandidate = null
        pendingSessionUrl = null
        startNewSession("Browser navigation", "")
    }

    @JavascriptInterface
    fun onSourceChanged(newUrl: String, pageUrl: String) {
        android.util.Log.i("VideoDetector", "onSourceChanged: $newUrl (Page: $pageUrl)")
        synchronized(detectedVideos) {
            val (shouldChange, reason) = checkSessionThreshold(newUrl, pageUrl)
            if (shouldChange) {
                setPendingSession(newUrl, pageUrl, reason ?: "Source changed")
            }
        }
    }

    fun forceNewSession() {
        synchronized(detectedVideos) {
            startNewSession("Force New Session", lastPageUrl)
            updateBestCandidateRank()
        }
    }

    private fun setPendingSession(url: String, pageUrl: String, reason: String) {
        if (url == pendingSessionUrl) return
        pendingSessionUrl = url
        pendingSessionPageUrl = pageUrl
        pendingSessionTimestamp = System.currentTimeMillis()
        pendingSessionReason = reason
        android.util.Log.i("VideoDetector", "Pending session set: $reason for $url")
    }

    private fun checkPendingTimeout() {
        if (pendingSessionUrl != null && System.currentTimeMillis() - pendingSessionTimestamp > 30000) {
            android.util.Log.i("VideoDetector", "Pending session timed out: $pendingSessionUrl")
            pendingSessionUrl = null
            pendingSessionReason = null
        }
    }

    private fun maybeConfirmPending(activityUrl: String, confirmationReason: String) {
        val pendingUrl = pendingSessionUrl ?: return
        if (isRelatedToPending(activityUrl, pendingUrl)) {
            val hierarchy = mutableListOf<String>()
            hierarchy.add(activityUrl)
            
            val parentMedia = if (isSegmentUrl(activityUrl)) {
                findParentPlaylist(activityUrl)?.url
            } else null
            parentMedia?.let { hierarchy.add(it) }
            
            val masterUrl = parentMedia?.let { findMasterPlaylistForChild(it)?.url } 
                ?: if (activityUrl.contains(".m3u8")) findMasterPlaylistForChild(activityUrl)?.url else null
            masterUrl?.let { hierarchy.add(it) }

            val richReason = if (parentMedia != null) "segment activity via child playlist" else confirmationReason
            
            // Promote existing objects to new session
            startNewSession("Confirmed by $richReason", pendingSessionPageUrl ?: "", hierarchy)
            
            // Explicitly mark promotion method for best candidate
            hierarchy.forEach { url ->
                detectedVideos.find { it.url == url }?.let { it.confirmedBy = richReason }
            }
            
            pendingSessionUrl = null
        }
    }

    private fun findMasterPlaylistForChild(childUrl: String): DetectedVideo? {
        val candidate = detectedVideos.find { it.url == childUrl }
        candidate?.parentMasterUrl?.let { parentUrl ->
            val master = detectedVideos.find { it.url == parentUrl }
            if (master != null) return master
        }
        
        // Search by identity matching
        val childId = getContentIdentity(childUrl) ?: return null
        return detectedVideos.find { it.hlsMetadata?.isMaster == true && getContentIdentity(it.url) == childId && it.url != childUrl }
    }

    private fun isRelatedToPending(url: String, pendingUrl: String): Boolean {
        if (url == pendingUrl) return true
        val pendingId = getContentIdentity(pendingUrl)
        val activityId = getContentIdentity(url)
        return pendingId != null && pendingId == activityId
    }

    private fun getContentIdentity(url: String): String? {
        // Extract episode ID or common path segment (e.g. /416711/ from /824824/416711/index.m3u8)
        // Usually it's a numeric or long stable string in the path
        val pathSegments = url.substringAfter("://").substringAfter("/").split("/")
        return pathSegments.firstOrNull { it.length >= 4 && it.all { c -> c.isDigit() } } 
            ?: pathSegments.getOrNull(0) // Fallback to first directory
    }

    private fun checkSessionThreshold(newUrl: String, pageUrl: String): Pair<Boolean, String?> {
        if (newUrl.isEmpty() || newUrl.startsWith("blob:")) return false to null

        // 1. Page URL change
        if (lastPageUrl.isNotEmpty() && lastPageUrl != pageUrl && !isSamePage(lastPageUrl, pageUrl)) {
            return true to "Episode/Page changed"
        }

        // 2. Content path change
        val newPathId = getContentIdentity(newUrl)
        val lastPathId = lastContentPath?.let { getContentIdentity(it) }
        if (newPathId != null && lastPathId != null && newPathId != lastPathId) {
            return true to "Content path changed"
        }

        // 3. Inactivity timeout
        val idleTime = System.currentTimeMillis() - lastGlobalActivityTime
        if (idleTime > 20000 && isMediaUrl(newUrl)) {
            return true to "Inactivity timeout"
        }

        return false to null
    }

    private fun startNewSession(reason: String, pageUrl: String, excludeUrls: List<String> = emptyList()) {
        currentSessionId = UUID.randomUUID().toString()
        currentSessionReason = reason
        lastPageUrl = pageUrl
        
        detectedVideos.forEach { 
            if (!it.isArchived && !excludeUrls.contains(it.url)) {
                it.isArchived = true
                it.state = State.INACTIVE
                it.score -= 200
            } else if (excludeUrls.contains(it.url)) {
                it.sessionId = currentSessionId
                it.isArchived = false
            }
        }
        bestCandidate = null
    }

    private fun isSamePage(url1: String, url2: String): Boolean {
        val base1 = url1.substringBefore("?").substringBefore("#")
        val base2 = url2.substringBefore("?").substringBefore("#")
        return base1 == base2
    }

    private fun getContentPath(url: String): String {
        return url.substringBeforeLast("/")
    }

    @JavascriptInterface
    fun onVideoFound(json: String) {
        try {
            val obj = JSONObject(json)
            val url = obj.optString("url")
            val pageUrl = obj.optString("pageUrl", "")
            if (url.isEmpty()) return

            synchronized(detectedVideos) {
                maybeConfirmPending(url, "video play event")

                if (processedUrls.contains(url)) {
                    detectedVideos.find { it.url == url }?.let { updateActivity(it) }
                    return
                }
                processedUrls.add(url)

                val type = obj.optString("type", "unknown")
                val source = obj.optString("source", "unknown")
                val dimensions = obj.optString("dimensions", "N/A")
                val duration = obj.optString("duration", "N/A")
                val categoryStr = obj.optString("category", "HTML_VIDEO")
                
                val category = try { Category.valueOf(categoryStr) } catch (_: Exception) { Category.HTML_VIDEO }
                
                val priority = when {
                    isHighPriorityUrl(url) -> Priority.HIGH
                    url.startsWith("blob:") -> Priority.LOW
                    else -> Priority.MEDIUM
                }

                val score = calculateScore(url, type, State.DETECTED)
                val video = DetectedVideo(
                    url = url, type = type, source = source, pageUrl = pageUrl,
                    dimensions = dimensions, duration = duration, priority = priority,
                    category = category, score = score, sessionId = currentSessionId,
                    originalScore = score, finalScore = score
                )
                detectedVideos.add(video)
                
                if (url.contains(".m3u8")) {
                    if (isHighPriorityUrl(url)) lastContentPath = getContentPath(url)
                    analyzeHLSAsync(video)
                }

                updateBestCandidateRank()
                onVideoDetected(video)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoDetector", "Error parsing video data: ${e.message}")
        }
    }

    fun onNetworkResourceDetected(url: String, pageUrl: String) {
        if (!isMediaUrl(url)) return
        
        synchronized(detectedVideos) {
            lastGlobalActivityTime = System.currentTimeMillis()
            maybeConfirmPending(url, "segment activity")

            if (processedUrls.contains(url)) {
                detectedVideos.find { it.url == url }?.let { updateActivity(it) }
                return
            }

            if (isSegmentUrl(url)) {
                val parent = findParentPlaylist(url)
                if (parent != null) {
                    android.util.Log.v("VideoDetector", "[HLS] Segment matched: $url to child ${parent.url}")
                    updateActivity(parent)
                    return
                }
            }

            if (processedUrls.contains(url)) return
            processedUrls.add(url)

            val type = when {
                url.contains(".m3u8") -> "application/x-mpegURL"
                url.contains(".mpd") -> "application/dash+xml"
                url.contains(".mp4") -> "video/mp4"
                url.contains(".ts") -> "video/mp2t"
                else -> "video/unknown"
            }

            val score = calculateScore(url, type, State.DETECTED)
            if (score <= 0) {
                processedUrls.remove(url) 
                return
            }

            val video = DetectedVideo(
                url = url, type = type, source = "network_observation", pageUrl = pageUrl,
                dimensions = "N/A", duration = "N/A", priority = Priority.HIGH,
                category = Category.NETWORK, score = score, sessionId = currentSessionId,
                originalScore = score, finalScore = score
            )
            detectedVideos.add(video)

            if (url.contains(".m3u8")) {
                if (isHighPriorityUrl(url)) lastContentPath = getContentPath(url)
                if (url.lowercase().contains("index")) {
                    findMasterPlaylistForChild(url)?.let { master ->
                        video.parentMasterUrl = master.url
                    }
                }
                analyzeHLSAsync(video)
            }

            updateBestCandidateRank()
            onVideoDetected(video)
        }
    }

    private fun updateActivity(video: DetectedVideo) {
        if (video.isArchived) return
        
        val now = System.currentTimeMillis()
        val prevActivity = video.lastActivityTime
        video.lastActivityTime = now
        video.segmentCount++
        
        // State Machine Refinement
        if (video.state == State.DETECTED || video.state == State.INACTIVE) {
            video.state = State.ACTIVE
            video.score = calculateScore(video.url, video.type, video.state)
        } else if (video.state == State.ACTIVE && prevActivity > 0 && now - prevActivity < 10000) {
            video.state = State.PLAYING
            video.score = calculateScore(video.url, video.type, video.state)
        }

        if (video.segmentCount >= 3 && video.state != State.PLAYING_CONFIRMED) {
            video.state = State.PLAYING_CONFIRMED
            video.score = calculateScore(video.url, video.type, video.state)
            
            video.parentMasterUrl?.let { parentUrl ->
                detectedVideos.find { it.url == parentUrl }?.let { parent ->
                    android.util.Log.v("VideoDetector", "[HLS] Promotion: MASTER ${parent.url} -> PLAYING_PROXY via ${video.url}")
                    parent.state = State.PLAYING_PROXY
                    parent.activeChildUrl = video.url
                    parent.score = calculateScore(parent.url, parent.type, parent.state)
                }
            }
        }
        
        updateBestCandidateRank()
    }

    private fun findParentPlaylist(segmentUrl: String): DetectedVideo? {
        val segmentIdentity = getContentIdentity(segmentUrl) ?: return null
        val segmentBase = segmentUrl.substringBeforeLast("/")
        
        // Prefer exact path match
        detectedVideos.find { !it.isArchived && it.url.contains(".m3u8") && it.url.startsWith(segmentBase) }?.let { return it }
        
        // Fallback to identity match
        return detectedVideos.find { !it.isArchived && it.url.contains(".m3u8") && getContentIdentity(it.url) == segmentIdentity }
    }

    private fun calculateScore(url: String, type: String, state: State): Int {
        val lowerUrl = url.lowercase()
        
        if (lowerUrl.contains(".ts") || lowerUrl.contains(".m4s") || 
            lowerUrl.contains("thumbnail") || lowerUrl.contains("poster") || 
            lowerUrl.contains("/img/") || type.startsWith("image/")) {
            return 0
        }

        var score = when {
            lowerUrl.contains(".m3u8") -> 100
            lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") -> 80
            url.startsWith("blob:") -> 40
            else -> 60
        }

        score += when (state) {
            State.PLAYING_PROXY -> 500
            State.PLAYING_CONFIRMED -> 400
            State.PLAYING -> 300
            State.ACTIVE -> 200
            State.DETECTED -> 0
            State.INACTIVE -> -200
        }

        if (lowerUrl.contains(".m3u8") && (lowerUrl.contains("master") || lowerUrl.contains("playlist") || lowerUrl.contains("manifest"))) {
            score += 50
        }

        // Apply blacklist penalty
        if (blacklistPatterns.any { lowerUrl.contains(it) }) {
            score -= 500
        }

        return score.coerceAtLeast(-1000)
    }

    private fun analyzeHLSAsync(video: DetectedVideo) {
        synchronized(analyzedUrls) {
            if (analyzedUrls.contains(video.url)) return
            analyzedUrls.add(video.url)
        }

        val request = okhttp3.Request.Builder()
            .url(video.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .apply { if (video.pageUrl.isNotEmpty()) header("Referer", video.pageUrl) }
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                synchronized(detectedVideos) { video.analysisFailed = true }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        synchronized(detectedVideos) { video.analysisFailed = true }
                        return
                    }
                    val content = response.body?.source()?.let { 
                        val buffer = okio.Buffer()
                        it.read(buffer, 8192)
                        buffer.readUtf8()
                    } ?: ""
                    
                    val isMaster = content.contains("#EXT-X-STREAM-INF")
                    val isMedia = content.contains("#EXTINF")
                    
                    synchronized(detectedVideos) {
                        val meta = HLSMetadata(
                            isMaster = isMaster,
                            isMedia = isMedia,
                            variantsCount = "#EXT-X-STREAM-INF".toRegex().findAll(content).count(),
                            maxBandwidth = "BANDWIDTH=(\\d+)".toRegex().findAll(content).map { it.groupValues[1].toLong() }.maxOrNull() ?: 0L,
                            resolutions = "RESOLUTION=(\\d+x\\d+)".toRegex().findAll(content).map { it.groupValues[1] }.distinct().toList()
                        )
                        video.hlsMetadata = meta
                        
                        if (isMedia && !isMaster) {
                            findMasterPlaylistForChild(video.url)?.let { master ->
                                video.parentMasterUrl = master.url
                                android.util.Log.v("VideoDetector", "[HLS] Linked child ${video.url} to master ${master.url}")
                            }
                        }

                        video.score = calculateScore(video.url, video.type, video.state)
                        if (isMaster) video.score += 50
                        
                        updateBestCandidateRank()
                    }
                }
            }
        })
    }

    private fun updateBestCandidateRank() {
        val currentSessionVideos = detectedVideos.filter { !it.isArchived && it.sessionId == currentSessionId }
        
        // 1. Identify existing HLS Master with playback activity
        val activeHlsMasterExists = currentSessionVideos.any { 
            it.hlsMetadata?.isMaster == true && (it.state == State.PLAYING_PROXY || it.state == State.PLAYING_CONFIRMED)
        }

        // 2. Evaluate all candidates
        currentSessionVideos.forEach { v ->
            val lowerUrl = v.url.lowercase()
            v.rejectionReason = null // Reset before evaluation
            v.originalScore = calculateScore(v.url, v.type, v.state) // Score based on state/type
            
            // Apply hard rules
            if (blacklistPatterns.any { lowerUrl.contains(it) }) {
                v.rejectionReason = "Placeholder content"
            } else if (activeHlsMasterExists && (lowerUrl.contains(".mp4") || lowerUrl.contains(".webm")) && !lowerUrl.contains(".m3u8")) {
                v.rejectionReason = "Lower priority than active HLS"
            }

            v.finalScore = if (v.rejectionReason != null) v.originalScore - 500 else v.originalScore
            v.score = v.finalScore 
        }

        // 3. Selection by Tiered Priority
        val newBest = currentSessionVideos
            .filter { it.rejectionReason == null }
            .sortedWith(compareByDescending<DetectedVideo> {
                // Priority Tiers
                when {
                    it.hlsMetadata?.isMaster == true && it.state == State.PLAYING_CONFIRMED -> 5
                    it.hlsMetadata?.isMaster == true && it.state == State.PLAYING_PROXY -> 4
                    it.hlsMetadata?.isMedia == true && it.state == State.PLAYING_CONFIRMED -> 3
                    it.state == State.ACTIVE || it.state == State.PLAYING -> 2
                    else -> 1
                }
            }.thenByDescending { it.score })
            .firstOrNull()

        if (newBest != null && (bestCandidate == null || newBest.url != bestCandidate?.url || newBest.score != bestCandidate?.score)) {
            bestCandidate = newBest
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4") || 
               lower.contains(".webm") || lower.contains(".m4v") || isSegmentUrl(url)
    }

    private fun isSegmentUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".ts") || lower.contains(".m4s") || lower.contains(".aac") || 
               (lower.contains(".mp4") && (lower.contains("chunk") || lower.contains("seg-") || lower.contains("init")))
    }

    private fun isHighPriorityUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || (lower.contains(".mp4") && !isSegmentUrl(url)) || lower.contains(".webm")
    }

    fun getInjectionScript(): String {
        return """
            (function() {
                if (window.VideoDetectorInjected) return;
                window.VideoDetectorInjected = true;

                var sourceChangeTimer = null;
                function notifySourceChanged(el) {
                    clearTimeout(sourceChangeTimer);
                    sourceChangeTimer = setTimeout(() => {
                        if (window.VideoDetector) {
                            window.VideoDetector.onSourceChanged(el.src || el.currentSrc, window.location.href);
                        }
                    }, 1500);
                }

                function notifyAndroid(el, source, category) {
                    var url = el.currentSrc || el.src;
                    if (!url || url.length < 5) return;
                    var data = {
                        url: url,
                        type: el.type || 'video/unknown',
                        source: source,
                        category: category || 'HTML_VIDEO',
                        pageUrl: window.location.href,
                        dimensions: el.videoWidth + 'x' + el.videoHeight,
                        duration: el.duration ? el.duration.toFixed(1) + 's' : 'unknown'
                    };
                    if (window.VideoDetector) window.VideoDetector.onVideoFound(JSON.stringify(data));
                }

                function checkElement(el) {
                    if (el.tagName === 'VIDEO') {
                        notifyAndroid(el, 'video_element', 'HTML_VIDEO');
                        el.addEventListener('loadedmetadata', () => notifyAndroid(el, 'event_loadedmetadata', 'HTML_VIDEO'), { once: true });
                        el.addEventListener('play', () => notifyAndroid(el, 'event_play', 'HTML_VIDEO'));
                        
                        var observer = new MutationObserver((mutations) => {
                            mutations.forEach((m) => {
                                if (m.attributeName === 'src') notifySourceChanged(el);
                            });
                        });
                        observer.observe(el, { attributes: true, attributeFilter: ['src'] });
                    }
                }

                document.querySelectorAll('video').forEach(checkElement);
                
                new MutationObserver((mutations) => {
                    mutations.forEach((m) => {
                        m.addedNodes.forEach((node) => {
                            if (node.nodeType === 1) {
                                if (node.tagName === 'VIDEO') checkElement(node);
                                node.querySelectorAll('video').forEach(checkElement);
                            }
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });

                var originalCreateObjectURL = URL.createObjectURL;
                URL.createObjectURL = function(obj) {
                    var url = originalCreateObjectURL.apply(this, arguments);
                    if (obj instanceof MediaSource || obj instanceof Blob) {
                         if (window.VideoDetector) window.VideoDetector.onVideoFound(JSON.stringify({
                             url: url, type: obj.type || 'MediaSource', source: 'blob_hook', category: 'BLOB', pageUrl: window.location.href, dimensions: 'N/A', duration: 'N/A'
                         }));
                    }
                    return url;
                };

                console.log('VideoDetector: Session confirmed active');
            })();
        """.trimIndent()
    }
}
