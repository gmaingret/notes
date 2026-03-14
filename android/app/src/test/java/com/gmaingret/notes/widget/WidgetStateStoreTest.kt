package com.gmaingret.notes.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for WidgetStateStore.
 *
 * Uses Robolectric to provide a real Context for DataStore.
 * Tink Aead is mocked to pass plaintext through (identity cipher) so tests
 * are not dependent on Android Keystore availability in the Robolectric environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetStateStoreTest {

    private lateinit var context: Context
    private lateinit var store: WidgetStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Mock Aead as identity cipher: encrypt returns plaintext bytes, decrypt returns same
        val mockAead = mockk<Aead>()
        every { mockAead.encrypt(any(), any()) } answers {
            firstArg<ByteArray>()
        }
        every { mockAead.decrypt(any(), any()) } answers {
            firstArg<ByteArray>()
        }

        store = WidgetStateStore.createForTest(context, mockAead)
    }

    @Test
    fun `saveDocumentId then getDocumentId returns same value`() = runTest {
        store.saveDocumentId(appWidgetId = 42, docId = "doc-uuid-123")
        val result = store.getDocumentId(appWidgetId = 42)
        assertEquals("doc-uuid-123", result)
    }

    @Test
    fun `getDocumentId for unknown id returns null`() = runTest {
        val result = store.getDocumentId(appWidgetId = 999)
        assertNull(result)
    }

    @Test
    fun `clearDocumentId then getDocumentId returns null`() = runTest {
        store.saveDocumentId(appWidgetId = 42, docId = "doc-uuid-abc")
        store.clearDocumentId(appWidgetId = 42)
        val result = store.getDocumentId(appWidgetId = 42)
        assertNull(result)
    }

    @Test
    fun `clearDocumentId does not affect other widget ids`() = runTest {
        store.saveDocumentId(appWidgetId = 1, docId = "doc-1")
        store.saveDocumentId(appWidgetId = 2, docId = "doc-2")
        store.clearDocumentId(appWidgetId = 1)
        assertNull(store.getDocumentId(appWidgetId = 1))
        assertEquals("doc-2", store.getDocumentId(appWidgetId = 2))
    }
}
