package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.data.model.VoiceCommandResponse
import com.gmaingret.notes.domain.repository.VoiceRepository
import javax.inject.Inject

class SendVoiceCommandUseCase @Inject constructor(
    private val voiceRepository: VoiceRepository
) {
    suspend operator fun invoke(text: String): Result<VoiceCommandResponse> =
        voiceRepository.sendCommand(text)
}
