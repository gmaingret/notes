package com.gmaingret.notes.widget

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gmaingret.notes.MainActivity

// Intent extra key for opening a specific document from the widget
const val OPEN_DOCUMENT_ID = "OPEN_DOCUMENT_ID"

/**
 * Strips widget-incompatible markdown syntax from bullet content.
 *
 * Removes:
 *  - **bold** markers (keeps inner text)
 *  - ~~strikethrough~~ markers (keeps inner text; visual strikethrough handled by TextDecoration)
 *  - #tag and @mention patterns (metadata chips that can't render in widget)
 *  - !!date patterns
 *
 * Trims extra whitespace left by removals.
 */
fun stripMarkdownSyntax(content: String): String {
    return content
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")   // **bold** -> bold
        .replace(Regex("~~(.*?)~~"), "$1")             // ~~strike~~ -> strike
        .replace(Regex("#[\\w/]+"), "")                // #tag
        .replace(Regex("@[\\w.]+"), "")                // @mention
        .replace(Regex("!!\\S+"), "")                  // !!date or !!{date obj}
        .replace(Regex("\\s{2,}"), " ")                // collapse multiple spaces
        .trim()
}

// ---------------------------------------------------------------------------
// Top-level dispatcher
// ---------------------------------------------------------------------------

/**
 * Dispatches the correct Glance composable based on the current [WidgetUiState].
 *
 * Wraps everything in a surface-colored Column with rounded corners that fill
 * the entire widget frame.
 */
@androidx.compose.runtime.Composable
fun WidgetContent(uiState: WidgetUiState, context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is WidgetUiState.Content -> ContentView(uiState, context)
            is WidgetUiState.Loading -> LoadingContent()
            is WidgetUiState.Empty -> EmptyContent()
            is WidgetUiState.Error -> ErrorContent(uiState.message)
            is WidgetUiState.DocumentNotFound -> DocumentNotFoundContent()
            is WidgetUiState.SessionExpired -> SessionExpiredContent(context)
            is WidgetUiState.NotConfigured -> NotConfiguredContent(context)
        }
    }
}

// ---------------------------------------------------------------------------
// State-specific composables
// ---------------------------------------------------------------------------

@androidx.compose.runtime.Composable
fun ContentView(state: WidgetUiState.Content, context: Context) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(title = state.documentTitle, documentId = state.documentId, context = context)
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(state.bullets) { bullet ->
                BulletRow(bullet = bullet, context = context, documentId = state.documentId)
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun HeaderRow(title: String, documentId: String, context: Context) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP
        putExtra(OPEN_DOCUMENT_ID, documentId)
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openAppIntent))
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary
                ),
                modifier = GlanceModifier
                    .padding(8.dp)
                    .clickable(actionStartActivity(openAppIntent))
            )
        }
        // Thin divider
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GlanceTheme.colors.outline)
        ) {}
    }
}

@androidx.compose.runtime.Composable
fun BulletRow(bullet: WidgetBullet, context: Context, documentId: String) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP
        putExtra(OPEN_DOCUMENT_ID, documentId)
    }
    val dotColor: ColorProvider = if (bullet.isComplete) {
        ColorProvider(Color(0xFF9CA3AF))
    } else {
        GlanceTheme.colors.primary
    }
    val textColor: ColorProvider = if (bullet.isComplete) {
        ColorProvider(Color(0xFF9CA3AF))
    } else {
        GlanceTheme.colors.onSurface
    }
    val textDecoration = if (bullet.isComplete) TextDecoration.LineThrough else TextDecoration.None

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(actionStartActivity(openAppIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bullet dot
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .cornerRadius(3.dp)
                .background(dotColor)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = bullet.content,
            maxLines = 1,
            style = TextStyle(
                fontSize = 14.sp,
                color = textColor,
                textDecoration = textDecoration
            ),
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

@androidx.compose.runtime.Composable
fun LoadingContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header placeholder
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(18.dp)
                .cornerRadius(4.dp)
                .background(ColorProvider(Color(0xFFD1D5DB)))
        ) {}
        Spacer(GlanceModifier.height(8.dp))
        // Divider placeholder
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(Color(0xFFD1D5DB)))
        ) {}
        Spacer(GlanceModifier.height(8.dp))
        // 4 bullet placeholder rows
        PlaceholderRow(180.dp)
        PlaceholderRow(120.dp)
        PlaceholderRow(160.dp)
        PlaceholderRow(100.dp)
    }
}

@androidx.compose.runtime.Composable
private fun PlaceholderRow(textWidth: Dp) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .cornerRadius(3.dp)
                .background(ColorProvider(Color(0xFFD1D5DB)))
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .width(textWidth)
                .height(12.dp)
                .cornerRadius(4.dp)
                .background(ColorProvider(Color(0xFFD1D5DB)))
        ) {}
    }
}

@androidx.compose.runtime.Composable
fun EmptyContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No bullets yet",
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

@androidx.compose.runtime.Composable
fun ErrorContent(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RetryActionCallback>()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't load",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.error
                )
            )
            Text(
                text = "Tap to retry",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun DocumentNotFoundContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<ReconfigureActionCallback>()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Document not found",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Text(
                text = "Tap to reconfigure",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun SessionExpiredContent(context: Context) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(openAppIntent)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Session expired",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Text(
                text = "Tap to sign in",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun NotConfiguredContent(context: Context) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(openAppIntent)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to configure",
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}
