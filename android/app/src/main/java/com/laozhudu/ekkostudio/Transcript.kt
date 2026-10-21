package com.laozhudu.ekkostudio

/**
 * Turns server messages into transcript lines the way the web client does.
 *
 * Verified against a live 0.7.38 conversation:
 *
 *  - `user`      -> own bubble
 *  - `assistant` -> bubble carrying its own thinking, under `reasoning` or
 *                   `reasoning_content`; this is what makes the folded thinking
 *                   block survive a reload instead of vanishing with the stream
 *  - `tool`      -> NOT a bubble. Each row is a step folded into the reply it
 *                   helped produce, so the transcript stops showing raw JSON
 *                   payloads as if the agent had said them
 *  - `command`   -> system notice ("branched session …"), not a message
 */
internal fun messagesToLines(messages: List<Message>): List<ChatLine> {
    val out = mutableListOf<ChatLine>()
    val pendingTools = mutableListOf<ChatToolStep>()
    for (message in messages) {
        when (message.role) {
            "user" -> out += ChatLine(
                text = message.content,
                fromUser = true,
                timestamp = message.timestamp,
                messageId = message.id,
            )

            // Held back until the reply they belong to appears.
            "tool" -> pendingTools += ChatToolStep(
                id = message.id,
                name = message.toolName?.takeIf { it.isNotBlank() } ?: "tool",
                detail = null,
                status = ToolRunStatus.Done,
                // Historical rows have no timing of their own; the process block
                // groups them by the reply they belong to.
                startedAtMillis = 0,
                durationSeconds = null,
            )

            "assistant" -> {
                out += ChatLine(
                    text = message.content,
                    fromUser = false,
                    timestamp = message.timestamp,
                    messageId = message.id,
                    reasoning = message.reasoning?.takeIf { it.isNotBlank() },
                    tools = pendingTools.toList(),
                )
                pendingTools.clear()
            }

            else -> Unit
        }
    }
    return out
}
