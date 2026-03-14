package com.gmaingret.notes.voice

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceDataStore by preferencesDataStore(name = "voice_preferences")

@Singleton
class VoicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
    }

    val isVoiceEnabled: Flow<Boolean> = context.voiceDataStore.data
        .map { preferences -> preferences[KEY_VOICE_ENABLED] ?: false }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[KEY_VOICE_ENABLED] = enabled
        }
    }
}
