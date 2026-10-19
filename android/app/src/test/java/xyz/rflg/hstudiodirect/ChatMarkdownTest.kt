package xyz.rflg.hstudiodirect

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {
    @Test
    fun `studio headings and lists become native blocks`() {
        val blocks = parseChatMarkdown(
            """
            ### البريد غير المقروء
            - **635** عاجلة وتتطلب إجراء.
              - طلبات معلومات.

            1. **تنظيف البريد**
            """.trimIndent(),
        )

        assertEquals(ChatMarkdownBlock.Heading(3, "البريد غير المقروء"), blocks[0])
        assertEquals(ChatMarkdownBlock.Unordered(0, "**635** عاجلة وتتطلب إجراء."), blocks[1])
        assertEquals(ChatMarkdownBlock.Unordered(1, "طلبات معلومات."), blocks[2])
        assertEquals(ChatMarkdownBlock.Ordered(0, "1.", "**تنظيف البريد**"), blocks[3])
    }

    @Test
    fun `inline bold hides markdown markers and keeps bold span`() {
        val rendered = chatMarkdownInline("تنبيه **مهم** الآن")

        assertEquals("تنبيه مهم الآن", rendered.text)
        assertFalse(rendered.text.contains("**"))
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `gfm tables become a table block with per column alignment`() {
        val blocks = parseChatMarkdown(
            """
            | 项目 | 状态 | 耗时 |
            |:-----|:----:|-----:|
            | 构建 | 通过 | 12s |
            | 测试 | 进行中 | - |
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        val table = blocks[0] as ChatMarkdownBlock.Table
        assertEquals(listOf("项目", "状态", "耗时"), table.header)
        assertEquals(listOf(TextAlign.Left, TextAlign.Center, TextAlign.Right), table.aligns)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("构建", "通过", "12s"), table.rows[0])
        // A short row is padded so every row keeps the header's column count.
        assertEquals(3, table.rows[1].size)
    }

    @Test
    fun `a table without a delimiter row stays a paragraph`() {
        val blocks = parseChatMarkdown("a | b\nc | d")
        assertTrue(blocks.all { it is ChatMarkdownBlock.Paragraph })
    }

    @Test
    fun `escaped pipes stay inside one cell`() {
        assertEquals(listOf("a | b", "c"), splitTableRow("""| a \| b | c |"""))
    }

    @Test
    fun `task lists rules and strikethrough are recognised`() {
        val blocks = parseChatMarkdown("- [x] 已完成\n- [ ] 待办\n\n---\n")

        assertEquals(ChatMarkdownBlock.Task(0, true, "已完成"), blocks[0])
        assertEquals(ChatMarkdownBlock.Task(0, false, "待办"), blocks[1])
        assertEquals(ChatMarkdownBlock.Rule, blocks[2])

        val struck = chatMarkdownInline("旧的 ~~待办~~ 新的")
        assertEquals("旧的 待办 新的", struck.text)
        assertTrue(struck.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
    }

    @Test
    fun `fenced code keeps its language tag and inner blank lines`() {
        val blocks = parseChatMarkdown("```kotlin\nval a = 1\n\nval b = 2\n```")
        val code = blocks.single() as ChatMarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val a = 1\n\nval b = 2", code.text)
    }

    @Test
    fun `markdown links and bare urls both become link annotations`() {
        val rendered = chatMarkdownInline("看 https://hermes.rflg.xyz 和 [文档](https://example.com/a)")
        val links = rendered.getLinkAnnotations(0, rendered.length)

        assertEquals(2, links.size)
        assertFalse(rendered.text.contains("]("))
        assertTrue(rendered.text.contains("文档"))
    }
}
