package com.gmaingret.notes.widget

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test for NotesWidgetColorScheme.
 *
 * Uses Robolectric because ColorProviders wraps Compose Material3 color schemes
 * which require the Android runtime to initialize.
 *
 * Verifies that the colors object is initialized (not null). The type guarantee
 * is enforced by the Kotlin compiler at compile time — the object declares
 * `val colors: ColorProviders` so no runtime cast is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotesWidgetColorSchemeTest {

    @Test
    fun `colors object is initialized and not null`() {
        val colors = NotesWidgetColorScheme.colors
        assertNotNull("NotesWidgetColorScheme.colors must not be null", colors)
    }
}
