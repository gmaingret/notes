package com.gmaingret.notes.data.model

/**
 * Request body for PATCH /api/bullets/{id}.
 *
 * IMPORTANT: The server processes only ONE field at a time (first non-null wins).
 * Callers must send only the single field being changed. Never populate more than
 * one field per request.
 *
 * Use the named factory functions for each update type to enforce this contract.
 */
data class PatchBulletRequest(
    val content: String? = null,
    val isComplete: Boolean? = null,
    val isCollapsed: Boolean? = null,
    val note: String? = null
) {
    companion object {
        fun updateContent(content: String) = PatchBulletRequest(content = content)
        fun updateIsComplete(isComplete: Boolean) = PatchBulletRequest(isComplete = isComplete)
        fun updateIsCollapsed(isCollapsed: Boolean) = PatchBulletRequest(isCollapsed = isCollapsed)
        fun updateNote(note: String) = PatchBulletRequest(note = note)
    }
}
