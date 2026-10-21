package com.laozhudu.ekkostudio

/**
 * Reads a function out of a Kotlin source file by **name**, not by signature.
 *
 * Several tests in this suite assert on the shape of production code (the app has
 * no instrumentation test target, so this is the only way to pin "the composer
 * must not submit on Enter" or "the run must re-attach instead of cancelling").
 * Those assertions used to scrape with the full signature:
 *
 *     viewModel.substringAfter("fun openSession(session: SessionSummary) {")
 *
 * which breaks whenever a parameter is added — twice so far, both times on a
 * change that was correct. The fix is to find the function by name and balance
 * the parentheses, so a new parameter, a new modifier (`private`, `suspend`,
 * `internal`), or a reformatted parameter list cannot invalidate the test.
 *
 * Everything here is deliberately dumb: a scanner that skips string literals and
 * comments, and counts brackets. It is not a parser and does not try to be one.
 */
object KotlinSource {

    /** `fun name(` somewhere in the file — modifiers and annotations before it are fine. */
    private fun declarationIndex(source: String, name: String): Int {
        val masked = mask(source)
        val pattern = Regex("""\bfun\s+${Regex.escape(name)}\s*\(""")
        return pattern.find(masked)?.range?.first ?: -1
    }

    fun hasFun(source: String, name: String): Boolean = declarationIndex(source, name) >= 0

    /**
     * Everything from `fun name(` to the end of the function body.
     *
     * For a block body that is the matching `}`; for an expression body
     * (`fun x() = expr`) it is the end of that statement, found by scanning to the
     * first newline at bracket depth zero.
     */
    fun funBody(source: String, name: String): String? {
        val start = declarationIndex(source, name)
        if (start < 0) return null
        val masked = mask(source)

        // 1. balance the parameter list
        var depth = 0
        var i = masked.indexOf('(', start)
        if (i < 0) return null
        var paramsEnd = -1
        while (i < masked.length) {
            when (masked[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { paramsEnd = i; break }
                }
            }
            i++
        }
        if (paramsEnd < 0) return null

        // 2. block body, or expression body
        var j = paramsEnd + 1
        var sawEquals = false
        while (j < masked.length) {
            when (masked[j]) {
                '{' -> break
                '=' -> { sawEquals = true; break }
                '\n' -> return source.substring(start, paramsEnd + 1)   // declaration only
                else -> j++
            }
        }
        if (j >= masked.length) return source.substring(start).trimEnd()
        if (sawEquals) {
            // to the end of the statement at depth 0
            var k = j
            var d = 0
            while (k < masked.length) {
                when (masked[k]) {
                    '(', '[', '{' -> d++
                    ')', ']', '}' -> d--
                    '\n' -> if (d <= 0) return source.substring(start, k)
                }
                k++
            }
            return source.substring(start).trimEnd()
        }
        // 3. matching brace
        var k = j
        var d = 0
        while (k < masked.length) {
            when (masked[k]) {
                '{' -> d++
                '}' -> {
                    d--
                    if (d == 0) return source.substring(start, k + 1)
                }
            }
            k++
        }
        return source.substring(start).trimEnd()
    }

    /** The declaration only: `fun name(a: Int, b: String)` with no body. */
    fun funSignature(source: String, name: String): String? =
        funBody(source, name)?.substringBefore('{')?.substringBefore(" = ")?.trim()

    /**
     * Replaces string literals and comments with spaces, keeping offsets intact so
     * bracket counting is not confused by `"{"` inside a message.
     */
    internal fun mask(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') { out.append(' '); i++ }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    out.append("  "); i += 2
                    while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) {
                        out.append(' '); i++
                    }
                    if (i + 1 < source.length) { out.append("  "); i += 2 }
                }
                c == '"' -> {
                    // triple-quoted first
                    if (i + 2 < source.length && source[i + 1] == '"' && source[i + 2] == '"') {
                        out.append("   "); i += 3
                        while (i + 2 < source.length && !(source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"')) {
                            out.append(' '); i++
                        }
                        if (i + 2 < source.length) { out.append("   "); i += 3 }
                    } else {
                        out.append(' '); i++
                        while (i < source.length) {
                            if (source[i] == '\\') { out.append("  "); i += 2; continue }
                            if (source[i] == '"') { out.append(' '); i++; break }
                            out.append(' '); i++
                        }
                    }
                }
                c == '\'' -> {
                    out.append(' '); i++
                    while (i < source.length) {
                        if (source[i] == '\\') { out.append("  "); i += 2; continue }
                        if (source[i] == '\'') { out.append(' '); i++; break }
                        out.append(' '); i++
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
