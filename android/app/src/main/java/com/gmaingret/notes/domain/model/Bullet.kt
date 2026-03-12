package com.gmaingret.notes.domain.model

/**
 * Domain model for a bullet in a document's bullet tree.
 *
 * Position is Double because the backend uses FLOAT8 midpoint positioning
 * for stable fractional-indexing-based ordering among siblings.
 *
 * [parentId] is null for root-level bullets (direct children of the document).
 * [isCollapsed] controls whether children are shown in the flat list view.
 * [note] is an optional comment/annotation field (shown as inline note below bullet).
 */
data class Bullet(
    val id: String,
    val documentId: String,
    val parentId: String?,
    val content: String,
    val position: Double,
    val isComplete: Boolean,
    val isCollapsed: Boolean,
    val note: String?
)
