package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The scraper that lets source-reading tests survive refactors.
 *
 * Every case here is a shape that broke a test before this existed, or would
 * break a naive `substringAfter("fun name(...)")`.
 */
class KotlinSourceTest {

    @Test
    fun `finds a function by name no matter the modifiers`() {
        val source = """
            @Composable
            private fun target(a: Int) { println(a) }

            internal suspend fun other(): Int = 1
        """.trimIndent()

        assertTrue(KotlinSource.hasFun(source, "target"))
        assertTrue(KotlinSource.hasFun(source, "other"))
        assertFalse(KotlinSource.hasFun(source, "missing"))
    }

    @Test
    fun `adding a parameter does not break the lookup`() {
        val before = "fun openSession(session: SessionSummary) { doWork() }"
        val after = "fun openSession(session: SessionSummary, focus: String? = null) { doWork() }"

        // The body text still contains the signature, so compare what the tests
        // actually care about: the body was found, and it is complete.
        assertTrue(KotlinSource.funBody(before, "openSession")!!.contains("doWork()"))
        assertTrue(KotlinSource.funBody(after, "openSession")!!.contains("doWork()"))
        assertEquals(
            KotlinSource.funBody(before, "openSession")!!.count { it == '{' },
            KotlinSource.funBody(after, "openSession")!!.count { it == '{' },
        )
    }

    @Test
    fun `a generic type parameter list before the name is handled`() {
        val source = """
            private fun <T> launchWork(
                work: suspend () -> T,
                onSuccess: (T) -> Unit,
            ) {
                doWork()
            }
        """.trimIndent()

        assertTrue(KotlinSource.hasFun(source, "launchWork"))
        assertTrue(KotlinSource.funBody(source, "launchWork")!!.contains("doWork()"))
    }

    @Test
    fun `a block body ends at its matching brace, nested braces included`() {
        val source = """
            fun outer() {
                if (true) {
                    listOf(1).forEach { println(it) }
                }
                done()
            }
            fun next() { }
        """.trimIndent()

        val body = KotlinSource.funBody(source, "outer")!!

        assertTrue(body.contains("done()"))
        assertFalse("must not run into the next function", body.contains("fun next"))
    }

    @Test
    fun `an expression body stops at the end of its statement`() {
        val source = """
            fun launchWork(x: Int) = viewModelScope.launch {
                x
            }
            fun after() { }
        """.trimIndent()

        val body = KotlinSource.funBody(source, "launchWork")!!

        assertTrue(body.contains("viewModelScope.launch"))
        assertFalse(body.contains("fun after"))
    }

    @Test
    fun `braces inside strings and comments do not shift the boundary`() {
        val source = """
            fun shouty() {
                val note = "a { b } c"
                // a comment with } in it
                val other = "}"
                finish()
            }
            fun next() { }
        """.trimIndent()

        val body = KotlinSource.funBody(source, "shouty")!!

        assertTrue(body.contains("finish()"))
        assertFalse(body.contains("fun next"))
    }

    @Test
    fun `triple quoted strings are skipped too`() {
        val source = """
            fun doc() {
                val text = ""${'"'}
                    fun fake() { }
                ""${'"'}
                real()
            }
            fun next() { }
        """.trimIndent()

        val body = KotlinSource.funBody(source, "doc")!!

        assertTrue(body.contains("real()"))
        assertFalse(body.contains("fun next"))
    }

    @Test
    fun `the signature keeps the parameters and drops the body`() {
        val source = "fun open(a: Int, b: String = \"x\") { body() }"

        val signature = KotlinSource.funSignature(source, "open")!!

        assertTrue(signature.contains("a: Int"))
        assertTrue(signature.contains("b: String"))
        assertFalse(signature.contains("body()"))
    }

    @Test
    fun `an unknown name returns null rather than throwing`() {
        assertNull(KotlinSource.funBody("fun a() { }", "b"))
    }

    @Test
    fun `a function whose name is a prefix of another is not confused`() {
        val source = """
            fun send() { short() }
            fun sendNow() { long() }
        """.trimIndent()

        val body = KotlinSource.funBody(source, "send")!!

        assertTrue(body.contains("short()"))
        assertFalse("must not match sendNow", body.contains("long()"))
    }

    @Test
    fun `the real view model still has the functions the suite asserts on`() {
        val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

        listOf("openSession", "send", "onScreen", "pollWriteGate", "openProfiles", "launchWork").forEach { name ->
            assertTrue("$name disappeared from AppViewModel", KotlinSource.hasFun(viewModel, name))
        }
    }
}
