package com.adulthub.tv.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adulthub.tv.ui.theme.AdultHubTvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the resume dialog of the player screen, including the focus of the resume button
 * and the two possible decisions.
 */
@RunWith(AndroidJUnit4::class)
class ResumePlaybackDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDialogShowsTheResumePosition() {
        composeRule.setContent {
            AdultHubTvTheme {
                ResumePlaybackDialog(
                    resumePositionMs = 65_000L,
                    onResume = {},
                    onStartFromBeginning = {},
                )
            }
        }

        composeRule.onNodeWithText("Resume Playback?").assertIsDisplayed()
        composeRule.onNodeWithText("Resume (01:05)").assertIsDisplayed()
    }

    @Test
    fun theResumeButtonIsFocusedAndStartsThePlayback() {
        var resumed = false
        composeRule.setContent {
            AdultHubTvTheme {
                ResumePlaybackDialog(
                    resumePositionMs = 65_000L,
                    onResume = { resumed = true },
                    onStartFromBeginning = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resume (01:05)").performClick()
        composeRule.waitForIdle()

        assertTrue("The resume button has to start the playback", resumed)
    }

    @Test
    fun theStartFromBeginningButtonIsAvailable() {
        var startedFromBeginning = false
        composeRule.setContent {
            AdultHubTvTheme {
                ResumePlaybackDialog(
                    resumePositionMs = 65_000L,
                    onResume = {},
                    onStartFromBeginning = { startedFromBeginning = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start Over").performClick()
        composeRule.waitForIdle()

        assertTrue("The second button has to restart the playback", startedFromBeginning)
    }
}
