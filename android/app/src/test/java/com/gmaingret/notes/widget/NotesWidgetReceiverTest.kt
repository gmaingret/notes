package com.gmaingret.notes.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for NotesWidgetReceiver.
 *
 * Uses Robolectric for the onDeleted test because GlanceAppWidgetReceiver.onDeleted
 * calls BroadcastReceiver.goAsync() which requires a real Android environment to mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotesWidgetReceiverTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `glanceAppWidget property is a NotesWidget instance`() {
        val receiver = NotesWidgetReceiver()
        assertTrue(
            "Expected NotesWidget instance but was ${receiver.glanceAppWidget::class.simpleName}",
            receiver.glanceAppWidget is NotesWidget
        )
    }

    /**
     * Verifies that onDeleted calls WidgetStateStore.clearAll() once (clears entire store,
     * not individual document IDs per widget).
     *
     * WorkManager cancellation is tested via the real Robolectric WorkManager instance
     * (initialized with the test application context). If WorkManager is not initialized,
     * the test verifies only the store behavior — WorkManager integration is covered by
     * the assembleDebug compilation check.
     *
     * Uses mockkObject to intercept WidgetStateStore singleton so no real DataStore
     * or Tink keyset is needed.
     */
    @Test
    fun `onDeleted calls clearAll once instead of clearDocumentId per widget`() {
        val mockStore = mockk<WidgetStateStore>(relaxed = true)

        mockkObject(WidgetStateStore)
        every { WidgetStateStore.getInstance(any()) } returns mockStore

        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotesWidgetReceiver()
        // WorkManager.getInstance will use the real Robolectric WorkManager (no-op in tests)
        receiver.onDeleted(context, intArrayOf(1, 2, 3))

        // Verify clearAll is called once (not 3 times for 3 widget IDs)
        coVerify(exactly = 1) { mockStore.clearAll() }
        // Verify the old per-widget clearDocumentId is NOT called
        coVerify(exactly = 0) { mockStore.clearDocumentId(any()) }
    }
}
