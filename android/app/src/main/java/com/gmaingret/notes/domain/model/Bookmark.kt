package com.gmaingret.notes.domain.model

/**
 * Domain model for a bookmarked bullet.
 *
 * [bulletId] is used as the key for add/remove operations.
 * [documentTitle] is used to show which document the bookmark belongs to.
 */
data class Bookmark(
    val bulletId: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
)
