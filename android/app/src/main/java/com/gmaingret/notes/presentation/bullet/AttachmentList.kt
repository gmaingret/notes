package com.gmaingret.notes.presentation.bullet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.gmaingret.notes.domain.model.Attachment

/**
 * Inline attachment list composable rendered below a bullet row when expanded.
 *
 * For image attachments (mimeType starting with "image/"):
 *   - Renders a thumbnail via Coil [AsyncImage] with crossfade and gray placeholder
 *   - Max height 200.dp, fills max width, 8.dp rounded corners
 *   - Tapping opens a fullscreen lightbox Dialog
 *
 * For non-image attachments:
 *   - Renders a Row with file-type icon, filename, and size (KB/MB)
 *   - Tapping triggers [onDownload]
 */
@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    onDownload: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track which image (if any) is open in the lightbox
    var lightboxAttachment by remember { mutableStateOf<Attachment?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 28.dp) // indented to match bullet content
    ) {
        attachments.forEach { attachment ->
            if (attachment.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = attachment.downloadUrl,
                    contentDescription = attachment.filename,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFFE0E0E0)),
                    error = ColorPainter(Color(0xFFE0E0E0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { lightboxAttachment = attachment },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onDownload(attachment) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = if (attachment.mimeType == "application/pdf") {
                        Icons.Filled.PictureAsPdf
                    } else {
                        Icons.Filled.InsertDriveFile
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachment.filename,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatFileSize(attachment.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // Fullscreen lightbox Dialog — shown when an image thumbnail is tapped
    lightboxAttachment?.let { attachment ->
        ImageLightboxDialog(
            attachment = attachment,
            onDismiss = { lightboxAttachment = null }
        )
    }
}

/**
 * Fullscreen image lightbox shown when the user taps an image attachment thumbnail.
 *
 * Renders the full image via Coil [AsyncImage] with the auth-intercepted OkHttpClient.
 * A close button (X) in the top-right corner dismisses the dialog.
 * Tapping the background also dismisses.
 */
@Composable
private fun ImageLightboxDialog(
    attachment: Attachment,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = attachment.downloadUrl,
                contentDescription = attachment.filename,
                contentScale = ContentScale.Fit,
                placeholder = ColorPainter(Color(0xFF333333)),
                error = ColorPainter(Color(0xFF333333)),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    // Consume clicks on the image itself so they don't also dismiss the dialog
                    .clickable(onClick = {})
            )

            // Close button — top-right corner
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Filename label — bottom center
            Text(
                text = attachment.filename,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/** Formats byte count to human-readable KB or MB string. */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
