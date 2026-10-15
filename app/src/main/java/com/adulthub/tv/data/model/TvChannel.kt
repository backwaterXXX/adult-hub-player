package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvChannel(
    val id: String,
    val title: String,
    val slug: String,
    val url: String,
)
