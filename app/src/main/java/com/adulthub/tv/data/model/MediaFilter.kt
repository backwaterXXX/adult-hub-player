package com.adulthub.tv.data.model

enum class DurationFilter(val label: String, val minMinutes: Int?, val maxMinutes: Int?) {
    ALL("All", null, null),
    SHORT("<15m", 0, 15),
    MEDIUM("15–40m", 15, 40),
    LONG("40m+", 40, null);

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

enum class QualityFilter(val label: String) {
    ALL("All"),
    HD("HD only");

    fun matches(item: TvItem): Boolean {
        if (this == ALL) return true
        return item.isHd
    }
}

enum class ShuffleContextMode(val label: String) {
    DEFAULT("Any Random"),
    UNWATCHED("Unwatched Only"),
    SAME_PERFORMER("Same Performer"),
    SAME_CATEGORY("Same Category"),
}
