package com.gmaingret.notes.widget.sync

import android.content.Context
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.WidgetBullet
import com.gmaingret.notes.widget.WidgetEntryPoint
import com.gmaingret.notes.widget.WidgetStateStore
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.Document
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the widget refresh trigger logic.
 *
 * Tests [refreshWidgetIfDocMatches] in isolation using MockK.
 *
 * Test 1: When docId matches widget's pinned docId, saveBullets + saveDisplayState are called.
 * Test 2: When docId does NOT match widget's pinned docId, no writes happen.
 * Test 3: When no widget is configured (getFirstDocumentId returns null), no writes happen.
 */
class WidgetSyncTriggerTest {

    private lateinit var widgetStateStore: WidgetStateStore
    private lateinit var entryPoint: WidgetEntryPoint
    private lateinit var bulletRepository: BulletRepository
    private lateinit var documentRepository: DocumentRepository
    private lateinit var context: Context

    private val matchingDocId = "doc-123"
    private val differentDocId = "doc-456"
    private val sampleDocument = Document(id = matchingDocId, title = "Test Doc", position = 1.0)
    private val sampleBullets = listOf(
        Bullet(
            id = "b1",
            documentId = matchingDocId,
            parentId = null,
            content = "Root bullet 1",
            position = 1.0,
            isComplete = false,
            isCollapsed = false,
            note = null
        ),
        Bullet(
            id = "b2",
            documentId = matchingDocId,
            parentId = null,
            content = "Root bullet 2",
            position = 2.0,
            isComplete = false,
            isCollapsed = false,
            note = null
        )
    )

    @Before
    fun setUp() {
        widgetStateStore = mockk(relaxed = true)
        entryPoint = mockk()
        bulletRepository = mockk()
        documentRepository = mockk()
        context = mockk(relaxed = true)

        // Wire entry point mocks
        coEvery { entryPoint.bulletRepository() } returns bulletRepository
        coEvery { entryPoint.documentRepository() } returns documentRepository

        // Mock the static pushStateToGlance to avoid Glance framework calls in tests
        mockkObject(NotesWidget.Companion)
        coEvery { NotesWidget.pushStateToGlance(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(NotesWidget.Companion)
    }

    /**
     * Test 1: When currentDocId matches the widget's pinned docId, the helper
     * fetches data and writes saveBullets + saveDisplayState(CONTENT) to the store.
     */
    @Test
    fun `refreshWidgetIfDocMatches - writes bullets and CONTENT state when docId matches`() = runTest {
        // Arrange
        coEvery { widgetStateStore.getFirstDocumentId() } returns matchingDocId
        coEvery { documentRepository.getDocuments() } returns Result.success(listOf(sampleDocument))
        coEvery { bulletRepository.getBullets(matchingDocId) } returns Result.success(sampleBullets)

        // Act
        val result = refreshWidgetIfDocMatches(
            currentDocId = matchingDocId,
            widgetStateStore = widgetStateStore,
            entryPoint = entryPoint,
            context = context
        )

        // Assert
        assertTrue("Expected true when docIds match", result)
        coVerify { widgetStateStore.saveBullets(any()) }
        coVerify { widgetStateStore.saveDisplayState(DisplayState.CONTENT) }
        coVerify { NotesWidget.pushStateToGlance(context) }
    }

    /**
     * Test 2: When currentDocId does NOT match the widget's pinned docId,
     * no cache writes happen and the function returns false.
     */
    @Test
    fun `refreshWidgetIfDocMatches - does NOT write cache when docId does not match`() = runTest {
        // Arrange — widget is pinned to a different document
        coEvery { widgetStateStore.getFirstDocumentId() } returns differentDocId

        // Act — open document is matchingDocId, widget shows differentDocId
        val result = refreshWidgetIfDocMatches(
            currentDocId = matchingDocId,
            widgetStateStore = widgetStateStore,
            entryPoint = entryPoint,
            context = context
        )

        // Assert
        assertFalse("Expected false when docIds don't match", result)
        coVerify(exactly = 0) { widgetStateStore.saveBullets(any()) }
        coVerify(exactly = 0) { widgetStateStore.saveDisplayState(any()) }
    }

    /**
     * Test 3: When no widget is configured (getFirstDocumentId returns null),
     * no cache writes happen and the function returns false.
     */
    @Test
    fun `refreshWidgetIfDocMatches - does NOT write cache when no widget configured`() = runTest {
        // Arrange — no widget configured
        coEvery { widgetStateStore.getFirstDocumentId() } returns null

        // Act
        val result = refreshWidgetIfDocMatches(
            currentDocId = matchingDocId,
            widgetStateStore = widgetStateStore,
            entryPoint = entryPoint,
            context = context
        )

        // Assert
        assertFalse("Expected false when no widget configured", result)
        coVerify(exactly = 0) { widgetStateStore.saveBullets(any()) }
        coVerify(exactly = 0) { widgetStateStore.saveDisplayState(any()) }
    }
}
