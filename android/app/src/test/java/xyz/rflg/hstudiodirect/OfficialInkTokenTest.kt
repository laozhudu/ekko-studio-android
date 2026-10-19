package xyz.rflg.hstudiodirect

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the `--ink-*` palette to the tokens compiled into the official HStudio
 * APK, so the Compose theme cannot silently drift from it.
 *
 * The official stylesheets never hard-code a colour: every one resolves through
 * an `--ink-*` custom property (see docs/reference/apk-1.0.3/README.md). Those
 * tokens were extracted verbatim from the APK's `app.css` into
 * `docs/reference/apk-1.0.3/theme-tokens-{light,dark}.json`, which makes the
 * palette checkable rather than eyeballed.
 *
 * This is not hypothetical: it caught `inkSelectedBg`, whose dark value had been
 * copy-pasted from `--ink-focus-ring` (.12) instead of the official .18.
 *
 * Like the other tests here it reads source as text — this repo has no
 * instrumentation tests, so there is no Compose runtime to assert against.
 */
class OfficialInkTokenTest {

    private val theme = File("src/main/java/xyz/rflg/hstudiodirect/Theme.kt").readText()

    private fun tokens(name: String): JSONObject {
        val f = File("../../docs/reference/apk-1.0.3/theme-tokens-$name.json")
        assertTrue(
            "Official token reference missing at ${f.absolutePath}; " +
                "it is committed under docs/reference/apk-1.0.3/",
            f.exists(),
        )
        return JSONObject(f.readText())
    }

    /** ARGB packed into a Long the same way `Color(0xAARRGGBB)` is written. */
    private data class Argb(val value: Long) {
        override fun toString() = "0x%08X".format(value)
    }

    private fun parseKotlinColor(expr: String): Argb? {
        Regex("""Color\(0x([0-9A-Fa-f]{8})\)""").find(expr)?.let {
            return Argb(it.groupValues[1].toLong(16))
        }
        Regex("""Color\(0x([0-9A-Fa-f]{6})\)""").find(expr)?.let {
            return Argb(it.groupValues[1].toLong(16) or 0xFF000000L)
        }
        if (Regex("""Color\.White\b""").containsMatchIn(expr)) return Argb(0xFFFFFFFFL)
        if (Regex("""Color\.Black\b""").containsMatchIn(expr)) return Argb(0xFF000000L)
        return null
    }

    /** CSS colour to ARGB, unwrapping `var(--custom, fallback)`. */
    private fun parseCssColor(raw: String): Argb? {
        var v = raw.trim()
        Regex("""^var\([^,]+,\s*(.+)\)$""").find(v)?.let { v = it.groupValues[1].trim() }
        Regex("""^#([0-9a-fA-F]{6})$""").find(v)?.let {
            return Argb(it.groupValues[1].toLong(16) or 0xFF000000L)
        }
        Regex("""^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$""")
            .find(v)?.let { m ->
                val r = m.groupValues[1].toDouble().toLong()
                val g = m.groupValues[2].toDouble().toLong()
                val b = m.groupValues[3].toDouble().toLong()
                val a = if (m.groupValues[4].isEmpty()) 255L
                else Math.round(m.groupValues[4].toDouble() * 255).toLong()
                return Argb((a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        return null
    }

    private data class TokenFn(val fn: String, val token: String, val light: Argb?, val dark: Argb?)

    /**
     * Walks Theme.kt pairing each ink-token doc comment with the `fun` that
     * follows it and the two `if (dark) … else …` branches. Comments inside the
     * body are stripped first so an explanatory note between `=` and `if` does
     * not defeat the match.
     */
    private fun inkFunctions(): List<TokenFn> {
        val docRe = Regex("""/\*\*(?:(?!\*/)[\s\S])*?\*/""")
        val out = mutableListOf<TokenFn>()
        for (doc in docRe.findAll(theme)) {
            val token = Regex("""--ink-[a-z0-9-]+""").find(doc.value)?.value ?: continue
            val rest = theme.substring(doc.range.last + 1)
            val head = Regex("""fun\s+(\w+)\s*\(\s*dark:\s*Boolean\s*\)\s*:\s*Color\s*=""")
                .find(rest) ?: continue
            // Only accept the doc comment that is immediately above the fun.
            if (rest.substring(0, head.range.first).count { it == '\n' } > 2) continue

            val body = rest.substring(head.range.last + 1)
                .lineSequence()
                .takeWhile { !it.startsWith("fun ") && !it.startsWith("/**") }
                .joinToString("\n")
                .replace(Regex("""//[^\n]*"""), "")
                .replace(Regex("""/\*(?:(?!\*/)[\s\S])*?\*/"""), "")

            val branches = Regex("""if\s*\(\s*dark\s*\)\s*([\s\S]*?)\s*else\s*([\s\S]*?)\n""")
                .find(body) ?: continue
            out += TokenFn(
                fn = head.groupValues[1],
                token = token,
                dark = parseKotlinColor(branches.groupValues[1]),
                light = parseKotlinColor(branches.groupValues[2]),
            )
        }
        return out
    }

    @Test
    fun everyInkTokenMatchesTheOfficialApkPalette() {
        val light = tokens("light")
        val dark = tokens("dark")
        val fns = inkFunctions()

        assertTrue("expected to parse many ink tokens, got ${fns.size}", fns.size >= 20)

        val failures = mutableListOf<String>()
        var checked = 0
        for (fn in fns) {
            if (!light.has(fn.token) || !dark.has(fn.token)) continue
            val officialLight = parseCssColor(light.getString(fn.token)) ?: continue
            val officialDark = parseCssColor(dark.getString(fn.token)) ?: continue
            checked++
            if (fn.light != officialLight) {
                failures += "${fn.fn} light: Theme.kt=${fn.light} official=$officialLight " +
                    "(${light.getString(fn.token)})"
            }
            if (fn.dark != officialDark) {
                failures += "${fn.fn} dark: Theme.kt=${fn.dark} official=$officialDark " +
                    "(${dark.getString(fn.token)})"
            }
        }

        assertTrue("only $checked tokens compared; parser likely broke", checked >= 20)
        assertTrue(
            "Compose palette drifted from the official APK:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun selectedBackgroundIsNotConfusedWithTheFocusRing() {
        // --ink-selected-bg dark is .18 while --ink-focus-ring dark is .12. They
        // were once the same value here; pin the distinction explicitly so a
        // future tidy-up cannot merge them again.
        val light = tokens("light")
        val dark = tokens("dark")
        assertEquals(
            "rgba(255, 255, 255, .18)",
            Regex("""var\([^,]+,\s*(.+)\)""").find(dark.getString("--ink-selected-bg"))
                ?.groupValues?.get(1)?.trim(),
        )
        assertEquals(
            "rgba(255, 255, 255, .12)",
            Regex("""var\([^,]+,\s*(.+)\)""").find(dark.getString("--ink-focus-ring"))
                ?.groupValues?.get(1)?.trim(),
        )
        assertTrue(
            "inkSelectedBg must render at .18 in dark",
            theme.contains("Color(0x2EFFFFFF)"),
        )
    }
}
