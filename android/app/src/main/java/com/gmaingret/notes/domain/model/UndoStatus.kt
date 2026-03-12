package com.gmaingret.notes.domain.model

/**
 * Domain model representing the current state of the server-side undo/redo stack.
 *
 * Used to enable/disable the undo and redo toolbar buttons.
 */
data class UndoStatus(
    val canUndo: Boolean,
    val canRedo: Boolean
)
