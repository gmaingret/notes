package com.gmaingret.notes.data.local

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.util.Base64

/**
 * Provides Tink-based AES256-GCM encryption/decryption helpers for use with DataStore.
 *
 * DataStore Preferences does not natively support encryption, so we encrypt/decrypt
 * individual string values before reading/writing them. This is the recommended
 * pattern with Tink 1.8.0 + DataStore 1.2.1.
 *
 * Keyset is stored in SharedPreferences (encrypted by Android Keystore master key),
 * which is the standard Android-safe approach with Tink.
 */
object EncryptedDataStoreFactory {

    private const val AUTH_TOKENS_PREF = "__androidx_security_crypto_encrypted_prefs_auth_tokens__"
    private const val COOKIE_JAR_PREF = "__androidx_security_crypto_encrypted_prefs_cookie_jar__"
    private const val WIDGET_STATE_PREF = "__androidx_security_crypto_encrypted_prefs_widget_state__"
    private const val AUTH_TOKENS_KEYSET = "auth_tokens_keyset"
    private const val COOKIE_JAR_KEYSET = "cookie_jar_keyset"
    private const val WIDGET_STATE_KEYSET = "widget_state_keyset"
    private const val MASTER_KEY_URI = "android-keystore://notes_master_key"

    init {
        AeadConfig.register()
    }

    private fun getAead(context: Context, prefName: String, keysetName: String): Aead {
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, keysetName, prefName)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    fun getAuthTokensAead(context: Context): Aead =
        getAead(context, AUTH_TOKENS_PREF, AUTH_TOKENS_KEYSET)

    fun getCookieJarAead(context: Context): Aead =
        getAead(context, COOKIE_JAR_PREF, COOKIE_JAR_KEYSET)

    fun getWidgetStateAead(context: Context): Aead =
        getAead(context, WIDGET_STATE_PREF, WIDGET_STATE_KEYSET)

    /**
     * Encrypts a plaintext string and returns a Base64-encoded ciphertext.
     * The key name is used as associated data for domain separation.
     */
    fun encrypt(aead: Aead, plaintext: String, associatedData: String = ""): String {
        val ciphertext = aead.encrypt(
            plaintext.toByteArray(Charsets.UTF_8),
            associatedData.toByteArray(Charsets.UTF_8)
        )
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    /**
     * Decrypts a Base64-encoded ciphertext string back to plaintext.
     * Returns null if decryption fails (e.g. corrupted data or wrong key).
     */
    fun decrypt(aead: Aead, encoded: String, associatedData: String = ""): String? {
        return try {
            val ciphertext = Base64.getDecoder().decode(encoded)
            val plaintext = aead.decrypt(
                ciphertext,
                associatedData.toByteArray(Charsets.UTF_8)
            )
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
