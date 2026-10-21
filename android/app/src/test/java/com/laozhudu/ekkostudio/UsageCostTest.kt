package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cost and cache maths for the per-conversation usage sheet.
 *
 * The pricing table is user-authored, so both "no rate" and "rate in a
 * different shape" are normal states and must read as unknown rather than as
 * a free call.
 */
class UsageCostTest {
    private val rate = PricingRate("openrouter", "meta/muse-spark-open", input = 3.0, output = 15.0, cacheRead = 0.3)

    private fun usage(
        input: Long = 0,
        output: Long = 0,
        cacheRead: Long = 0,
        cacheWrite: Long = 0,
        model: String = "openrouter/meta/muse-spark-open",
    ) = SessionUsage(input, output, cacheRead, cacheWrite, 0, model, "default")

    @Test
    fun `an exact model match wins`() {
        assertEquals(rate, matchRate("openrouter/meta/muse-spark-open", listOf(rate)))
    }

    @Test
    fun `a rate stored with the bare model name still matches the qualified id`() {
        val bare = rate.copy(model = "meta/muse-spark-open")
        assertEquals(bare, matchRate("openrouter/meta/muse-spark-open", listOf(bare)))
    }

    @Test
    fun `a different provider does not match`() {
        assertNull(matchRate("anthropic/meta/muse-spark-open", listOf(rate)))
    }

    @Test
    fun `cost adds input output and cache read at their own rates`() {
        val cost = estimateCost(usage(input = 1_000_000, output = 1_000_000, cacheRead = 1_000_000), listOf(rate))!!

        // 3 + 15 + 0.3
        assertEquals(18.3, cost, 0.0001)
    }

    @Test
    fun `cache read falls back to the input rate when no cache rate is set`() {
        val noCache = rate.copy(cacheRead = null)
        val cost = estimateCost(usage(cacheRead = 1_000_000), listOf(noCache))!!

        assertEquals(3.0, cost, 0.0001)
    }

    @Test
    fun `an unpriced model is unknown, not zero`() {
        assertNull(estimateCost(usage(input = 5000, model = "nobody/unknown"), listOf(rate)))
    }

    @Test
    fun `an empty model is unknown`() {
        assertNull(estimateCost(usage(input = 5000, model = ""), listOf(rate)))
    }

    @Test
    fun `cache hit rate counts cache reads against the whole prompt`() {
        val hit = usage(input = 250_000, cacheRead = 750_000).cacheHitRate!!

        assertEquals(0.75, hit, 0.0001)
    }

    @Test
    fun `cache hit rate is null when no prompt was sent`() {
        assertNull(usage().cacheHitRate)
    }

    @Test
    fun `attachment caps match the official client`() {
        assertEquals(9, MAX_ATTACHMENTS)
        assertEquals(20L * 1024 * 1024, MAX_ATTACHMENT_BYTES)
        assertTrue("20 MB must be allowed exactly, only larger files are refused", 20L * 1024 * 1024 <= MAX_ATTACHMENT_BYTES)
    }
}
