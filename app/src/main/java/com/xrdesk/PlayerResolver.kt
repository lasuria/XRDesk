package com.xrdesk

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource

@UnstableApi
interface PlayerResolver {
    fun prepareMediaSource(source: PlayableSource): MediaSource
}

@UnstableApi
class DefaultPlayerResolver : PlayerResolver {
    override fun prepareMediaSource(source: PlayableSource): MediaSource {
        return MediaSourceHelper.createMediaSource(source)
    }
}
