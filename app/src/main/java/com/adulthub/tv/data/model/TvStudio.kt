package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvStudio(
    val id: String,
    val name: String,
    val primaryImageTag: String? = null,
    /**
     * Number of movies when it is known.
     *
     * `null` means "not counted": the count needs one extra request per studio,
     * so it is only read for the studios the user actually sees.
     */
    val movieCount: Int? = null,
)
