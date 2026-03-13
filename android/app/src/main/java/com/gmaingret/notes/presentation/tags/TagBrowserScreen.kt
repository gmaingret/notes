package com.gmaingret.notes.presentation.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gmaingret.notes.domain.model.TagCount

/**
 * Tags browser screen showing all tag/mention/date chips grouped by type.
 *
 * Displayed when the user taps "Tags" in the navigation drawer.
 * Each chip shows its value and count. Tapping a chip triggers a search
 * for that chip (e.g. "#kotlin", "@alice", "!![2025-01-01]").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagBrowserScreen(
    uiState: TagsUiState,
    onTagClick: (chipType: String, value: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is TagsUiState.Loading -> {
                CircularProgressIndicator()
            }

            is TagsUiState.Empty -> {
                Text(
                    text = "No tags yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is TagsUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is TagsUiState.Success -> {
                // Canonical group order: tags, mentions, dates
                val groupOrder = listOf("tag", "mention", "date")
                val groupLabels = mapOf(
                    "tag" to "Tags (#)",
                    "mention" to "Mentions (@)",
                    "date" to "Dates (!!)"
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().align(Alignment.TopStart)
                ) {
                    groupOrder.forEach { chipType ->
                        val chips = uiState.grouped[chipType]
                        if (!chips.isNullOrEmpty()) {
                            item(key = "header_$chipType") {
                                Text(
                                    text = groupLabels[chipType] ?: chipType,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                HorizontalDivider()
                            }
                            item(key = "chips_$chipType") {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chips.forEach { tagCount ->
                                        TagChip(
                                            tagCount = tagCount,
                                            onClick = { onTagClick(tagCount.chipType, tagCount.value) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    tagCount: TagCount,
    onClick: () -> Unit
) {
    val prefix = when (tagCount.chipType) {
        "tag" -> "#"
        "mention" -> "@"
        "date" -> "!!"
        else -> ""
    }
    val displayText = if (tagCount.chipType == "date") {
        "!![${tagCount.value}]"
    } else {
        "$prefix${tagCount.value}"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Badge(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Text(
                    text = tagCount.count.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
