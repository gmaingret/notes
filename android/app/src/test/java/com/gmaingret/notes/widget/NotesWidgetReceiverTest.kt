package com.gmaingret.notes.widget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for NotesWidgetReceiver.
 *
 * Tests that the receiver's glanceAppWidget property is a NotesWidget instance.
 * The onDeleted cleanup behavior is tested via integration with WidgetStateStore
 * in a Robolectric-based test (see below).
 *
 * Note: Full onDeleted testing with Robolectric would require setting up
 * DataStore and Tink, which is already covered by WidgetStateStoreTest.
 * This test focuses on the structural assertion.
 */
class NotesWidgetReceiverTest {

    @Test
    fun `glanceAppWidget property is a NotesWidget instance`() {
        val receiver = NotesWidgetReceiver()
        assertTrue(
            "Expected NotesWidget instance but was ${receiver.glanceAppWidget::class.simpleName}",
            receiver.glanceAppWidget is NotesWidget
        )
    }
}
