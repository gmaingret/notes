package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName
import com.gmaingret.notes.domain.model.UndoStatus

/**
 * API response DTO for the undo/redo stack status.
 *
 * Returned by POST /api/undo, POST /api/redo, and GET /api/undo/status.
 */
data class UndoStatusDto(
    @SerializedName("can_undo") val canUndo: Boolean,
    @SerializedName("can_redo") val canRedo: Boolean
) {
    fun toDomain(): UndoStatus = UndoStatus(
        canUndo = canUndo,
        canRedo = canRedo
    )
}
