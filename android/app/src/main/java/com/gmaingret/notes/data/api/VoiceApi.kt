package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.VoiceCommandRequest
import com.gmaingret.notes.data.model.VoiceCommandResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface VoiceApi {

    @POST("api/voice/command")
    suspend fun sendCommand(@Body request: VoiceCommandRequest): VoiceCommandResponse
}
