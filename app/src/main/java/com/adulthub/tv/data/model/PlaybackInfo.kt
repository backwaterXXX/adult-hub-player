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
)
