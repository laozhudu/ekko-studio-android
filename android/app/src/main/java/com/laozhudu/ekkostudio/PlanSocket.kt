package com.laozhudu.ekkostudio

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
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
        val options = IO.Options.builder()
            .setForceNew(true)
            .setReconnection(false)
            .setAuth(mapOf("token" to token))
            .build()
        // The namespace is part of the URL, the same way ChatSocket connects.
        val live = IO.socket(URI.create(baseUrl.trimEnd('/') + "/chat-run"), options)
        socket = live

        live.on(Socket.EVENT_CONNECT) {
            live.emit(
                "resume",
                JSONObject().put("session_id", sessionId).put("profile", profile),
            )
        }
        live.on("resumed", Emitter.Listener { args ->
            val payload = args.firstOrNull()
            val obj = when (payload) {
                is JSONObject -> payload
                null -> null
                else -> runCatching { JSONObject(payload.toString()) }.getOrNull()
            }
            if (obj != null) onPlans?.invoke(parseTaskPlans(obj))
            close()
        })
        live.on(Socket.EVENT_CONNECT_ERROR) { close() }
        live.connect()
    }

    fun close() {
        if (closed) return
        closed = true
        socket?.disconnect()
        socket = null
    }
}
