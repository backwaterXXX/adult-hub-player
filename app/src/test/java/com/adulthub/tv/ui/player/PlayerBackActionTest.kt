package com.adulthub.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackActionTest {

    @Test
    fun `back closes the resume dialog instead of leaving the player`() {
        val action = resolvePlayerBackAction(
            autoplayCountdownSec = null,
            showInfoDialog = false,
            hasPendingResumeDialog = true,
            isOsdVisible = true,
        )
        assertEquals(PlayerBackAction.DISMISS_RESUME_DIALOG, action)
    }

    @Test
    fun `back closes the info dialog before anything else`() {
        val action = resolvePlayerBackAction(
            autoplayCountdownSec = null,
            showInfoDialog = true,
            hasPendingResumeDialog = true,
            isOsdVisible = true,
        )
        assertEquals(PlayerBackAction.CLOSE_INFO_DIALOG, action)
    }

    @Test
    fun `back cancels the autoplay countdown`() {
        val action = resolvePlayerBackAction(
            autoplayCountdownSec = 5,
            showInfoDialog = false,
            hasPendingResumeDialog = false,
            isOsdVisible = true,
        )
        assertEquals(PlayerBackAction.CANCEL_AUTOPLAY, action)
    }

    @Test
    fun `back hides the on screen display`() {
        val action = resolvePlayerBackAction(
            autoplayCountdownSec = null,
            showInfoDialog = false,
            hasPendingResumeDialog = false,
            isOsdVisible = true,
        )
        assertEquals(PlayerBackAction.HIDE_OSD, action)
    }

    @Test
    fun `back leaves the player when nothing is open`() {
        val action = resolvePlayerBackAction(
            autoplayCountdownSec = null,
            showInfoDialog = false,
            hasPendingResumeDialog = false,
            isOsdVisible = false,
        )
        assertEquals(PlayerBackAction.EXIT_PLAYER, action)
    }
}
