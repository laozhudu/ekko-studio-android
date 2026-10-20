package com.laozhudu.ekkostudio

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

/**
 * One-shot resume that only extracts `taskPlans`.
 *
 * Studio has no REST endpoint for a session's plan and only ships it inside the
 * `/chat-run` resume payload, so a tiny throwaway socket is the cheapest way to
 * poll the plan without reloading the whole transcript.
 */
class PlanSocket(
    private val baseUrl: String,
    private val token: String,
) {

    private var socket: Socket? = null
    private var closed = false

    var onPlans: ((List<TaskPlanSnapshot>) -> Unit)? = null

    fun connect(sessionId: String, profile: String) {
        val options = IO.Options().apply {
            reconnection = false
            auth = mapOf("token" to token)
        }
        val manager = IO.manager(URI.create(baseUrl.trimEnd('/')), options)
        val namespaced = manager.socket("/chat-run")
        socket = namespaced

        namespaced.on(Socket.EVENT_CONNECT) {
            namespaced.emit(
                "resume",
                JSONObject().put("session_id", sessionId).put("profile", profile),
            )
        }
        namespaced.on("resumed") { args ->
            val payload = args.firstOrNull()
            val obj = when (payload) {
                is JSONObject -> payload
                null -> null
                else -> runCatching { JSONObject(payload.toString()) }.getOrNull()
            }
            if (obj != null) onPlans?.invoke(parseTaskPlans(obj))
            close()
        }
        namespaced.on(Socket.EVENT_CONNECT_ERROR) { close() }
        namespaced.connect()
    }

    fun close() {
        if (closed) return
        closed = true
        socket?.disconnect()
        socket = null
    }
}
