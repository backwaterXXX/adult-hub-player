package com.adulthub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the focus behaviour that the TV screens rely on: the focus of a screen is restored
 * after the screen was removed from the composition and shown again.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusRestoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusIsRestoredAfterTheScreenWasShownAgain() {
        val firstFocusRequester = FocusRequester()
        val secondFocusRequester = FocusRequester()
        val showContent = androidx.compose.runtime.mutableStateOf(true)

        composeRule.setContent {
            if (showContent.value) {
                Column(
                    modifier = Modifier
                        .focusRestorer()
                        .width(200.dp)
                        .height(200.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .testTag("first")
                            .focusRequester(firstFocusRequester)
                            .focusable()
                            .background(Color.Black),
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .testTag("second")
                            .focusRequester(secondFocusRequester)
                            .focusable()
                            .background(Color.Black),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("second").performClick()
        composeRule.runOnIdle {
            secondFocusRequester.requestFocus()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("second").assertIsFocused()

        // The screen is removed and shown again, like a navigation to another screen and back.
        composeRule.runOnIdle { showContent.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showContent.value = true }
        composeRule.waitForIdle()

        val restored = runCatching {
            composeRule.onNodeWithTag("second").assertIsFocused()
        }.isSuccess
        assertTrue("The focus has to be restored after the screen was shown again", restored)
    }
}
