package com.laozhudu.ekkostudio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the socket event names to what the server actually emits.
 *
 * These were measured on the live server by running one turn and logging every
 * frame:
 *
 *   usage.updated {inputTokens, outputTokens, contextTokens}
 *   session.activity {status: running|completed|failed}
 *   agent.event {kind, text}
 *   plan.updated
 *   message.delta {delta, output}      <- streaming text, verified逐字
 *   thinking.delta {text}              <- often empty for non-reasoning models
 *   tool.started / tool.completed / tool.failed
 *
 * Names the server does NOT emit on `/chat-run` (subscribing to them was the bug
 * behind the frozen context meter): `context.estimated`, `run.usage.updated`
 * as a text-carrier, and any `run_usage` socket event.
 */
class SocketEventContractTest {

    private val socket = File("src/main/java/com/laozhudu/ekkostudio/ChatSocket.kt").readText()

    @Test
    fun liveUsageComesFromUsageUpdated() {
        assertTrue(
            "usage.updated is the only event carrying contextTokens",
            socket.contains("live.on(\"usage.updated\")"),
        )
    }

    @Test
    fun workStateComesFromSessionActivity() {
        assertTrue(socket.contains("live.on(name) {"))
        assertTrue(socket.contains("\"session.activity\""))
        assertTrue("status must be read and forwarded", socket.contains("RunEvent.Status(status)"))
    }

    @Test
    fun lifecycleNoticesAreSubscribed() {
        assertTrue(socket.contains("live.on(\"agent.event\")"))
        assertTrue(socket.contains("RunEvent.Notice("))
    }

    @Test
    fun plansArriveByPushAndPollingIsOnlyFallback() {
        assertTrue(socket.contains("live.on(\"plan.updated\")"))
        assertTrue(socket.contains("RunEvent.Plans("))
    }

    @Test
    fun streamingTextStillUsesMessageDelta() {
        assertTrue(socket.contains("live.on(\"message.delta\")"))
        assertTrue(
            "delta carries the increment; output carries the cumulative text",
            socket.contains("firstString(\"delta\", \"text\")"),
        )
    }
}
