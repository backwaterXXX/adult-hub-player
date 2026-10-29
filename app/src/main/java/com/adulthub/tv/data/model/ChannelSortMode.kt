package com.adulthub.tv.data.model

import androidx.annotation.StringRes
import com.adulthub.tv.R

enum class ChannelSortMode(val id: String, @StringRes val labelRes: Int) {
    LATEST("latest", R.string.sort_latest),
    LONGEST("longest", R.string.sort_longest),
    SHORTEST("shortest", R.string.sort_shortest);

    companion object {
        fun fromId(id: String?): ChannelSortMode = entries.firstOrNull { it.id == id } ?: LATEST
    }
}
