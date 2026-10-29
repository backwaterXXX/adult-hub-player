package com.adulthub.tv.data.model

import androidx.annotation.StringRes
import com.adulthub.tv.R

enum class DurationFilter(@StringRes val labelRes: Int, val minMinutes: Int?, val maxMinutes: Int?) {
    ALL(R.string.filter_all, null, null),
    SHORT(R.string.duration_short, 0, 15),
    MEDIUM(R.string.duration_medium, 15, 40),
    LONG(R.string.duration_long, 40, null);

    fun matchesMinutes(minutes: Int?): Boolean {
        if (this == ALL) return true
        val m = minutes ?: return false
        val min = minMinutes ?: 0
        val max = maxMinutes ?: Int.MAX_VALUE
        return m in min..max
    }

    fun matchesSeconds(seconds: Double?): Boolean {
        if (this == ALL) return true
        val s = seconds ?: return false
        if (s <= 0.0) return false
        val minSec = (minMinutes ?: 0) * 60.0
        val maxSec = (maxMinutes?.toDouble() ?: Double.MAX_VALUE) * 60.0
        return s >= minSec && s <= maxSec
    }
}

enum class QualityFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    HD(R.string.quality_hd_only);

    fun matches(item: TvItem): Boolean {
        if (this == ALL) return true
        return item.isHd
    }
}

enum class ShuffleContextMode(@StringRes val labelRes: Int) {
    DEFAULT(R.string.shuffle_any_random),
    UNWATCHED(R.string.shuffle_unwatched),
    SAME_PERFORMER(R.string.shuffle_same_performer_mode),
    SAME_CATEGORY(R.string.shuffle_same_category_mode),
}
