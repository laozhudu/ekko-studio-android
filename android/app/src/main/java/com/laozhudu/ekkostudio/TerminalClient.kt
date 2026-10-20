package com.laozhudu.ekkostudio

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Thin client for Studio's `/api/hermes/terminal` socket.
 *
 * Protocol (verified against the server bundle):
 *  - the server pushes raw pty output as plain text frames
 *  - control frames are JSON: created / switched / exited / error
 *  - client commands: create, switch, close, resize, input
 *
 * Every keystroke is sent as `{"type":"input"}`, never as a raw frame: the
 * server treats any frame whose first byte is `{` as JSON, so raw input would
 * corrupt commands that start with a brace.
 */
class TerminalClient(
    private val baseUrl: String,
    private val token: String,
) {

    private var socket: WebSocket? = null
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    var onOutput: ((String) -> Unit)? = null
    var onCreated: ((id: String, shell: String) -> Unit)? = null
    var onExited: ((id: String, code: Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    fun connect(cols: Int, rows: Int) {
        val wsBase = baseUrl.trimEnd('/')
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
        val url = buildString {
            append(wsBase)
            append("/api/hermes/terminal?token=")
            append(URLEncoder.encode(token, "UTF-8"))
            append("&cols=").append(cols)
            append("&rows=").append(rows)
        }
        socket?.cancel()
        socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.startsWith("{")) {
                val obj = runCatching { JSONObject(text) }.getOrNull()
                when (obj?.optString("type")) {
                    "created" -> onCreated?.invoke(obj.optString("id"), obj.optString("shell"))
                    "switched" -> Unit
                    "exited" -> onExited?.invoke(obj.optString("id"), obj.optInt("exitCode"))
                    "error" -> onError?.invoke(obj.optString("message"))
                    else -> onOutput?.invoke(text)
                }
            } else {
                onOutput?.invoke(text)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onOutput?.invoke(bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onError?.invoke(t.message ?: "connection failed")
            onClosed?.invoke()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onClosed?.invoke()
        }
    }

    fun sendInput(data: String) {
        socket?.send(JSONObject().put("type", "input").put("data", data).toString())
    }

    fun resize(cols: Int, rows: Int) {
        socket?.send(
            JSONObject().put("type", "resize").put("cols", cols).put("rows", rows).toString(),
        )
    }

    fun close() {
        socket?.close(1000, "bye")
        socket = null
    }
}
