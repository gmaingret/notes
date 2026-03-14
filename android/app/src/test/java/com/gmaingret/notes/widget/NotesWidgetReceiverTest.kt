package com.gmaingret.notes.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
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
        unmockkObject(WidgetStateStore)
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
     * Verifies that onDeleted calls WidgetStateStore.clearDocumentId once for each
     * widget ID in the appWidgetIds array.
     *
     * Uses mockkObject to intercept the WidgetStateStore companion singleton so
     * no real DataStore or Tink keyset is needed.
     * Uses a real Robolectric application context so super.onDeleted() can proceed
     * without throwing (GlanceAppWidgetReceiver.onDeleted calls goAsync which requires
     * Android mocks that Robolectric provides).
     */
    @Test
    fun `onDeleted calls clearDocumentId for each widget id`() {
        val mockStore = mockk<WidgetStateStore>(relaxed = true)
        coEvery { mockStore.clearDocumentId(any()) } just Runs

        mockkObject(WidgetStateStore)
        every { WidgetStateStore.getInstance(any()) } returns mockStore

        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotesWidgetReceiver()
        receiver.onDeleted(context, intArrayOf(1, 2, 3))

        coVerify(exactly = 1) { mockStore.clearDocumentId(1) }
        coVerify(exactly = 1) { mockStore.clearDocumentId(2) }
        coVerify(exactly = 1) { mockStore.clearDocumentId(3) }
    }
}
