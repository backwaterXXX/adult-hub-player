package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvPerson(
    val id: String,
    val name: String,
    val primaryImageTag: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val nationality: String? = null,
    val aliases: List<String> = emptyList(),
    val photoUrl: String? = null,
    val movieCount: Int = 0,
)
