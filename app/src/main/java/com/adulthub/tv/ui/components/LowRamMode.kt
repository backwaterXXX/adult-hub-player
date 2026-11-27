package com.adulthub.tv.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Low-RAM mode resolved once for the whole app.
 *
 * Grid cards read this instead of collecting the settings DataStore in every composed cell:
 * a paged TV grid used to open one collector per card and re-evaluate all of them on every
 * unrelated preference write.
 */
val LocalLowRamMode = compositionLocalOf { false }
