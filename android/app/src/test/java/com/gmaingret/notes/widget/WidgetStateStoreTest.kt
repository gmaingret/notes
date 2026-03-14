package com.gmaingret.notes.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @After
    fun tearDown() = runTest {
        // Clear DataStore state between tests to prevent cross-test contamination.
        // Robolectric reuses the same DataStore backing file within a test class run.
        store.clearAll()
    }

    // ---------------------------------------------------------------------------
    // Existing tests — document ID persistence
    // ---------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------
    // New tests — bullet cache persistence
    // ---------------------------------------------------------------------------

    @Test
    fun `saveBullets then getBullets returns same list`() = runTest {
        val bullets = listOf(
            WidgetBullet(id = "b1", content = "First bullet", isComplete = false),
            WidgetBullet(id = "b2", content = "Done item", isComplete = true)
        )
        store.saveBullets(bullets)
        val result = store.getBullets()
        assertEquals(2, result.size)
        assertEquals("b1", result[0].id)
        assertEquals("First bullet", result[0].content)
        assertEquals(false, result[0].isComplete)
        assertEquals("b2", result[1].id)
        assertEquals(true, result[1].isComplete)
    }

    @Test
    fun `getBullets returns empty list when nothing saved`() = runTest {
        val result = store.getBullets()
        assertTrue(result.isEmpty())
    }

    // ---------------------------------------------------------------------------
    // New tests — display state persistence
    // ---------------------------------------------------------------------------

    @Test
    fun `saveDisplayState then getDisplayState returns same value`() = runTest {
        store.saveDisplayState(DisplayState.CONTENT)
        val result = store.getDisplayState()
        assertEquals(DisplayState.CONTENT, result)
    }

    @Test
    fun `getDisplayState returns NOT_CONFIGURED when nothing saved`() = runTest {
        val result = store.getDisplayState()
        assertEquals(DisplayState.NOT_CONFIGURED, result)
    }

    // ---------------------------------------------------------------------------
    // New tests — getFirstDocumentId
    // ---------------------------------------------------------------------------

    @Test
    fun `getFirstDocumentId returns stored docId`() = runTest {
        store.saveDocumentId(appWidgetId = 10, docId = "doc-first")
        val result = store.getFirstDocumentId()
        assertEquals("doc-first", result)
    }

    @Test
    fun `getFirstDocumentId returns null when no widgets configured`() = runTest {
        val result = store.getFirstDocumentId()
        assertNull(result)
    }

    // ---------------------------------------------------------------------------
    // New tests — clearAll
    // ---------------------------------------------------------------------------

    @Test
    fun `clearAll removes bullets display state and document IDs`() = runTest {
        store.saveDocumentId(appWidgetId = 5, docId = "doc-clear")
        store.saveBullets(listOf(WidgetBullet("b1", "content", false)))
        store.saveDisplayState(DisplayState.CONTENT)

        store.clearAll()

        assertNull(store.getDocumentId(appWidgetId = 5))
        assertTrue(store.getBullets().isEmpty())
        assertEquals(DisplayState.NOT_CONFIGURED, store.getDisplayState())
        assertNull(store.getFirstDocumentId())
    }
}
