package com.laozhudu.ekkostudio

/**
 * A five-field cron expression, validated the way the server validates it.
 *
 * The rules were read out of the shipped server: it normalises four aliases,
 * requires exactly five fields, and then for each field parses a comma list of
 * `*`, `a`, `a-b` or `a-b/step` inside a fixed range —
 *
 *     minute 0-59 · hour 0-23 · day-of-month 1-31 · month 1-12 · day-of-week 0-6
 *
 * The point of duplicating it here is not purity: a phone keyboard plus a text
 * field is a reliable way to produce `0 9 * * MON`, and finding out after a round
 * trip that the server wanted `1` is worse than being told as you type.
 */
object CronExpression {

    /** The aliases the server accepts and what they expand to. */
    val ALIASES: Map<String, String> = mapOf(
        "@hourly" to "0 * * * *",
        "@daily" to "0 0 * * *",
        "@weekly" to "0 0 * * 0",
        "@monthly" to "0 0 1 * *",
    )

    /** Ranges per field, in field order. */
    private val RANGES = listOf(0..59, 0..23, 1..31, 1..12, 0..6)

    /** The server's alias expansion: case-insensitive, otherwise the text itself. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return ALIASES[trimmed.lowercase()] ?: trimmed
    }

    /**
     * Null when the expression is valid, otherwise the reason — the same phrasing
     * the server would return, so the message is not a surprise if it ever lands
     * there anyway.
     */
    fun validate(raw: String): String? {
        val fields = normalize(raw).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (fields.size != 5) return "schedule must be a five-field cron expression"
        fields.forEachIndexed { index, field ->
            if (!validField(field, RANGES[index])) return "invalid cron field"
        }
        return null
    }

    fun isValid(raw: String): Boolean = validate(raw) == null

    /** A comma list of `*`, `n`, `a-b`, each optionally `/step`. */
    internal fun validField(field: String, range: IntRange): Boolean {
        if (field.isBlank()) return false
        return field.split(',').all { part -> validPart(part, range) }
    }

    private fun validPart(part: String, range: IntRange): Boolean {
        if (part.isBlank()) return false
        val pieces = part.split('/')
        if (pieces.size > 2) return false
        val step = if (pieces.size == 2) pieces[1].toIntOrNull() else 1
        if (step == null || step < 1) return false
        val body = pieces[0]
        if (body == "*") return true
        val bounds = body.split('-')
        if (bounds.size > 2) return false
        val low = bounds[0].toIntOrNull() ?: return false
        val high = if (bounds.size == 2) bounds[1].toIntOrNull() ?: return false else low
        return low in range && high in range && low <= high
    }

    /** What to show under the field: the alias expansion, so the user sees the truth. */
    fun describe(raw: String): String {
        val trimmed = raw.trim()
        val normalised = normalize(trimmed)
        return if (normalised != trimmed) "$trimmed → $normalised" else normalised
    }

    /**
     * A rough "when will this next fire" for the presets and simple shapes.
     *
     * Deliberately not a scheduler: it understands `*` and a single number in the
     * minute/hour fields, and says nothing for anything more complex. A wrong
     * prediction would be worse than none.
     */
    fun describeSchedule(raw: String): String? {
        val fields = normalize(raw).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (fields.size != 5) return null
        val (minute, hour, day, month, weekday) = fields
        return when {
            fields.all { it == "*" } -> "every minute"
            minute == "*" && hour == "*" && day == "*" && month == "*" && weekday == "*" -> "every minute"
            minute.startsWith("*/") && hour == "*" -> "every ${minute.removePrefix("*/")} minutes"
            minute.toIntOrNull() != null && hour == "*" -> "every hour at :${minute.padStart(2, '0')}"
            minute.toIntOrNull() != null && hour.toIntOrNull() != null && day == "*" && month == "*" && weekday == "*" ->
                "daily at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
            minute.toIntOrNull() != null && hour.toIntOrNull() != null && weekday == "1" && day == "*" ->
                "Mondays at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
            minute.toIntOrNull() != null && hour.toIntOrNull() != null && day.toIntOrNull() != null && month == "*" ->
                "monthly on day $day at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
            else -> null
        }
    }
}
