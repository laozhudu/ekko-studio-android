package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The context readout must be derived from the live counters, never a literal —
 * a hardcoded "50.3k / 1.0M" would silently lie the moment the session changes.
 */
class ContextReadoutTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    private val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
    private val socket = File("src/main/java/com/laozhudu/ekkostudio/ChatSocket.kt").readText()

    private val readout: String
        get() = activity.substringAfter("private fun ContextUsage(")
            .substringBefore("/** `.input-context-status--warning")

    @Test
    fun readoutIsComputedFromStateAndShowsWhatIsLeft() {
        assertTrue("remaining tokens must be computed", readout.contains("state.contextWindow - state.contextTokens"))
        assertTrue("the three numbers come from context_usage_full", readout.contains("R.string.context_usage_full"))
        assertTrue("remaining is passed to the format", readout.contains("compactNumber(remaining)"))
    }

    @Test
    fun readoutCarriesNoHardcodedFigures() {
        Regex("\\d+\\.\\d+k").findAll(readout).forEach {
            assertFalse("hardcoded figure in readout: ${it.value}", true)
        }
        assertFalse(readout.contains("1.0M"))
    }

    @Test
    fun unknownContextIsDistinctFromZero() {
        assertTrue("state starts unknown, not zero", viewModel.contains("val contextTokens: Long = -1"))
        assertTrue("readout checks for the unknown sentinel", readout.contains(">= 0"))
    }

    @Test
    fun liveUsageEventsAreSubscribed() {
        // The server publishes context.estimated and run.usage.updated; neither is
        // called "run_usage", which is why the meter used to freeze.
        assertTrue(socket.contains("live.on(\"context.estimated\")"))
        assertTrue(socket.contains("live.on(\"run.usage.updated\")"))
        assertTrue(KotlinSource.hasFun(socket, "usageFromEstimate"))
    }
}
