package com.adulthub.tv.data.model

import org.jellyfin.sdk.model.api.PlayMethod

data class PlaybackInfo(
    val itemId: String,
    val streamUrl: String,
    val mediaSourceId: String? = null,
    val playSessionId: String? = null,
    val startPositionTicks: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val playMethod: PlayMethod,
    val title: String = "",
    val fileSizeBytes: Long? = null,
    val container: String? = null,
    val durationMs: Long? = null,
    /** Identifier accepted by the resolver variants endpoint, when the stream comes from it. */
    val resolverVideoId: String? = null,
    /** The quality variant the stream URL was built for. Null means the original. */
    val variantId: String? = null,
)
