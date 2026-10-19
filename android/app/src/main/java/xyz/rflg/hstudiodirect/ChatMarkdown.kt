package xyz.rflg.hstudiodirect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal sealed interface ChatMarkdownBlock {
    val text: String

    data class Paragraph(override val text: String) : ChatMarkdownBlock
    data class Heading(val level: Int, override val text: String) : ChatMarkdownBlock
    data class Unordered(val indent: Int, override val text: String) : ChatMarkdownBlock
    data class Ordered(val indent: Int, val marker: String, override val text: String) : ChatMarkdownBlock
    data class Task(val indent: Int, val checked: Boolean, override val text: String) : ChatMarkdownBlock
    data class Quote(override val text: String) : ChatMarkdownBlock
    data class Code(override val text: String, val language: String = "") : ChatMarkdownBlock

    /** GFM pipe table: | a | b | with a |---|---| delimiter row. */
    data class Table(
        val header: List<String>,
        val aligns: List<TextAlign>,
        val rows: List<List<String>>,
    ) : ChatMarkdownBlock {
        override val text: String get() = ""
    }

    /** --- / *** / ___ */
    data object Rule : ChatMarkdownBlock {
        override val text: String get() = ""
    }
}

private val TABLE_DELIMITER = Regex("^:?-{1,}:?$")
private val BARE_URL = Regex("""https?://[^\s<>()\[\]{}"'`]+""")
private val FENCE_LANGUAGES = mapOf(
    "kt" to "kotlin", "kts" to "kotlin", "js" to "javascript", "ts" to "typescript",
    "py" to "python", "sh" to "shell", "bash" to "shell", "zsh" to "shell",
    "yml" to "yaml", "md" to "markdown", "rb" to "ruby", "rs" to "rust",
    "cs" to "csharp", "c++" to "cpp", "ps1" to "powershell", "text" to "", "plain" to "",
)

/** Splits a table row on unescaped pipes; keeps `\|` as a literal pipe. */
internal fun splitTableRow(line: String): List<String> {
    var body = line.trim()
    if (body.startsWith("|")) body = body.drop(1)
    if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < body.length) {
        val char = body[index]
        when {
            char == '\\' && body.getOrNull(index + 1) == '|' -> {
                cell.append('|')
                index += 2
            }
            char == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
                index++
            }
            else -> {
                cell.append(char)
                index++
            }
        }
    }
    cells += cell.toString().trim()
    return cells
}

internal fun isTableDelimiterRow(line: String): Boolean {
    if (!line.contains('-')) return false
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { it.isNotEmpty() && TABLE_DELIMITER.matches(it) }
}

private fun tableAligns(delimiter: String): List<TextAlign> = splitTableRow(delimiter).map { cell ->
    val leading = cell.startsWith(":")
    val trailing = cell.endsWith(":")
    when {
        leading && trailing -> TextAlign.Center
        trailing -> TextAlign.Right
        else -> TextAlign.Left
    }
}

internal fun parseChatMarkdown(source: String): List<ChatMarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<ChatMarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var fence = ""
    var fenceLanguage = ""
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += ChatMarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }
    fun flushCode() {
        if (code.isNotEmpty() || fenceLanguage.isNotEmpty()) {
            blocks += ChatMarkdownBlock.Code(code.joinToString("\n"), fenceLanguage)
            code.clear()
            fenceLanguage = ""
        }
    }

    while (index < lines.size) {
        val raw = lines[index]
        val trimmed = raw.trim()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            flushParagraph()
            if (fence.isNotEmpty()) {
                flushCode()
                fence = ""
            } else {
                fence = trimmed.take(3)
                val tag = trimmed.drop(3).trim().lowercase().substringBefore(' ')
                fenceLanguage = FENCE_LANGUAGES[tag] ?: tag.take(14)
            }
            index++
            continue
        }
        if (fence.isNotEmpty()) {
            code += raw
            index++
            continue
        }
        if (trimmed.isEmpty()) {
            flushParagraph()
            index++
            continue
        }

        // --- / *** / ___ horizontal rule
        if (trimmed.length >= 3 && trimmed.all { it == '-' || it == '*' || it == '_' } &&
            trimmed.toSet().size == 1
        ) {
            flushParagraph()
            blocks += ChatMarkdownBlock.Rule
            index++
            continue
        }

        // GFM table: a pipe row followed by a delimiter row.
        if (trimmed.contains('|') && index + 1 < lines.size && isTableDelimiterRow(lines[index + 1])) {
            flushParagraph()
            val header = splitTableRow(trimmed)
            val aligns = tableAligns(lines[index + 1]).let { list ->
                List(header.size) { list.getOrElse(it) { TextAlign.Left } }
            }
            val rows = mutableListOf<List<String>>()
            var cursor = index + 2
            while (cursor < lines.size) {
                val candidate = lines[cursor].trim()
                if (candidate.isEmpty() || !candidate.contains('|')) break
                if (isTableDelimiterRow(candidate)) break
                val cells = splitTableRow(candidate)
                rows += List(header.size) { cells.getOrElse(it) { "" } }
                cursor++
            }
            blocks += ChatMarkdownBlock.Table(header, aligns, rows)
            index = cursor
            continue
        }

        val heading = parseHeading(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks += heading
            index++
            continue
        }
        val task = parseTask(raw)
        if (task != null) {
            flushParagraph()
            blocks += task
            index++
            continue
        }
        val unordered = parseUnordered(raw)
        if (unordered != null) {
            flushParagraph()
            blocks += unordered
            index++
            continue
        }
        val ordered = parseOrdered(raw)
        if (ordered != null) {
            flushParagraph()
            blocks += ordered
            index++
            continue
        }

        if (trimmed.startsWith("> ") || trimmed == ">") {
            flushParagraph()
            blocks += ChatMarkdownBlock.Quote(trimmed.removePrefix(">").trimStart())
            index++
            continue
        }

        paragraph += trimmed
        index++
    }
    flushParagraph()
    flushCode()
    return blocks
}

private fun parseHeading(line: String): ChatMarkdownBlock.Heading? {
    val level = line.takeWhile { it == '#' }.length
    if (level !in 1..6 || line.getOrNull(level) != ' ') return null
    return ChatMarkdownBlock.Heading(level, line.drop(level + 1).trim())
}

private fun parseTask(line: String): ChatMarkdownBlock.Task? {
    val prefix = line.takeWhile { it == ' ' || it == '\t' }
    val value = line.drop(prefix.length)
    if (value.length < 5) return null
    if (value[0] !in charArrayOf('-', '*', '+') || value[1] != ' ') return null
    if (value.getOrNull(2) != '[') return null
    val check = value.getOrNull(3) ?: return null
    if (value.getOrNull(4) != ']') return null
    if (check != ' ' && check != 'x' && check != 'X') return null
    val rest = value.drop(5).trimStart()
    val spaces = prefix.fold(0) { total, char -> total + if (char == '\t') 2 else 1 }
    return ChatMarkdownBlock.Task(spaces / 2, check != ' ', rest)
}

private fun parseUnordered(line: String): ChatMarkdownBlock.Unordered? {
    val prefix = line.takeWhile { it == ' ' || it == '\t' }
    val value = line.drop(prefix.length)
    if (value.length < 2 || value[0] !in charArrayOf('-', '*', '+', '•', '◦') || value[1] != ' ') return null
    val spaces = prefix.fold(0) { total, char -> total + if (char == '\t') 2 else 1 }
    return ChatMarkdownBlock.Unordered(spaces / 2, value.drop(2))
}

private fun parseOrdered(line: String): ChatMarkdownBlock.Ordered? {
    val prefix = line.takeWhile { it == ' ' || it == '\t' }
    val value = line.drop(prefix.length)
    val digits = value.takeWhile(Char::isDigit)
    val punctuation = value.getOrNull(digits.length)
    if (digits.isEmpty() || punctuation == null || punctuation !in charArrayOf('.', ')')) return null
    if (value.getOrNull(digits.length + 1) != ' ') return null
    val spaces = prefix.fold(0) { total, char -> total + if (char == '\t') 2 else 1 }
    return ChatMarkdownBlock.Ordered(spaces / 2, "$digits$punctuation", value.drop(digits.length + 2))
}

internal fun chatMarkdownInline(
    source: String,
    linkColor: Color = Color.Unspecified,
    codeBackground: Color = Color.Transparent,
    codeColor: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    appendMarkdown(source, 0, source.length, linkColor, codeBackground, codeColor)
}

/** Trailing punctuation that belongs to the sentence, not to the URL. */
private fun trimUrl(raw: String): String = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

private fun AnnotatedString.Builder.appendLink(url: String, linkColor: Color, label: String? = null) {
    pushLink(LinkAnnotation.Url(url))
    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    append(label ?: url)
    pop()
    pop()
}

private fun AnnotatedString.Builder.appendMarkdown(
    source: String,
    start: Int,
    end: Int,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color = Color.Unspecified,
) {
    var index = start
    while (index < end) {
        val char = source[index]
        when {
            char == '\\' && index + 1 < end -> {
                append(source[index + 1])
                index += 2
            }
            source.startsWith("**", index) || source.startsWith("__", index) -> {
                val delimiter = source.substring(index, index + 2)
                val close = source.indexOf(delimiter, index + 2).takeIf { it in (index + 2)..<end }
                if (close != null) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendMarkdown(source, index + 2, close, linkColor, codeBackground, codeColor)
                    pop()
                    index = close + 2
                } else {
                    append(delimiter)
                    index += 2
                }
            }
            source.startsWith("~~", index) -> {
                val close = source.indexOf("~~", index + 2).takeIf { it in (index + 2)..<end }
                if (close != null) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendMarkdown(source, index + 2, close, linkColor, codeBackground, codeColor)
                    pop()
                    index = close + 2
                } else {
                    append("~~")
                    index += 2
                }
            }
            char == '`' -> {
                val close = source.indexOf('`', index + 1).takeIf { it in (index + 1)..<end }
                if (close != null) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor))
                    append(source.substring(index + 1, close))
                    pop()
                    index = close + 1
                } else {
                    append('`')
                    index++
                }
            }
            char == '[' -> {
                val labelEnd = source.indexOf(']', index + 1)
                val urlStart = labelEnd + 1
                val urlEnd = if (labelEnd in (index + 1)..<end && source.getOrNull(urlStart) == '(') {
                    source.indexOf(')', urlStart + 1)
                } else -1
                if (urlEnd in (urlStart + 1)..<end) {
                    val url = source.substring(urlStart + 1, urlEnd)
                    pushLink(LinkAnnotation.Url(url))
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    appendMarkdown(source, index + 1, labelEnd, linkColor, codeBackground, codeColor)
                    pop()
                    pop()
                    index = urlEnd + 1
                } else {
                    append('[')
                    index++
                }
            }
            source.startsWith("http://", index) || source.startsWith("https://", index) -> {
                val match = BARE_URL.find(source, index)?.takeIf { it.range.first == index }
                if (match != null) {
                    val url = trimUrl(match.value)
                    appendLink(url, linkColor)
                    index += url.length
                } else {
                    append(char)
                    index++
                }
            }
            char == '*' || char == '_' -> {
                val delimiter = char
                val close = source.indexOf(delimiter, index + 1).takeIf { it in (index + 1)..<end }
                if (close != null) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendMarkdown(source, index + 1, close, linkColor, codeBackground, codeColor)
                    pop()
                    index = close + 1
                } else {
                    append(delimiter)
                    index++
                }
            }
            else -> {
                append(char)
                index++
            }
        }
    }
}

internal fun chatTextDirection(text: String): TextDirection {
    text.forEach { char ->
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return TextDirection.Rtl
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return TextDirection.Ltr
        }
    }
    return TextDirection.Content
}

@Composable
internal fun ChatMarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        parseChatMarkdown(text).forEach { block ->
            when (block) {
                is ChatMarkdownBlock.Heading -> MarkdownLine(
                    block.text,
                    modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 6.dp, bottom = 1.dp),
                    fontWeight = FontWeight.Bold,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp)
                        4 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )
                is ChatMarkdownBlock.Unordered ->
                    MarkdownListRow("◦", block.text, block.indent, subtleMarker = true)
                is ChatMarkdownBlock.Ordered ->
                    MarkdownListRow(block.marker, block.text, block.indent, subtleMarker = false)
                is ChatMarkdownBlock.Task ->
                    MarkdownTaskRow(block)
                is ChatMarkdownBlock.Quote -> MarkdownQuoteRow(block.text)
                is ChatMarkdownBlock.Code -> MarkdownCodeBlock(block)
                is ChatMarkdownBlock.Table -> MarkdownTable(block)
                ChatMarkdownBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 5.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                is ChatMarkdownBlock.Paragraph -> MarkdownLine(block.text)
            }
        }
    }
}

@Composable
private fun MarkdownLine(
    text: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    monospace: Boolean = false,
    softWrap: Boolean = true,
) {
    val direction = chatTextDirection(text)
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Text(
        text = chatMarkdownInline(
            text,
            linkColor = MaterialTheme.colorScheme.primary,
            // HStudio .markdown-body code (official App): --ink-bg-code fill,
            // --ink-text-primary text, 4px radius, 13px mono, 2px/6px padding.
            codeBackground = inkCodeBg(inkDark),
            codeColor = inkTextPrimary(inkDark),
        ),
        modifier = modifier.fillMaxWidth(),
        style = style.copy(
            textDirection = direction,
            fontFamily = if (monospace) FontFamily.Monospace else style.fontFamily,
        ),
        fontWeight = fontWeight,
        softWrap = softWrap,
        overflow = if (softWrap) TextOverflow.Clip else TextOverflow.Visible,
        textAlign = if (direction == TextDirection.Rtl) TextAlign.Right else TextAlign.Left,
    )
}

/**
 * HStudio blockquote: `padding: 4px 12px`, `border-inline-start: 3px solid
 * $border-color`, `color: $text-secondary`. A start-anchored hairline bar,
 * not a filled chip. The Row lays children out in reading order, so under
 * RTL the bar mirrors to the trailing edge on its own.
 */
@Composable
private fun MarkdownQuoteRow(text: String) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val direction = chatTextDirection(text)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(inkBorder(inkDark)),
        )
        Text(
            text = chatMarkdownInline(
                text,
                linkColor = MaterialTheme.colorScheme.primary,
                codeBackground = inkCodeBg(inkDark),
                codeColor = inkTextPrimary(inkDark),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                textDirection = direction,
                color = inkTextSecondary(inkDark),
            ),
            textAlign = if (direction == TextDirection.Rtl) TextAlign.Right else TextAlign.Left,
        )
    }
}

/**
 * Fenced code keeps its own line breaks and scrolls sideways instead of
 * wrapping. HStudio .markdown-native-code-block (official App): --ink-bg-code
 * fill, 1px --ink-border hairline, 6px radius; when a language is known a
 * 36px header strip on --ink-pressed shows the uppercase 10px muted label and
 * a 48×30 copy affordance, split from the body by --ink-border.
 */
@Composable
private fun MarkdownCodeBlock(block: ChatMarkdownBlock.Code) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    // Highlighting is cached per (text, theme) so scrolling and unrelated
    // recompositions never re-run the tokenizer over a long block.
    val highlighted = remember(block.text, inkDark) { highlightCode(block.text, inkDark) }
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, inkBorder(inkDark), RoundedCornerShape(6.dp))
            .background(inkCodeBg(inkDark)),
    ) {
        if (block.language.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(inkPressed(inkDark))
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.language.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        textDirection = TextDirection.Ltr,
                    ),
                    color = inkTextMuted(inkDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { clipboard.setText(AnnotatedString(block.text)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.message_copy),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = inkTextSecondary(inkDark),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkBorder(inkDark)))
        }
        val scroll = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Text(
                text = highlighted,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                softWrap = false,
                color = inkCodeText(inkDark),
            )
        }
    }
}

/** Above this size the block falls back to plain text so streaming stays smooth. */
private const val CODE_HIGHLIGHT_LIMIT = 20_000

// HStudio --ink-code-* palette (app.css): keyword purple, string teal,
// number amber, comment slate — identical roles to our token groups.
private val CodeCommentLight = Color(0xFF6B7280)
private val CodeCommentDark = Color(0xFF94A3B8)
private val CodeKeywordLight = Color(0xFF7C3AED)
private val CodeKeywordDark = Color(0xFFC084FC)
private val CodeStringLight = Color(0xFF0F766E)
private val CodeStringDark = Color(0xFF5EEAD4)
private val CodeNumberLight = Color(0xFFB45309)
private val CodeNumberDark = Color(0xFFFBBF24)

/**
 * A single combined scan (one regex pass, named groups) instead of one sweep
 * per token class — v1.9.0's stacked per-scan highlighter made long code
 * blocks janky while a reply streamed.
 */
private val CodeToken = Regex(
    """(?<comment>(?<!:)//[^\n]*|/\*.*?\*/|(?<![^\s])#[^\n]*)""" +
        """|(?<string>"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*')""" +
        """|(?<keyword>\b(?:fun|val|var|if|else|when|while|for|return|import|class|object|interface|extends|implements|new|try|catch|finally|throw|def|async|await|public|private|protected|static|final|struct|enum|package|func|switch|case|break|continue|in|is|as|and|or|not|let|const|fn|mut|match|type|export|default|from|require|this|super|true|false|None|True|False|undefined|self|lambda|yield|go|defer|range|elif|except|raise|with|pass|assert)\b)""" +
        """|(?<number>\b0[xX][0-9a-fA-F]+\b|\b\d[\d_]*(?:\.\d[\d_]*)?\b)""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

private fun highlightCode(text: String, dark: Boolean): AnnotatedString {
    if (text.length > CODE_HIGHLIGHT_LIMIT) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (match in CodeToken.findAll(text)) {
            val style = when {
                match.groups["comment"] != null -> SpanStyle(color = if (dark) CodeCommentDark else CodeCommentLight, fontStyle = FontStyle.Italic)
                match.groups["string"] != null -> SpanStyle(color = if (dark) CodeStringDark else CodeStringLight)
                match.groups["keyword"] != null -> SpanStyle(
                    color = if (dark) CodeKeywordDark else CodeKeywordLight,
                    fontWeight = FontWeight.SemiBold,
                )
                else -> SpanStyle(color = if (dark) CodeNumberDark else CodeNumberLight)
            }
            addStyle(style, match.range.first, match.range.last + 1)
        }
    }
}

/**
 * GFM table. Columns are weighted by their widest cell so rows stay aligned on a
 * phone-width transcript without needing a horizontal scroller.
 */
@Composable
private fun MarkdownTable(block: ChatMarkdownBlock.Table) {
    val dark = isSystemInDarkTheme()
    val selectedBg = inkSelectedBg(dark)
    val columns = block.header.size
    if (columns == 0) return
    val sample = block.rows + listOf(block.header)
    val weights = List(columns) { column -> 
        val widest = sample.maxOfOrNull { it.getOrElse(column) { "" } }?.length ?: 1
        widest.coerceIn(3, 42).toFloat()
    }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, selectedBg, RoundedCornerShape(8.dp)),
    ) {
        TableRow(
            cells = block.header,
            aligns = block.aligns,
            weights = weights,
            header = true,
            divider = false,
            outline = outline,
        )
        block.rows.forEachIndexed { rowIndex, row ->
            TableRow(
                cells = row,
                aligns = block.aligns,
                weights = weights,
                header = false,
                divider = rowIndex < block.rows.lastIndex,
                outline = outline,
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<TextAlign>,
    weights: List<Float>,
    header: Boolean,
    divider: Boolean,
    outline: Color,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (header) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                ),
        ) {
            cells.forEachIndexed { column, cell ->
                TableCell(
                    cell = cell,
                    align = aligns.getOrElse(column) { TextAlign.Left },
                    weight = weights.getOrElse(column) { 1f },
                    header = header,
                )
            }
        }
        if (divider) {
            HorizontalDivider(color = outline)
        }
    }
}

@Composable
private fun RowScope.TableCell(cell: String, align: TextAlign, weight: Float, header: Boolean) {
    val style = if (header) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.bodySmall
    }
    Text(
        text = chatMarkdownInline(
            cell,
            linkColor = MaterialTheme.colorScheme.primary,
            codeBackground = inkCodeBg(MaterialTheme.colorScheme.isInkDark()),
            codeColor = inkTextPrimary(MaterialTheme.colorScheme.isInkDark()),
        ),
        modifier = Modifier.weight(weight).padding(horizontal = 8.dp, vertical = 7.dp),
        style = style.copy(textDirection = chatTextDirection(cell)),
        textAlign = align,
        color = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MarkdownTaskRow(block: ChatMarkdownBlock.Task) {
    val direction = chatTextDirection(block.text)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (direction == TextDirection.Rtl) 0.dp else (block.indent * 16).dp,
                    end = if (direction == TextDirection.Rtl) (block.indent * 16).dp else 0.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (block.checked) "☑" else "☐",
                style = MaterialTheme.typography.bodyLarge,
                color = if (block.checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MarkdownLine(
                block.text,
                modifier = Modifier.weight(1f),
                fontWeight = if (block.checked) FontWeight.Normal else null,
            )
        }
    }
}

@Composable
private fun MarkdownListRow(marker: String, text: String, indent: Int, subtleMarker: Boolean) {
    val direction = chatTextDirection(text)
    // A local physical-LTR row keeps the marker on the real right for Arabic;
    // the text itself still uses the correct Unicode paragraph direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (direction == TextDirection.Rtl) 0.dp else (indent * 16).dp,
                    end = if (direction == TextDirection.Rtl) (indent * 16).dp else 0.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (direction == TextDirection.Rtl) {
                MarkdownLine(text, modifier = Modifier.weight(1f))
                MarkdownMarker(marker, subtleMarker)
            } else {
                MarkdownMarker(marker, subtleMarker)
                MarkdownLine(text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkdownMarker(marker: String, subtle: Boolean) {
    Text(
        marker,
        modifier = Modifier.width(14.dp),
        color = if (subtle) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        style = if (subtle) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
