package com.laozhudu.ekkostudio

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** What a run tells us while it is happening. */
sealed interface RunEvent {
    data class Started(val occurredAtMillis: Long) : RunEvent
    data class Text(val delta: String) : RunEvent
    data class Reasoning(val delta: String) : RunEvent
    data class Tool(
        val id: String,
        val name: String,
        val detail: String?,
        val status: ToolRunStatus,
        val durationSeconds: Double?,
        val occurredAtMillis: Long,
    ) : RunEvent
    data class Usage(val contextTokens: Long, val contextWindow: Long?) : RunEvent
    /** `session.activity{status}`: running | completed | failed. */
    data class Status(val status: String) : RunEvent
    /** `agent.event{kind,text}`: lifecycle warnings, retries, tool chatter. */
    data class Notice(val kind: String, val text: String) : RunEvent
    /** `plan.updated`: the live task plan, no polling needed. */
    data class Plans(val plans: List<TaskPlanSnapshot>) : RunEvent
    /** `session.title.updated`: the agent named or renamed the conversation. */
    data class TitleChanged(val title: String) : RunEvent
    /** `compression.*`: context compaction, with the numbers the payload carries. */
    data class Compression(
        val running: Boolean,
        val messageCount: Int = 0,
        val beforeTokens: Long = 0,
        val afterTokens: Long = 0,
    ) : RunEvent
    /** `message.interim`: a whole message at once, so the bubble stops streaming. */
    data class Interim(val text: String) : RunEvent
    /** `reasoning.available`: a reasoning fold exists before any delta arrives. */
    object ReasoningAvailable : RunEvent
    /** `session.activity.snapshot`: sessions already running when we connect. */
    data class RunningSessions(val ids: List<String>) : RunEvent
    /** `session.workspace.updated`: the agent moved this session's workspace. */
    data class WorkspaceChanged(val path: String) : RunEvent
    /** `workspace.diff.completed`: this run touched files. */
    data class WorkspaceDiff(val changeId: String, val files: Int) : RunEvent
    /** `peer.user.message`: another device posted into this conversation. */
    object PeerMessage : RunEvent
    /** `app.notification`: the server's own run-completion notice. */
    data class CompletionNotice(val title: String, val content: String, val sessionId: String) : RunEvent
    /** `abort.started|timeout|completed`: whether the server really stopped. */
    data class AbortPhase(val phase: String) : RunEvent
    data class Done(val output: String, val reasoning: String) : RunEvent
    data class RequiresAction(
        val kind: RequiredAction,
        val id: String,
        val prompt: String,
        val options: List<String> = emptyList(),
    ) : RunEvent
    data class ActionResolved(val kind: RequiredAction, val id: String, val resolved: Boolean) : RunEvent
    data class QueueChanged(val messages: List<QueuedRun>?, val insertionActive: Boolean?) : RunEvent
    data class BackgroundAgent(val task: BackgroundAgentRun) : RunEvent
    data class ResumedState(
        val pageId: String?,
        val messages: List<ResumedMessage>?,
        val model: String?,
        val provider: String?,
        val reasoningEffort: String?,
        val workspace: String?,
        val workspaceChanges: List<WorkspaceRunChange>,
        val taskPlans: List<TaskPlanSnapshot> = emptyList(),
    ) : RunEvent
    data class Failed(val error: String, val retryableTransport: Boolean) : RunEvent
}

enum class ToolRunStatus { Running, Done, Error }

enum class RequiredAction { Approval, Clarification }

data class ResumedMessage(
    val id: String?,
    val role: String,
    val content: String,
    val reasoning: String?,
)

data class TaskPlanStep(val id: String, val step: String, val status: String)

data class TaskPlanSnapshot(
    val planId: String,
    val revision: Int,
    val executionState: String,
    /** Server epoch millis; newest plan in the array wins. */
    val updatedAt: Long,
    val steps: List<TaskPlanStep>,
)

data class WorkspaceRunChange(
    val id: String,
    val assistantMessageId: String?,
    val workspace: String?,
    val filesChanged: Int,
    val additions: Int,
    val deletions: Int,
)

/**
 * The streaming half of the chat API.
 *
 * `POST /api/chat-run/runs` is the server's own wrapper around this socket: it
 * connects, waits for the whole answer, and returns it. Talking to /chat-run
 * directly is the same conversation, except the words arrive as they are
 * written, and the run can be stopped part-way.
 */
class ChatSocket(
    private var baseUrl: String,
    private var token: String,
) {
    private var socket: Socket? = null

    fun update(baseUrl: String, token: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.token = token
    }

    /** Stops the run the server is streaming for this session. */
    fun abort(sessionId: String) {
        runCatching { socket?.emit("abort", JSONObject().put("session_id", sessionId)) }
    }

    fun respondToApproval(sessionId: String, approvalId: String, choice: String) {
        socket?.emit("approval.respond", JSONObject().put("session_id", sessionId).put("approval_id", approvalId).put("choice", choice))
    }

    fun respondToClarification(sessionId: String, clarifyId: String, response: String) {
        socket?.emit("clarify.respond", JSONObject().put("session_id", sessionId).put("clarify_id", clarifyId).put("response", response))
    }

    fun insertQueuedRun(sessionId: String, queueId: String) {
        socket?.emit("insert_queued_run", JSONObject().put("session_id", sessionId).put("queue_id", queueId))
    }

    fun cancelQueuedRun(sessionId: String, queueId: String) {
        socket?.emit("cancel_queued_run", JSONObject().put("session_id", sessionId).put("queue_id", queueId))
    }

    fun run(
        profile: String,
        sessionId: String,
        input: String,
        attachments: List<Upload>,
        reasoningEffort: String?,
        model: String?,
        provider: String?,
        runtime: AgentRuntimeSelection = AgentRuntimeSelection(),
        cachedPageId: String? = null,
        /**
         * Group the new conversation into this category as it is created.
         *
         * The server's run handler reads `category_id` off the payload, which is
         * the only way to file the first message of a conversation — a session
         * does not exist until a run creates it, so there is nothing to PATCH.
         */
        categoryId: Int? = null,
        /** Starting workspace for a brand-new conversation; same reasoning. */
        workspace: String? = null,
    ): Flow<RunEvent> = callbackFlow {
        val payload = JSONObject()
            .put("input", contentFor(input, attachments))
            .put("profile", profile)
            .put("session_id", sessionId)
        if (!reasoningEffort.isNullOrBlank()) payload.put("reasoning_effort", reasoningEffort)
        if (categoryId != null && categoryId > 0) payload.put("category_id", categoryId)
        if (!workspace.isNullOrBlank()) payload.put("workspace", workspace)
        if (!model.isNullOrBlank()) payload.put("model", model)
        if (!provider.isNullOrBlank()) payload.put("provider", provider)
        if (!runtime.isHermes) {
            payload.put("source", if (runtime.globalAgent) "global_agent" else "coding_agent")
            if (runtime.globalAgent) payload.put("session_source", "global_agent")
            payload.put("coding_agent_id", runtime.codingAgentId)
            payload.put("mode", "global")
        }

        val options = IO.Options.builder()
            .setForceNew(true)
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(1_000)
            .setReconnectionDelayMax(30_000)
            .setTransports(arrayOf(WebSocket.NAME, Polling.NAME))
            .setAuth(mapOf("token" to token))
            .setQuery("profile=" + URLEncoder.encode(profile, "UTF-8"))
            .setTimeout(30_000)
            .build()

        val live = IO.socket(URI.create(baseUrl.trimEnd('/') + "/chat-run"), options)
        socket = live
        var runStarted = false
        var terminal = false
        var submitted = false
        var resumePageId = cachedPageId

        fun eventPayload(args: Array<out Any?>): JSONObject? = args.firstOrNull() as? JSONObject

        live.on(Socket.EVENT_CONNECT) {
            if (!submitted) {
                submitted = true
                live.emit("run", payload)
            } else {
                // A mobile network can change while an agent is working. Join
                // the existing session again instead of submitting the turn a
                // second time or showing a false "disconnected" reply.
                val resume = JSONObject().put("session_id", sessionId).put("profile", profile)
                live.emit("app.resume", resume.put("id", resumePageId.orEmpty()))
            }
        }
        fun restoreSnapshot(event: JSONObject) {
            event.optString("id").takeIf(String::isNotBlank)?.let { resumePageId = it }
            trySend(parseResumedState(event))
            usageFrom(event)?.let { trySend(it) }
            trySend(RunEvent.QueueChanged(parseQueue(event), event.optJSONObject("queueInsertion") != null))
            event.optJSONArray("backgroundTasks")?.let { tasks ->
                for (index in 0 until tasks.length()) parseBackgroundTask(tasks.optJSONObject(index))?.let {
                    trySend(RunEvent.BackgroundAgent(it))
                }
            }
            event.optJSONArray("events")?.let { events ->
                for (index in 0 until events.length()) {
                    val restored = events.optJSONObject(index) ?: continue
                    val data = restored.optJSONObject("data") ?: restored
                    when (restored.optString("event")) {
                        "approval.requested" -> parseRequiredAction(data, RequiredAction.Approval)?.let { trySend(it) }
                        "clarify.requested" -> parseRequiredAction(data, RequiredAction.Clarification)?.let { trySend(it) }
                    }
                }
            }
        }
        fun handleResume(args: Array<out Any?>) {
            val event = eventPayload(args) ?: return
            restoreSnapshot(event)
            if (event.optBoolean("isWorking", false)) {
                runStarted = true
                return
            }
            if (event.optInt("queue_remaining", 0) > 0 || event.optInt("background_pending", 0) > 0) return
            terminal = true
            val completion = completionFromResume(event)
            if (completion != null) {
                trySend(completion)
            } else {
                trySend(RunEvent.Failed("run finished without output", retryableTransport = false))
            }
            close()
        }
        live.on("resumed") { args -> handleResume(args) }
        live.on("app.resumed") { args -> handleResume(args) }
        live.on("run.started") {
            runStarted = true
            trySend(RunEvent.Started(System.currentTimeMillis()))
        }
        live.on("message.delta") { args ->
            val delta = eventPayload(args).firstString("delta", "text")
            if (delta.isNotEmpty()) {
                runStarted = true
                trySend(RunEvent.Text(delta))
            }
        }
        // Some models report thinking under one name, some under the other.
        listOf("reasoning.delta", "thinking.delta").forEach { event ->
            live.on(event) { args ->
                val delta = eventPayload(args).firstString("delta", "text")
                if (delta.isNotEmpty()) {
                    runStarted = true
                    trySend(RunEvent.Reasoning(delta))
                }
            }
        }
        live.on("tool.started") { args ->
            runStarted = true
            parseToolEvent(
                event = eventPayload(args),
                status = ToolRunStatus.Running,
                occurredAtMillis = System.currentTimeMillis(),
            )?.let { trySend(it) }
        }
        live.on("tool.completed") { args ->
            runStarted = true
            parseToolEvent(
                event = eventPayload(args),
                status = ToolRunStatus.Done,
                occurredAtMillis = System.currentTimeMillis(),
            )?.let { trySend(it) }
        }
        live.on("tool.failed") { args ->
            runStarted = true
            parseToolEvent(
                event = eventPayload(args),
                status = ToolRunStatus.Error,
                occurredAtMillis = System.currentTimeMillis(),
            )?.let { trySend(it) }
        }
        // Live context accounting. Verified against the server: the event is
        // called `usage.updated` and carries
        // {inputTokens, outputTokens, contextTokens}; there is no
        // `context.estimated` and no `run.usage.updated` on this transport.
        live.on("usage.updated") { args ->
            eventPayload(args)?.let { event -> usageFromEstimate(event)?.let { trySend(it) } }
        }
        live.on("context.estimated") { args ->
            eventPayload(args)?.let { event -> usageFromEstimate(event)?.let { trySend(it) } }
        }
        live.on("run.usage.updated") { args ->
            eventPayload(args)?.let { event -> usageFromEstimate(event)?.let { trySend(it) } }
        }
        // Is the agent working right now? `session.activity` is the only event
        // that answers that, and it fires for running / completed / failed.
        listOf("session.activity", "app.activity").forEach { name ->
            live.on(name) { args ->
                val status = eventPayload(args)?.optString("status").orEmpty()
                if (status.isNotBlank()) trySend(RunEvent.Status(status))
            }
        }
        // Lifecycle chatter — retries, warnings, "auxiliary title generation
        // failed". Without it a failing provider looks like a silent hang.
        live.on("agent.event") { args ->
            val event = eventPayload(args) ?: return@on
            val text = event.optString("text")
            if (text.isNotBlank()) {
                runStarted = true
                trySend(RunEvent.Notice(event.optString("kind").ifBlank { "info" }, text))
            }
        }
        // The plan arrives push-style; the 10s resume poller is only a fallback.
        live.on("plan.updated") { args ->
            eventPayload(args)?.let { event ->
                val plans = parseTaskPlans(event)
                if (plans.isNotEmpty()) trySend(RunEvent.Plans(plans))
            }
        }
        // Events the web client listens to and the app used to ignore. All of them
        // are plain socket events on the same authenticated channel, so a native
        // client can use them exactly like the browser does.
        live.on("session.title.updated") { args ->
            eventPayload(args)?.optString("title")?.takeIf { it.isNotBlank() }?.let {
                trySend(RunEvent.TitleChanged(it))
            }
        }
        // The payload carries the size of the compaction, which is the only
        // honest way to say what happened: "压缩 87 条 · 51.2k → 12.4k".
        live.on("compression.started") { args ->
            val event = eventPayload(args)
            trySend(
                RunEvent.Compression(
                    running = true,
                    messageCount = event?.optInt("message_count") ?: 0,
                    beforeTokens = event?.optLong("token_count") ?: 0,
                ),
            )
        }
        live.on("compression.completed") { args ->
            val event = eventPayload(args)
            trySend(
                RunEvent.Compression(
                    running = false,
                    messageCount = event?.optInt("totalMessages") ?: 0,
                    beforeTokens = event?.optLong("beforeTokens") ?: 0,
                    afterTokens = event?.optLong("contextTokens") ?: (event?.optLong("afterTokens") ?: 0),
                ),
            )
        }
        // A model can answer in one piece, with no deltas at all. The official
        // client replaces the streaming bubble with this text; without it such
        // a run looks empty until it completes.
        live.on("message.interim") { args ->
            val text = eventPayload(args)?.let { event -> event.firstString("text", "content") }.orEmpty()
            if (text.isNotBlank()) {
                runStarted = true
                trySend(RunEvent.Interim(text))
            }
        }
        // "There is reasoning for this turn", sent before the deltas: it is what
        // lets the fold appear immediately instead of after the first token.
        live.on("reasoning.available") { _ ->
            runStarted = true
            trySend(RunEvent.ReasoningAvailable)
        }
        // Which sessions are already working when this phone connects. Without
        // it a run started on the desktop is invisible in the list until reload.
        live.on("session.activity.snapshot") { args ->
            val sessions = eventPayload(args)?.optJSONArray("sessions") ?: return@on
            val ids = (0 until sessions.length()).mapNotNull { index ->
                val row = sessions.optJSONObject(index) ?: return@mapNotNull null
                row.optString("session_id").takeIf { row.optString("status") == "running" && it.isNotBlank() }
            }
            trySend(RunEvent.RunningSessions(ids))
        }
        live.on("session.workspace.updated") { args ->
            eventPayload(args)?.optString("workspace")?.takeIf { it.isNotBlank() }
                ?.let { trySend(RunEvent.WorkspaceChanged(it)) }
        }
        live.on("workspace.diff.completed") { args ->
            val event = eventPayload(args) ?: return@on
            val changeId = event.firstString("change_id", "id")
            val files = event.optJSONObject("change")?.optJSONArray("files")?.length() ?: 0
            if (changeId.isNotBlank()) trySend(RunEvent.WorkspaceDiff(changeId, files))
        }
        // Another device (the desktop, or a second phone) posting into the same
        // conversation. Two names exist on the wire; both mean "reload".
        listOf("peer.user.message", "run.peer_user_message").forEach { name ->
            live.on(name) { _ -> trySend(RunEvent.PeerMessage) }
        }
        // The server already writes the notification; the client only has to
        // show it. Fired on completion for runs this phone did not start too.
        live.on("app.notification") { args ->
            val event = eventPayload(args) ?: return@on
            trySend(
                RunEvent.CompletionNotice(
                    title = event.optString("title"),
                    content = event.optString("content"),
                    sessionId = event.optString("sessionId").ifBlank { event.optString("session_id") },
                ),
            )
        }
        // Abort is asynchronous on the server: "timeout" means it did NOT stop.
        listOf("abort.started", "abort.completed", "abort.timeout").forEach { name ->
            live.on(name) { args ->
                val phase = name.substringAfter('.')
                trySend(RunEvent.AbortPhase(phase))
                if (phase == "completed" && (eventPayload(args)?.optInt("queue_remaining", 0) ?: 0) == 0) {
                    terminal = true
                    close()
                }
            }
        }
        // The server forwards background work as a single `subagent.event` (it is
        // in the session whitelist under that exact name; the per-phase names the
        // web bundle registers belong to a newer bridge). Surface its type and text
        // as a notice rather than inventing a richer model than the payload has.
        live.on("subagent.event") { args ->
            val event = eventPayload(args) ?: return@on
            val kind = event.optString("type").ifBlank { event.optString("kind") }.ifBlank { "subagent" }
            val text = event.optString("text").ifBlank {
                event.optString("summary").ifBlank { event.optString("label") }
            }
            if (text.isNotBlank()) trySend(RunEvent.Notice("subagent", "$kind: $text"))
        }
        live.on("run.reattach_failed") { args ->
            // The resume could not be re-attached; tell the user instead of
            // leaving a spinner running forever.
            val reason = eventPayload(args)?.optString("error").orEmpty()
            trySend(RunEvent.Notice("warn", reason.ifBlank { "run re-attach failed" }))
        }
        live.on("run.completed") { args ->
            val event = args.firstOrNull() as? JSONObject
            usageFrom(event)?.let { trySend(it) }
            trySend(
                RunEvent.Done(
                    output = event?.optString("output").orEmpty(),
                    reasoning = event?.optString("reasoning").orEmpty(),
                ),
            )
            if ((event?.optInt("queue_remaining", 0) ?: 0) == 0 && (event?.optInt("background_pending", 0) ?: 0) == 0) {
                terminal = true
                close()
            }
        }
        live.on("run.failed") { args ->
            val event = args.firstOrNull() as? JSONObject
            val message = event?.optString("error").orEmpty()
            trySend(RunEvent.Failed(message.ifBlank { "run failed" }, retryableTransport = false))
            if ((event?.optInt("queue_remaining", 0) ?: 0) == 0 && (event?.optInt("background_pending", 0) ?: 0) == 0) {
                terminal = true
                close()
            }
        }
        // Keep the full interaction identity so the mobile decision is applied
        // to the exact interrupted run, including after a reconnect.
        live.on("approval.requested") { args ->
            val event = eventPayload(args) ?: JSONObject()
            parseRequiredAction(event, RequiredAction.Approval)?.let { trySend(it) }
        }
        live.on("clarify.requested") { args ->
            val event = eventPayload(args) ?: JSONObject()
            parseRequiredAction(event, RequiredAction.Clarification)?.let { trySend(it) }
        }
        live.on("approval.resolved") { args -> eventPayload(args)?.let { event -> trySend(RunEvent.ActionResolved(RequiredAction.Approval, event.firstString("approval_id", "id"), event.optBoolean("resolved", true))) } }
        live.on("clarify.resolved") { args -> eventPayload(args)?.let { event -> trySend(RunEvent.ActionResolved(RequiredAction.Clarification, event.firstString("clarify_id", "id"), event.optBoolean("resolved", true))) } }
        live.on("run.queued") { args -> eventPayload(args)?.let { event -> trySend(RunEvent.QueueChanged(parseQueue(event), null)) } }
        live.on("run.queue_insertion.updated") { args -> eventPayload(args)?.let { event -> trySend(RunEvent.QueueChanged(null, event.optString("phase").isNotBlank() && event.optString("phase") != "cancelled")) } }
        listOf("subagent.start", "subagent.tool", "subagent.progress", "subagent.text", "subagent.thinking", "subagent.complete", "delegation.updated").forEach { name ->
            live.on(name) { args -> eventPayload(args)?.let { event -> parseBackgroundTask(event, name.substringAfter('.'))?.let { trySend(RunEvent.BackgroundAgent(it)) } } }
        }
        live.on(Socket.EVENT_CONNECT_ERROR) { args ->
            // Before the first successful connection REST is the safe fallback.
            // Afterwards Socket.IO owns reconnection and resume; surfacing every
            // temporary radio/network loss as an assistant message is wrong.
            if (!submitted) {
                terminal = true
                val detail = args.firstOrNull()?.toString().orEmpty()
                trySend(RunEvent.Failed(detail.ifBlank { "connect_error" }, retryableTransport = true))
                close()
            }
        }
        live.on(Socket.EVENT_DISCONNECT) { args ->
            if (!terminal && !submitted) {
                trySend(RunEvent.Failed("disconnected", retryableTransport = !runStarted))
                close()
            } else if (!terminal && args.firstOrNull()?.toString() == "io server disconnect") {
                // Server-requested disconnects are not retried automatically by
                // Socket.IO, while transport and network disconnects are.
                live.connect()
            }
        }

        live.connect()

        awaitClose {
            live.off()
            live.disconnect()
            if (socket === live) socket = null
        }
    }

    private fun parseRequiredAction(event: JSONObject, kind: RequiredAction): RunEvent.RequiresAction? {
        val id = if (kind == RequiredAction.Approval) event.firstString("approval_id", "id") else event.firstString("clarify_id", "id")
        if (id.isBlank()) return null
        val options = (event.optJSONArray("choices") ?: event.optJSONArray("options"))?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val prompt = if (kind == RequiredAction.Approval) event.firstString("description", "command", "prompt", "message") else event.firstString("prompt", "question", "message")
        return RunEvent.RequiresAction(kind, id, prompt, options)
    }

    private fun parseQueue(event: JSONObject): List<QueuedRun>? {
        val array = event.optJSONArray("queued_messages") ?: event.optJSONArray("queueMessages") ?: return null
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.firstString("queue_id", "id")
            if (id.isBlank()) return@mapNotNull null
            val raw = item.opt("input")
            val preview = item.firstString("display_input", "preview").ifBlank { raw?.toString().orEmpty() }
            QueuedRun(id, preview.take(180), index + 1)
        }
    }

    private fun parseBackgroundTask(event: JSONObject?, fallbackStatus: String = ""): BackgroundAgentRun? {
        event ?: return null
        val id = event.firstString("subagent_id", "task_id", "id")
        if (id.isBlank()) return null
        return BackgroundAgentRun(
            id = id,
            runtime = event.firstString("runtime", "agent", "agent_id"),
            status = event.firstString("status", "phase").ifBlank { fallbackStatus },
            label = event.firstString("label", "name", "task", "goal", "prompt").ifBlank { id },
            output = event.firstString("output", "summary", "result", "error").takeIf(String::isNotBlank),
        )
    }

    /** Studio accepts a bare string, or blocks when files ride along. */
    private fun contentFor(input: String, attachments: List<Upload>): Any =
        if (attachments.isEmpty()) {
            input
        } else {
            JSONArray().apply {
                if (input.isNotBlank()) put(JSONObject().put("type", "text").put("text", input))
                attachments.forEach { file ->
                    put(
                        JSONObject()
                            .put("type", if (file.mime.startsWith("image/")) "image" else "file")
                            .put("name", file.name)
                            .put("path", file.path)
                            .put("media_type", file.mime),
                    )
                }
            }
        }
}

internal fun parseResumedState(payload: JSONObject): RunEvent.ResumedState {
    val messages = if (payload.optBoolean("messagesCached", false)) null else payload.optJSONArray("messages")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val role = item.optString("display_role").ifBlank { item.optString("role") }
            val raw = item.opt("display_content").takeUnless { it == null || it == JSONObject.NULL }
                ?: item.opt("content")
            val content = when (raw) {
                is String -> raw
                is JSONArray -> (0 until raw.length()).mapNotNull { partIndex ->
                    val part = raw.optJSONObject(partIndex)
                    part?.optString("text")?.takeIf(String::isNotBlank)
                }.joinToString("\n")
                null, JSONObject.NULL -> ""
                else -> raw.toString()
            }
            if (role.isBlank() || content.isBlank()) return@mapNotNull null
            ResumedMessage(
                id = item.firstString("id", "message_id").takeIf(String::isNotBlank),
                role = role,
                content = content,
                reasoning = item.optString("reasoning").takeIf(String::isNotBlank),
            )
        }
    }
    val changes = payload.optJSONArray("workspaceRunChanges")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.firstString("change_id", "id")
            if (id.isBlank()) return@mapNotNull null
            WorkspaceRunChange(
                id = id,
                assistantMessageId = item.firstString("assistant_message_id", "message_id").takeIf(String::isNotBlank),
                workspace = item.optString("workspace").takeIf(String::isNotBlank),
                filesChanged = item.optInt("files_changed"),
                additions = item.optInt("additions"),
                deletions = item.optInt("deletions"),
            )
        }
    }.orEmpty()
    return RunEvent.ResumedState(
        pageId = payload.optString("id").takeIf(String::isNotBlank),
        messages = messages,
        model = payload.optString("model").takeIf(String::isNotBlank),
        provider = payload.optString("provider").takeIf(String::isNotBlank),
        reasoningEffort = payload.firstString("reasoning_effort", "reasoningEffort").takeIf(String::isNotBlank),
        workspace = payload.optString("workspace").takeIf(String::isNotBlank),
        workspaceChanges = changes,
        taskPlans = parseTaskPlans(payload),
    )
}

/** Studio ships every plan revision for the session; newest revision wins in the UI. */
internal fun parseTaskPlans(payload: JSONObject): List<TaskPlanSnapshot> {
    val array = payload.optJSONArray("taskPlans") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val rawSteps = item.optJSONArray("plan") ?: item.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until rawSteps.length()).mapNotNull { stepIndex ->
            val step = rawSteps.optJSONObject(stepIndex) ?: return@mapNotNull null
            TaskPlanStep(
                id = step.optString("id"),
                step = step.optString("step").ifBlank { step.optString("title") },
                status = step.optString("status").ifBlank { "pending" },
            )
        }.filter { it.step.isNotBlank() }
        if (steps.isEmpty()) return@mapNotNull null
        // Server sends millis; tolerate seconds from older builds.
        val rawUpdated = item.optLong("updated_at", 0L)
        val updatedAt = if (rawUpdated in 1..9_999_999_999L) rawUpdated * 1000L else rawUpdated
        TaskPlanSnapshot(
            planId = item.optString("plan_id"),
            revision = item.optInt("revision", 0),
            executionState = item.optString("execution_state"),
            updatedAt = updatedAt,
            steps = steps,
        )
    }
}

/**
 * Reads a context estimate out of any of the payload shapes the server uses:
 *
 * - `context.estimated`: `{run_id, contextTokens, context_tokens, ...}`
 * - `run.usage.updated`: `{session_id, run_id, run_usage:{...}}`, and the record
 *   itself may carry `contextTokens` or only the token breakdown
 * - `run.completed` / `run.failed`: usage fields inline
 *
 * The estimate is the only trustworthy "current prompt size"; a persisted
 * `input_tokens` figure is a single request's increment and is deliberately not
 * used here.
 */
internal fun usageFromEstimate(payload: JSONObject?): RunEvent.Usage? {
    payload ?: return null
    fun number(source: JSONObject, vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        if (!source.has(key) || source.isNull(key)) return@firstNotNullOfOrNull null
        when (val value = source.opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
    val record = payload.optJSONObject("run_usage") ?: payload
    val used = number(record, "contextTokens", "context_tokens", "tokenCount", "token_count")
        ?: number(payload, "contextTokens", "context_tokens")
        ?: return null
    val window = number(record, "contextWindow", "context_window", "contextLength", "context_length")
        ?: number(payload, "contextWindow", "context_window", "contextLength", "context_length")
    return RunEvent.Usage(
        contextTokens = used.coerceAtLeast(0),
        contextWindow = window?.takeIf { it > 0 },
    )
}

internal fun usageFrom(payload: JSONObject?): RunEvent.Usage? {
    payload ?: return null
    fun number(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        if (!payload.has(key) || payload.isNull(key)) return@firstNotNullOfOrNull null
        when (val value = payload.opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
    val used = number("contextTokens", "context_tokens", "tokenCount", "token_count") ?: return null
    return RunEvent.Usage(
        contextTokens = used.coerceAtLeast(0),
        contextWindow = number("contextWindow", "context_window", "contextLength", "context_length")
            ?.takeIf { it > 0 },
    )
}

/** Returns the answer persisted while this phone was temporarily offline. */
internal fun completionFromResume(payload: JSONObject): RunEvent.Done? {
    if (payload.optBoolean("isWorking", false)) return null
    val messages = payload.optJSONArray("messages") ?: return null
    var lastUserIndex = -1
    for (index in 0 until messages.length()) {
        val role = messages.optJSONObject(index)?.optString("role").orEmpty()
        if (role == "user" || role == "command") lastUserIndex = index
    }
    if (lastUserIndex < 0) return null

    for (index in messages.length() - 1 downTo lastUserIndex + 1) {
        val message = messages.optJSONObject(index) ?: continue
        if (message.optString("role") != "assistant") continue
        val output = message.optString("display_content")
            .ifBlank { message.optString("content") }
        if (output.isBlank()) continue
        return RunEvent.Done(
            output = output,
            reasoning = message.optString("reasoning"),
        )
    }
    return null
}

/** Normalizes tool payloads emitted by both current and older Studio bridges. */
internal fun parseToolEvent(
    event: JSONObject?,
    status: ToolRunStatus,
    occurredAtMillis: Long,
): RunEvent.Tool? {
    event ?: return null
    val id = event.firstString("tool_call_id", "call_id", "id")
    val name = event.firstString("tool", "name", "tool_name", "function_name")
    if (id.isBlank() && name.isBlank()) return null

    val duration = listOf("duration_seconds", "duration")
        .firstNotNullOfOrNull { key ->
            if (!event.has(key) || event.isNull(key)) return@firstNotNullOfOrNull null
            event.optDouble(key).takeIf { it.isFinite() && it >= 0.0 }
        }
    val reportedError = event.opt("error").let { value ->
        when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.isNotBlank() && !value.equals("false", ignoreCase = true)
            else -> false
        }
    }

    return RunEvent.Tool(
        id = id,
        name = name.ifBlank { "tool" },
        detail = event.toolDetail(),
        status = if (status == ToolRunStatus.Done && reportedError) ToolRunStatus.Error else status,
        durationSeconds = duration,
        occurredAtMillis = occurredAtMillis,
    )
}

private fun JSONObject?.firstString(vararg keys: String): String {
    if (this == null) return ""
    return keys.firstNotNullOfOrNull { key ->
        if (!has(key) || isNull(key)) return@firstNotNullOfOrNull null
        optString(key).trim().takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JSONObject.toolDetail(): String? {
    firstString("preview", "detail").takeIf { it.isNotBlank() }?.let { return it.oneLine() }
    val raw = listOf("arguments", "args", "function_args")
        .firstNotNullOfOrNull { key -> opt(key).takeUnless { it == null || it == JSONObject.NULL } }
        ?: return null
    val selected = if (raw is JSONObject) {
        listOf("command", "cmd", "path", "file_path", "query", "url", "prompt")
            .firstNotNullOfOrNull { key -> raw.opt(key).takeUnless { it == null || it == JSONObject.NULL } }
            ?: raw
    } else {
        raw
    }
    return selected.toString().oneLine().takeIf { it.isNotBlank() }
}

private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim().let {
    if (it.length <= 180) it else it.take(177) + "…"
}
