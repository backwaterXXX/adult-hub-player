package com.adulthub.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adulthub.tv.R
import com.adulthub.tv.data.model.TvItem

@Composable
fun formattedDurationLabel(item: TvItem): String {
    val minutes = item.runtimeMinutes ?: return ""
    val hours = minutes / 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes % 60)
    } else {
        stringResource(R.string.duration_minutes_only, minutes)
    }
}
