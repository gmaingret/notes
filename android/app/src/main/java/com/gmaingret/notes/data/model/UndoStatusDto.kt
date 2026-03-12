package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.UndoStatus

/**
 * API response DTO for the undo/redo stack status.
 *
 * Returned by POST /api/undo, POST /api/redo, and GET /api/undo/status.
 */
data class UndoStatusDto(
    val canUndo: Boolean,
    val canRedo: Boolean
) {
    fun toDomain(): UndoStatus = UndoStatus(
        canUndo = canUndo,
        canRedo = canRedo
    )
}
