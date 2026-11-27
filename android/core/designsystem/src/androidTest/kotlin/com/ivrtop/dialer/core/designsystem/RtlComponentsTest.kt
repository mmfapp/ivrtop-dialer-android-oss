package com.ivrtop.dialer.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.ivrtop.dialer.core.designsystem.component.DialPad
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.theme.IvrtopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Compose UI tests: RTL layout must not mirror the keypad nor phone numbers. */
class RtlComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dialPadKeysStayLtrInsideRtl() {
        val pressed = mutableListOf<Char>()
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                IvrtopTheme { DialPad(onKey = { pressed += it }, onLongPress = { false }) }
            }
        }
        rule.onNodeWithContentDescription("1").performClick()
        rule.onNodeWithContentDescription("#").performClick()
        assertEquals(listOf('1', '#'), pressed)
        val one = rule.onNodeWithContentDescription("1").fetchSemanticsNode().boundsInRoot
        val three = rule.onNodeWithContentDescription("3").fetchSemanticsNode().boundsInRoot
        assertTrue("1 must be left of 3 even in RTL", one.left < three.left)
    }

    @Test fun phoneNumberRendersUnmirrored() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                IvrtopTheme { LtrText("054-123-4567") }
            }
        }
        rule.onNodeWithText("054-123-4567").assertIsDisplayed()
    }
}
