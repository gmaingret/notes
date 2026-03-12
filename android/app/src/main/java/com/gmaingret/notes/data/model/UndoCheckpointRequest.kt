package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /api/bullets/{id}/undo-checkpoint.
 *
 * Records a content change in the server-side undo history.
 * [content] is the new (current) content after the edit.
 * [previousContent] is the content before the edit (null on first edit).
 */
data class UndoCheckpointRequest(
    val content: String,
    @SerializedName("previous_content") val previousContent: String?
)
