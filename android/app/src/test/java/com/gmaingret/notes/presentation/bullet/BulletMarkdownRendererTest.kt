package com.gmaingret.notes.presentation.bullet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BulletMarkdownRenderer].
 *
 * Tests cover:
 * - Plain text produces no spans
 * - Bold (**text**) applies FontWeight.Bold to correct range
 * - Italic (*text* and _text_) applies FontStyle.Italic
 * - Strikethrough (~~text~~) applies TextDecoration.LineThrough
 * - Links ([label](url)) produce blue underline + LinkAnnotation with correct URL
 * - #tag extraction as ChipSegment(TAG)
 * - @mention extraction as ChipSegment(MENTION)
 * - !!date extraction as ChipSegment(DATE)
 * - Mixed text+chips produce correct ordered segment list
 * - Text with no special syntax is a single TextSegment
 */
class BulletMarkdownRendererTest {

    // -----------------------------------------------------------------------
    // buildMarkdownAnnotatedString tests
    // -----------------------------------------------------------------------

    @Test
    fun `plain text returns AnnotatedString with no spans`() {
        val result = buildMarkdownAnnotatedString("hello world")
        assertEquals("hello world", result.text)
        assertTrue("No spans expected for plain text", result.spanStyles.isEmpty())
    }

    @Test
    fun `bold markers apply FontWeight Bold to inner text`() {
        val result = buildMarkdownAnnotatedString("**bold**")
        // The displayed text should strip the markers: "bold"
        assertEquals("bold", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(FontWeight.Bold, result.spanStyles[0].item.fontWeight)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(4, result.spanStyles[0].end)
    }

    @Test
    fun `italic star markers apply FontStyle Italic to inner text`() {
        val result = buildMarkdownAnnotatedString("*italic*")
        assertEquals("italic", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(FontStyle.Italic, result.spanStyles[0].item.fontStyle)
    }

    @Test
    fun `italic underscore markers apply FontStyle Italic to inner text`() {
        val result = buildMarkdownAnnotatedString("_italic_")
        assertEquals("italic", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(FontStyle.Italic, result.spanStyles[0].item.fontStyle)
    }

    @Test
    fun `strikethrough markers apply LineThrough decoration`() {
        val result = buildMarkdownAnnotatedString("~~strike~~")
        assertEquals("strike", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(TextDecoration.LineThrough, result.spanStyles[0].item.textDecoration)
    }

    @Test
    fun `link produces blue underlined span with annotation`() {
        val result = buildMarkdownAnnotatedString("[label](https://example.com)")
        assertEquals("label", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0].item
        assertEquals(TextDecoration.Underline, span.textDecoration)
        assertEquals(Color.Blue, span.color)
        // Check URL annotation exists
        val annotations = result.getUrlAnnotations(0, result.text.length)
        assertEquals(1, annotations.size)
        assertEquals("https://example.com", annotations[0].item.url)
    }

    @Test
    fun `mixed text and bold preserves surrounding text`() {
        val result = buildMarkdownAnnotatedString("Hello **world** bye")
        assertEquals("Hello world bye", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0]
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        // "world" starts at index 6
        assertEquals(6, span.start)
        assertEquals(11, span.end)
    }

    @Test
    fun `bold and italic in same string produce two spans`() {
        val result = buildMarkdownAnnotatedString("**bold** and *italic*")
        assertEquals("bold and italic", result.text)
        assertEquals(2, result.spanStyles.size)
        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        val italicSpan = result.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assertTrue("Bold span expected", boldSpan != null)
        assertTrue("Italic span expected", italicSpan != null)
    }

    // -----------------------------------------------------------------------
    // parseContentSegments tests
    // -----------------------------------------------------------------------

    @Test
    fun `text with no special syntax returns single TextSegment`() {
        val segments = parseContentSegments("just plain text")
        assertEquals(1, segments.size)
        val seg = segments[0] as ContentSegment.TextSegment
        assertEquals("just plain text", seg.text)
    }

    @Test
    fun `hashtag is extracted as TAG ChipSegment`() {
        val segments = parseContentSegments("#tag")
        assertEquals(1, segments.size)
        val chip = segments[0] as ContentSegment.ChipSegment
        assertEquals(ChipType.TAG, chip.type)
        assertEquals("#tag", chip.text)
    }

    @Test
    fun `mention is extracted as MENTION ChipSegment`() {
        val segments = parseContentSegments("@mention")
        assertEquals(1, segments.size)
        val chip = segments[0] as ContentSegment.ChipSegment
        assertEquals(ChipType.MENTION, chip.type)
        assertEquals("@mention", chip.text)
    }

    @Test
    fun `date is extracted as DATE ChipSegment`() {
        val segments = parseContentSegments("!!2024-01-15")
        assertEquals(1, segments.size)
        val chip = segments[0] as ContentSegment.ChipSegment
        assertEquals(ChipType.DATE, chip.type)
        assertEquals("!!2024-01-15", chip.text)
    }

    @Test
    fun `mixed text and hashtag produces TextSegment then ChipSegment`() {
        val segments = parseContentSegments("Hello #tag")
        assertEquals(2, segments.size)
        val text = segments[0] as ContentSegment.TextSegment
        assertEquals("Hello ", text.text)
        val chip = segments[1] as ContentSegment.ChipSegment
        assertEquals(ChipType.TAG, chip.type)
        assertEquals("#tag", chip.text)
    }

    @Test
    fun `multiple chips with surrounding text are ordered correctly`() {
        // "Buy @milk and #todo for !!2024-01-15"
        val segments = parseContentSegments("Buy @milk and #todo for !!2024-01-15")
        assertEquals(6, segments.size)

        val text1 = segments[0] as ContentSegment.TextSegment
        assertEquals("Buy ", text1.text)

        val chip1 = segments[1] as ContentSegment.ChipSegment
        assertEquals(ChipType.MENTION, chip1.type)

        val text2 = segments[2] as ContentSegment.TextSegment
        assertEquals(" and ", text2.text)

        val chip2 = segments[3] as ContentSegment.ChipSegment
        assertEquals(ChipType.TAG, chip2.type)

        val text3 = segments[4] as ContentSegment.TextSegment
        assertEquals(" for ", text3.text)

        val chip3 = segments[5] as ContentSegment.ChipSegment
        assertEquals(ChipType.DATE, chip3.type)
    }

    @Test
    fun `empty string returns empty segment list`() {
        val segments = parseContentSegments("")
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `text after chip is captured as TextSegment`() {
        val segments = parseContentSegments("#tag after")
        assertEquals(2, segments.size)
        val chip = segments[0] as ContentSegment.ChipSegment
        assertEquals(ChipType.TAG, chip.type)
        val text = segments[1] as ContentSegment.TextSegment
        assertEquals(" after", text.text)
    }
}
