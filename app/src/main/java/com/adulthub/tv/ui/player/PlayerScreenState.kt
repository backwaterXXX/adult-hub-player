package com.adulthub.tv.ui.player

/**
 * State that is shared between the player screen and the activity.
 */
internal object PlayerScreenState {
    /**
     * True while a player screen is on top of the activity. The activity uses it to restore the
     * keep screen on flag after the app was paused in the background.
     */
    @Volatile
    var isPlayerScreenVisible: Boolean = false
}
