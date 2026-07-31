package com.xrdesk

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@UnstableApi
object MediaSourceHelper {

    fun createMediaSource(source: PlayableSource): MediaSource {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.headers["User-Agent"])
            .setDefaultRequestProperties(source.headers.toMutableMap().apply {
                buildCookieHeader(source.cookies)?.let { put("Cookie", it) }
            })

        val mediaItem = MediaItem.fromUri(source.url)
        
        return if (source.type == "hls" || source.url.lowercase().contains(".m3u8")) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    /**
     * Formats cookies from name=value; name2=value2 into a proper header value.
     * Currently simply returns the string if not null, but can be expanded for complex validation.
     */
    fun buildCookieHeader(rawCookies: String?): String? {
        if (rawCookies.isNullOrBlank()) return null
        return rawCookies.trim()
    }
}
