package com.adulthub.tv.ui.player

/**
 * Actions the back gesture can trigger on the player screen.
 */
internal enum class PlayerBackAction {
    CANCEL_AUTOPLAY,
    CLOSE_INFO_DIALOG,
    DISMISS_RESUME_DIALOG,
    HIDE_OSD,
    EXIT_PLAYER,
}

/**
 * Resolves what the back gesture has to do.
 *
 * The order matters: an open dialog is always closed first, so the back gesture never leaves
 * the player while a dialog is waiting for a decision.
 */
internal fun resolvePlayerBackAction(
    autoplayCountdownSec: Int?,
    showInfoDialog: Boolean,
    hasPendingResumeDialog: Boolean,
    isOsdVisible: Boolean,
): PlayerBackAction = when {
    autoplayCountdownSec != null -> PlayerBackAction.CANCEL_AUTOPLAY
    showInfoDialog -> PlayerBackAction.CLOSE_INFO_DIALOG
    hasPendingResumeDialog -> PlayerBackAction.DISMISS_RESUME_DIALOG
    isOsdVisible -> PlayerBackAction.HIDE_OSD
    else -> PlayerBackAction.EXIT_PLAYER
}
