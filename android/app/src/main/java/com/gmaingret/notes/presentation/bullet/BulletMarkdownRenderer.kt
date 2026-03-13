package com.gmaingret.notes.presentation.bullet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// -----------------------------------------------------------------------
// Content segment model
// -----------------------------------------------------------------------

/**
 * Sealed hierarchy for rendering bullet content:
 * - [TextSegment]: plain text (may contain markdown formatting)
 * - [ChipSegment]: inline chip (#tag, @mention, !!date)
 */
sealed interface ContentSegment {
    data class TextSegment(val text: String) : ContentSegment
    data class ChipSegment(val type: ChipType, val text: String) : ContentSegment
}

enum class ChipType { TAG, MENTION, DATE }

// -----------------------------------------------------------------------
// Markdown AnnotatedString builder
// -----------------------------------------------------------------------

/**
 * Builds an [AnnotatedString] from markdown-flavored text.
 *
 * Supported syntax:
 * - `**bold**` → [FontWeight.Bold]
 * - `*italic*` or `_italic_` → [FontStyle.Italic]
 * - `~~strikethrough~~` → [TextDecoration.LineThrough]
 * - `[label](url)` → blue underlined text + [LinkAnnotation.Url]
 *
 * Markers are stripped from the output text. Processing order:
 * links → bold → strikethrough → italic (ensures ** is not confused with *).
 *
 * @param text raw content string
 * @return styled [AnnotatedString] with spans applied
 */
@OptIn(ExperimentalTextApi::class)
fun buildMarkdownAnnotatedString(text: String): AnnotatedString {
    // Represent each source-text character as either visible or part of a marker.
    // We build a list of (displayText, spanType?) tuples.

    data class Span(val start: Int, val end: Int, val style: SpanStyle, val url: String? = null)

    // Collect all markdown matches with their source positions
    val matches = mutableListOf<Triple<IntRange, String, SpanStyle?>>() // range, displayText, style

    // We'll process by building an output string from segments and tracking span positions.
    // Strategy: find all non-overlapping patterns, sort by start, emit text between them.

    data class MarkdownMatch(
        val sourceStart: Int,
        val sourceEnd: Int,   // exclusive
        val innerText: String,
        val spanStyle: SpanStyle?,
        val url: String? = null
    )

    val allMatches = mutableListOf<MarkdownMatch>()

    // 1. Links: [label](url)
    val linkRegex = Regex("""\[(.+?)\]\((.+?)\)""")
    linkRegex.findAll(text).forEach { match ->
        allMatches.add(
            MarkdownMatch(
                sourceStart = match.range.first,
                sourceEnd = match.range.last + 1,
                innerText = match.groupValues[1],
                spanStyle = SpanStyle(
                    color = Color.Blue,
                    textDecoration = TextDecoration.Underline
                ),
                url = match.groupValues[2]
            )
        )
    }

    // 2. Bold: **text**
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    boldRegex.findAll(text).forEach { match ->
        // Only add if not overlapping with existing matches
        val range = match.range.first until (match.range.last + 1)
        if (allMatches.none { it.sourceStart < range.last && it.sourceEnd > range.first }) {
            allMatches.add(
                MarkdownMatch(
                    sourceStart = match.range.first,
                    sourceEnd = match.range.last + 1,
                    innerText = match.groupValues[1],
                    spanStyle = SpanStyle(fontWeight = FontWeight.Bold)
                )
            )
        }
    }

    // 3. Strikethrough: ~~text~~
    val strikeRegex = Regex("""~~(.+?)~~""")
    strikeRegex.findAll(text).forEach { match ->
        val range = match.range.first until (match.range.last + 1)
        if (allMatches.none { it.sourceStart < range.last && it.sourceEnd > range.first }) {
            allMatches.add(
                MarkdownMatch(
                    sourceStart = match.range.first,
                    sourceEnd = match.range.last + 1,
                    innerText = match.groupValues[1],
                    spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
                )
            )
        }
    }

    // 4. Italic: *text* (single star, not preceded/followed by another *)
    //    Use negative lookbehind/lookahead to avoid matching **bold** markers.
    //    Pattern: (?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)
    val italicStarRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
    italicStarRegex.findAll(text).forEach { match ->
        val range = match.range.first until (match.range.last + 1)
        if (allMatches.none { it.sourceStart < range.last && it.sourceEnd > range.first }) {
            allMatches.add(
                MarkdownMatch(
                    sourceStart = match.range.first,
                    sourceEnd = match.range.last + 1,
                    innerText = match.groupValues[1],
                    spanStyle = SpanStyle(fontStyle = FontStyle.Italic)
                )
            )
        }
    }

    // 5. Italic: _text_
    val italicUnderscoreRegex = Regex("""_(.+?)_""")
    italicUnderscoreRegex.findAll(text).forEach { match ->
        val range = match.range.first until (match.range.last + 1)
        if (allMatches.none { it.sourceStart < range.last && it.sourceEnd > range.first }) {
            allMatches.add(
                MarkdownMatch(
                    sourceStart = match.range.first,
                    sourceEnd = match.range.last + 1,
                    innerText = match.groupValues[1],
                    spanStyle = SpanStyle(fontStyle = FontStyle.Italic)
                )
            )
        }
    }

    // Sort all matches by source start position
    allMatches.sortBy { it.sourceStart }

    return buildAnnotatedString {
        var cursor = 0
        for (match in allMatches) {
            // Append text before this match
            if (cursor < match.sourceStart) {
                append(text.substring(cursor, match.sourceStart))
            }
            // Append inner text with span
            val spanStart = length
            append(match.innerText)
            val spanEnd = length
            if (match.spanStyle != null) {
                addStyle(match.spanStyle, spanStart, spanEnd)
            }
            if (match.url != null) {
                addLink(LinkAnnotation.Url(match.url), start = spanStart, end = spanEnd)
            }
            cursor = match.sourceEnd
        }
        // Append remaining text after last match
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

// -----------------------------------------------------------------------
// Content segment parser
// -----------------------------------------------------------------------

/**
 * Parses bullet content text into an ordered list of [ContentSegment]s.
 *
 * Chip patterns detected:
 * - `#word` → [ChipType.TAG]
 * - `@word` → [ChipType.MENTION]
 * - `!!word-or-date` → [ChipType.DATE]
 *
 * Non-chip text between chips is preserved as [ContentSegment.TextSegment].
 * Empty strings are NOT added as TextSegments.
 *
 * @param text raw bullet content
 * @return ordered list of segments preserving original text order
 */
fun parseContentSegments(text: String): List<ContentSegment> {
    if (text.isEmpty()) return emptyList()

    val chipRegex = Regex("""(#\w+)|(@\w+)|(!!\[[\w-]+\])|(!![\w-]+)""")
    val segments = mutableListOf<ContentSegment>()
    var cursor = 0

    chipRegex.findAll(text).forEach { match ->
        // Text before this chip
        if (cursor < match.range.first) {
            segments.add(ContentSegment.TextSegment(text.substring(cursor, match.range.first)))
        }
        // Determine chip type from which group matched
        val chipText = match.value
        val type = when {
            chipText.startsWith("#") -> ChipType.TAG
            chipText.startsWith("@") -> ChipType.MENTION
            chipText.startsWith("!!") -> ChipType.DATE
            else -> ChipType.TAG // fallback, should not occur
        }
        segments.add(ContentSegment.ChipSegment(type, chipText))
        cursor = match.range.last + 1
    }

    // Text after the last chip
    if (cursor < text.length) {
        segments.add(ContentSegment.TextSegment(text.substring(cursor)))
    }

    return segments
}
