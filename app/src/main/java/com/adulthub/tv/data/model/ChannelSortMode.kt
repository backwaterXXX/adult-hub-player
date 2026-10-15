package com.adulthub.tv.data.model

enum class ChannelSortMode(val id: String, val label: String) {
    LATEST("latest", "Latest"),
    LONGEST("longest", "Longest"),
    SHORTEST("shortest", "Shortest");

    companion object {
        fun fromId(id: String?): ChannelSortMode = entries.firstOrNull { it.id == id } ?: LATEST
    }
}
