package com.gmaingret.notes.widget

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Unit tests for the stripMarkdownSyntax helper.
 *
 * Pure Kotlin — no Robolectric or Android dependencies needed because
 * stripMarkdownSyntax is a plain function operating on strings only.
 */
class WidgetContentHelperTest {

    @Test
    fun `stripMarkdownSyntax removes tags, mentions, dates, keeps bold text`() {
        val input = "hello #tag @mention !!date **bold**"
        val result = stripMarkdownSyntax(input)
        assertEquals("hello bold", result)
    }

    @Test
    fun `stripMarkdownSyntax leaves normal text unchanged`() {
        val input = "normal text"
        val result = stripMarkdownSyntax(input)
        assertEquals("normal text", result)
    }

    @Test
    fun `stripMarkdownSyntax strips strikethrough markers keeping content`() {
        val input = "~~strikethrough~~"
        val result = stripMarkdownSyntax(input)
        assertEquals("strikethrough", result)
    }

    @Test
    fun `stripMarkdownSyntax handles empty string`() {
        val result = stripMarkdownSyntax("")
        assertEquals("", result)
    }
}
