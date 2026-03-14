package com.gmaingret.notes.widget.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.WidgetBullet
import com.gmaingret.notes.widget.WidgetStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for WidgetSyncWorker.
 *
 * The worker is constructed directly with mocked dependencies since @HiltWorker's
 * @AssistedInject constructor is just a regular Kotlin constructor at the JVM level.
 * MockK mocks are used for WorkerParameters to avoid Android framework dependencies.
 */
class WidgetSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var bulletRepository: BulletRepository
    private lateinit var documentRepository: DocumentRepository
    private lateinit var widgetStateStore: WidgetStateStore

    private lateinit var worker: WidgetSyncWorker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        bulletRepository = mockk()
        documentRepository = mockk()
        widgetStateStore = mockk(relaxed = true)

        worker = WidgetSyncWorker(
            context = context,
            workerParams = workerParams,
            bulletRepository = bulletRepository,
            documentRepository = documentRepository,
            widgetStateStore = widgetStateStore
        )
    }

    // Test 1: no configured docId — no-op, return success
    @Test
    fun `doWork with no configured docId returns success without calling repositories`() = runTest {
        coEvery { widgetStateStore.getFirstDocumentId() } returns null

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { documentRepository.getDocuments() }
        coVerify(exactly = 0) { bulletRepository.getBullets(any()) }
    }

    // Test 2: valid docId, bullets fetched successfully — writes bullets and CONTENT state
    @Test
    fun `doWork with valid docId fetches bullets writes to store returns success`() = runTest {
        val docId = "doc-123"
        val documents = listOf(Document(id = docId, title = "My Doc", position = 1.0))
        val bullets = listOf(
            Bullet(
                id = "b1", documentId = docId, parentId = null,
                content = "First bullet", position = 1.0, isComplete = false,
                isCollapsed = false, note = null
            ),
            Bullet(
                id = "b2", documentId = docId, parentId = null,
                content = "Second bullet", position = 2.0, isComplete = true,
                isCollapsed = false, note = null
            )
        )

        coEvery { widgetStateStore.getFirstDocumentId() } returns docId
        coEvery { documentRepository.getDocuments() } returns Result.success(documents)
        coEvery { bulletRepository.getBullets(docId) } returns Result.success(bullets)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { widgetStateStore.saveBullets(any()) }
        coVerify { widgetStateStore.saveDisplayState(DisplayState.CONTENT) }
    }

    // Test 3: valid docId, empty bullet list — writes EMPTY state
    @Test
    fun `doWork with valid docId but empty bullets writes DisplayState EMPTY`() = runTest {
        val docId = "doc-456"
        val documents = listOf(Document(id = docId, title = "Empty Doc", position = 1.0))

        coEvery { widgetStateStore.getFirstDocumentId() } returns docId
        coEvery { documentRepository.getDocuments() } returns Result.success(documents)
        coEvery { bulletRepository.getBullets(docId) } returns Result.success(emptyList())

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { widgetStateStore.saveBullets(emptyList()) }
        coVerify { widgetStateStore.saveDisplayState(DisplayState.EMPTY) }
    }

    // Test 4: network failure (non-auth) — keeps existing cached data, returns success
    @Test
    fun `doWork on network failure keeps cached data returns success`() = runTest {
        val docId = "doc-789"

        coEvery { widgetStateStore.getFirstDocumentId() } returns docId
        coEvery { documentRepository.getDocuments() } returns Result.failure(
            RuntimeException("Network timeout")
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Must NOT overwrite cached data on non-auth network errors
        coVerify(exactly = 0) { widgetStateStore.saveBullets(any()) }
        coVerify(exactly = 0) { widgetStateStore.saveDisplayState(any()) }
    }

    // Test 5: 401 auth error after refresh failure — writes SESSION_EXPIRED, returns success
    @Test
    fun `doWork on 401 error writes DisplayState SESSION_EXPIRED returns success`() = runTest {
        val docId = "doc-auth"

        coEvery { widgetStateStore.getFirstDocumentId() } returns docId
        val httpException = mockk<HttpException>()
        io.mockk.every { httpException.code() } returns 401
        io.mockk.every { httpException.message } returns "HTTP 401 Unauthorized"
        coEvery { documentRepository.getDocuments() } returns Result.failure(httpException)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED) }
        coVerify(exactly = 0) { widgetStateStore.saveBullets(any()) }
    }

    // Test 6: document deleted (not found in list) — writes DOCUMENT_NOT_FOUND, returns success
    @Test
    fun `doWork with deleted document writes DisplayState DOCUMENT_NOT_FOUND returns success`() = runTest {
        val docId = "doc-deleted"
        // Server returns list but it doesn't contain our docId
        val documents = listOf(
            Document(id = "other-doc", title = "Other", position = 1.0)
        )

        coEvery { widgetStateStore.getFirstDocumentId() } returns docId
        coEvery { documentRepository.getDocuments() } returns Result.success(documents)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { widgetStateStore.saveDisplayState(DisplayState.DOCUMENT_NOT_FOUND) }
        coVerify(exactly = 0) { widgetStateStore.saveBullets(any()) }
    }
}
