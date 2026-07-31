package com.xrdesk

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Global manager for media playback sessions.
 * Allows sharing a single ExoPlayer instance between phone Activity and XR Presentation.
 */
@OptIn(UnstableApi::class)
object MediaSessionManager {
    private var player: ExoPlayer? = null
    var currentSource: PlayableSource? = null
        private set

    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            player = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .build()
            applySavedPreferences(context)
        }
        return player!!
    }

    /**
     * Prepares the shared player for a new source or a transition.
     */
    fun prepare(context: Context, source: PlayableSource) {
        val p = getPlayer(context)
        
        if (currentSource?.url != source.url) {
            currentSource = source
            val resolver = DefaultPlayerResolver()
            val mediaSource = resolver.prepareMediaSource(source)
            
            p.stop()
            p.clearMediaItems()
            p.setMediaSource(mediaSource)
            p.prepare()
            
            // Check for resume
            val history = PlaybackHistoryStore.getSavedPosition(context, source.url)
            if (history != null) {
                p.seekTo(history.positionMs)
            }
            
            applySavedPreferences(context)
        }
    }

    fun applySavedPreferences(context: Context) {
        val p = player ?: return
        val paramsBuilder = p.trackSelectionParameters.buildUpon()
        MediaPrefsStore.applyToParameters(context, paramsBuilder)
        p.trackSelectionParameters = paramsBuilder.build()
    }

    /**
     * Safely attaches the shared player to a new view.
     */
    fun attachToView(view: PlayerView) {
        view.player = player
    }

    /**
     * Detaches player from any view before a transition or stop.
     */
    fun detachFromView(view: PlayerView?) {
        view?.player = null
    }

    fun saveProgress(context: Context) {
        val p = player ?: return
        val s = currentSource ?: return
        
        if (p.duration > 0) {
            PlaybackHistoryStore.savePosition(
                context, 
                s.url, 
                s.title, 
                p.currentPosition, 
                p.duration
            )
        }
    }

    /**
     * Completely stops playback and releases resources.
     */
    fun closePlayer(context: Context) {
        player?.let { p ->
            // 1. Save state
            saveProgress(context)
            
            // 2. Clear callbacks/listeners
            // (Listeners added by Activities should be managed by them, but we clear media items)

            // 3. Pause & Stop
            p.pause()
            p.stop()
            p.clearMediaItems()
            
            // 4. Release (handles abandoning audio focus)
            p.release()
        }
        player = null
        currentSource = null
    }

    fun release() {
        player?.release()
        player = null
        currentSource = null
    }
}
