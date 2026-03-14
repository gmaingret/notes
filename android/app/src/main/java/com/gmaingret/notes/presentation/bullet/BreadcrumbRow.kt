package com.gmaingret.notes.presentation.bullet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gmaingret.notes.domain.model.Bullet

/**
 * Horizontal scrollable breadcrumb strip shown when zoomed into a bullet subtree.
 *
 * Layout: Home > crumb1 > crumb2 > ... > currentCrumb
 *
 * - First item is always "Home" (zoom back to document root, onCrumbClick(null))
 * - Each intermediate crumb is clickable (onCrumbClick(bullet.id))
 * - Last crumb is the current zoom level — displayed in onSurface color, not clickable
 * - Auto-scrolls to the last item whenever breadcrumbs change
 *
 * Visibility: This composable is only intended to be shown when breadcrumbs.isNotEmpty().
 * Wrap the call site in AnimatedVisibility(breadcrumbs.isNotEmpty()).
 *
 * @param breadcrumbs Ordered list from document root down to current zoom (excluding zoom root itself).
 * @param onCrumbClick Called with null to zoom to document root, or with a bullet ID to zoom to that level.
 * @param modifier Optional modifier.
 */
@Composable
fun BreadcrumbRow(
    breadcrumbs: List<Bullet>,
    onCrumbClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to last crumb whenever the list changes
    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) {
            // +1 for the Home item
            listState.animateScrollToItem(breadcrumbs.size)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home item
        item(key = "home") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onCrumbClick(null) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Breadcrumb items
        itemsIndexed(breadcrumbs, key = { _, bullet -> bullet.id }) { index, bullet ->
            val isLast = index == breadcrumbs.lastIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Separator chevron
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Crumb text
                Text(
                    text = bullet.content.take(20).ifEmpty { "..." },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLast) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = if (!isLast) {
                        Modifier
                            .clickable { onCrumbClick(bullet.id) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    }
                )
            }
        }
    }
}
