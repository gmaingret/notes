package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.VoiceApi
import com.gmaingret.notes.data.model.VoiceCommandRequest
import com.gmaingret.notes.data.model.VoiceCommandResponse
import com.gmaingret.notes.domain.repository.VoiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepositoryImpl @Inject constructor(
    private val voiceApi: VoiceApi
) : VoiceRepository {

    override suspend fun sendCommand(text: String): Result<VoiceCommandResponse> = try {
        val response = voiceApi.sendCommand(VoiceCommandRequest(text))
        Result.success(response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
