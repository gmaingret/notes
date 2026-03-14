package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.data.model.VoiceCommandResponse

interface VoiceRepository {
    suspend fun sendCommand(text: String): Result<VoiceCommandResponse>
}
