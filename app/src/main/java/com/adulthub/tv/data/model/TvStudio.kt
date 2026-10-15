package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvStudio(
    val id: String,
    val name: String,
    val primaryImageTag: String? = null,
    val movieCount: Int = 0,
)
