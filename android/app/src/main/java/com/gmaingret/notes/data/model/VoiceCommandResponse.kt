package com.gmaingret.notes.data.model

data class VoiceCommandResponse(
    val success: Boolean,
    val action: String,
    val message: String
)
