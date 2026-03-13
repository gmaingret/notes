package com.gmaingret.notes.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gmaingret.notes.domain.model.SearchResult

/**
 * Single row in the search results list.
 *
 * Shows the bullet content with the search query term highlighted using
 * a primary container background span (case-insensitive match).
 *
 * @param result the [SearchResult] to display
 * @param query the current search query (used to highlight matching text)
 * @param onClick called when the user taps this result
 */
@Composable
fun SearchResultItem(
    result: SearchResult,
    query: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = buildHighlightedText(result.content, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Builds an AnnotatedString where occurrences of [query] in [text] are highlighted
 * with a primary container background (case-insensitive).
 */
@Composable
private fun buildHighlightedText(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    val highlightStyle = androidx.compose.ui.text.SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer
    )

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var startIndex = 0

    while (startIndex < text.length) {
        val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
        if (matchIndex == -1) {
            append(text.substring(startIndex))
            break
        }
        // Text before match
        if (matchIndex > startIndex) {
            append(text.substring(startIndex, matchIndex))
        }
        // Highlighted match
        withStyle(highlightStyle) {
            append(text.substring(matchIndex, matchIndex + query.length))
        }
        startIndex = matchIndex + query.length
    }
}
