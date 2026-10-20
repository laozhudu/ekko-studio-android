package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The chat composer must treat Enter as "new line" and leave submitting to the
 * round button. A soft keyboard that relabels the return key "send", or an IME
 * that reports Send/Done, must not be able to post a message.
 */
class ComposerKeyboardTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

    private val composer: String
        get() = activity.substringAfter("private fun Composer(")
            .substringBefore("private fun ComposerActionButton(")

    @Test
    fun returnKeyIsANewlineKey() {
        assertTrue(
            "Composer must pin imeAction to None so the return key stays a newline key",
            composer.contains("imeAction = ImeAction.None"),
        )
        assertFalse(
            "Composer must not request a send/done action from the keyboard",
            composer.contains("imeAction = ImeAction.Send") || composer.contains("ImeAction.Done"),
        )
    }

    @Test
    fun imeActionsAreSwallowedInsteadOfSubmitting() {
        val actions = composer.substringAfter("keyboardActions = KeyboardActions(")
            .substringBefore("),\n                    modifier")
        listOf("onSend", "onDone", "onGo", "onSearch").forEach { action ->
            assertTrue("$action must be bound to a no-op", actions.contains("$action = {}"))
        }
    }

    @Test
    fun multilineEditingIsEnabled() {
        assertTrue("Composer keeps growing up to five lines", composer.contains("maxLines = 5"))
    }

    @Test
    fun theButtonIsTheOnlySubmitPath() {
        val button = activity.substringAfter("private fun ComposerActionButton(")
            .substringBefore("private fun ToolbarChip(")
        assertTrue("Send button calls onSend", button.contains("hasPayload -> IconButton(onClick = onSend)"))
    }
}
