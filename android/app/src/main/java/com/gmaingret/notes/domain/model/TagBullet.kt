package com.gmaingret.notes.domain.model

/**
 * Domain model for a bullet returned by a tag/mention/date search.
 *
 * Flat representation (no tree fields) — used for display in the Tags browser.
 * [bulletId] is used to navigate to the bullet in its document.
 */
data class TagBullet(
    val bulletId: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
)
