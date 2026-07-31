package com.xrdesk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayableSource(
    val url: String,
    val type: String, // "hls", "mp4", "webm", etc.
    val headers: Map<String, String> = emptyMap(),
    val cookies: String? = null,
    val title: String? = null,
    val quality: String? = null,
    val resolverName: String,
    val isMaster: Boolean = false
) : Parcelable
