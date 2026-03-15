package com.gmaingret.notes.widget.add

import android.content.Context
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.widget.DisplayState
import com.gmaingret.notes.widget.NotesWidget
import com.gmaingret.notes.widget.WidgetBullet
import com.gmaingret.notes.widget.WidgetStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for [performAddBullet] — the testable core of AddBulletActivity.
 *
 * Tests target the extracted [performAddBullet] function directly, avoiding any
 * dependency on Android UI framework (Activity, Toast, Compose).
 * Focus: optimistic insert, success/failure/auth error, state mutations in WidgetStateStore.
 */
class AddBulletActionTest {

    private lateinit var store: WidgetStateStore
    private lateinit var createBulletUseCase: CreateBulletUseCase
    private lateinit var context: Context

    private val existingBullet1 = WidgetBullet(id = "b1", content = "Existing 1", isComplete = false)
    private val existingBullet2 = WidgetBullet(id = "b2", content = "Existing 2", isComplete = false)

    private fun makeServerBullet(id: String, content: String): Bullet = Bullet(
        id = id,
        documentId = "doc-1",
        parentId = null,
        content = content,
        isComplete = false,
        position = 0.0,
        isCollapsed = false,
        note = null
    )

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        createBulletUseCase = mockk()
        context = mockk(relaxed = true)
        mockkObject(NotesWidget.Companion)
        coEvery { NotesWidget.pushStateToGlance(any()) } returns Unit
        coEvery { NotesWidget.pushToGlanceDirect(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(NotesWidget.Companion)
    }

    // -------------------------------------------------------------------------
    // Test 1: Calls CreateBulletUseCase with correct request
    // -------------------------------------------------------------------------

    @Test
    fun `performAddBullet calls CreateBulletUseCase with correct CreateBulletRequest`() = runTest {
        coEvery { store.getBullets() } returns listOf(existingBullet1)
        coEvery { createBulletUseCase.invoke(any()) } returns
            Result.success(makeServerBullet("server-id", "New bullet"))

        performAddBullet(
            docId = "doc-1",
            content = "New bullet",
            store = store,
            context = context,
            createBulletUseCase = createBulletUseCase
        )

        coVerify {
            createBulletUseCase.invoke(
                CreateBulletRequest(
                    documentId = "doc-1",
                    parentId = null,
                    afterId = null,
                    content = "New bullet"
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: On success, temp bullet is replaced with real bullet (server ID)
    // -------------------------------------------------------------------------

    @Test
    fun `on CreateBulletUseCase success, temp bullet is replaced with server-assigned ID`() =
        runTest {
            val originalList = listOf(existingBullet1, existingBullet2)
            coEvery { store.getBullets() } returns originalList
            coEvery { createBulletUseCase.invoke(any()) } returns
                Result.success(makeServerBullet("server-id-42", "My new bullet"))

            val result = performAddBullet(
                docId = "doc-1",
                content = "My new bullet",
                store = store,
                context = context,
                createBulletUseCase = createBulletUseCase
            )

            assertTrue(result is AddBulletResult.Success)

            // The final saveBullets call should contain the real ID, not a "temp-" ID
            coVerify {
                store.saveBullets(
                    withArg { saved ->
                        assertTrue(saved.any { it.id == "server-id-42" })
                        assertTrue(saved.none { it.id.startsWith("temp-") })
                    }
                )
            }
        }

    // -------------------------------------------------------------------------
    // Test 3: On failure, original list is restored (rollback)
    // -------------------------------------------------------------------------

    @Test
    fun `on CreateBulletUseCase failure, original bullet list is restored to WidgetStateStore`() =
        runTest {
            val originalList = listOf(existingBullet1, existingBullet2)
            coEvery { store.getBullets() } returns originalList
            coEvery { createBulletUseCase.invoke(any()) } returns
                Result.failure(RuntimeException("Network error"))

            val result = performAddBullet(
                docId = "doc-1",
                content = "New bullet",
                store = store,
                context = context,
                createBulletUseCase = createBulletUseCase
            )

            assertTrue(result is AddBulletResult.Failure)

            // Rollback: original list must be saved
            coVerify {
                store.saveBullets(originalList)
            }
        }

    // -------------------------------------------------------------------------
    // Test 4: On auth error (401), display state transitions to SESSION_EXPIRED
    // -------------------------------------------------------------------------

    @Test
    fun `on auth error 401, display state transitions to SESSION_EXPIRED`() = runTest {
        val authException = HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(401, it)
            }
        )
        coEvery { store.getBullets() } returns listOf(existingBullet1)
        coEvery { createBulletUseCase.invoke(any()) } returns Result.failure(authException)

        val result = performAddBullet(
            docId = "doc-1",
            content = "New bullet",
            store = store,
            context = context,
            createBulletUseCase = createBulletUseCase
        )

        assertTrue(result is AddBulletResult.AuthError)
        coVerify {
            store.saveDisplayState(DisplayState.SESSION_EXPIRED)
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: Optimistic add inserts new bullet at position 0 (top of list)
    // -------------------------------------------------------------------------

    @Test
    fun `optimistic add inserts new WidgetBullet at position 0 (top of list)`() = runTest {
        val originalList = listOf(existingBullet1, existingBullet2)
        coEvery { store.getBullets() } returns originalList
        coEvery { createBulletUseCase.invoke(any()) } returns
            Result.success(makeServerBullet("server-id", "New bullet"))

        performAddBullet(
            docId = "doc-1",
            content = "New bullet",
            store = store,
            context = context,
            createBulletUseCase = createBulletUseCase
        )

        // First saveBullets call = optimistic insert at index 0
        coVerify {
            store.saveBullets(
                withArg { saved ->
                    assertEquals(3, saved.size)
                    assertTrue(saved[0].id.startsWith("temp-"))
                    assertEquals(existingBullet1.id, saved[1].id)
                    assertEquals(existingBullet2.id, saved[2].id)
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Test 6: After successful add, display state is CONTENT
    // -------------------------------------------------------------------------

    @Test
    fun `after successful add, display state is CONTENT even if list was previously empty`() =
        runTest {
            coEvery { store.getBullets() } returns emptyList()
            coEvery { createBulletUseCase.invoke(any()) } returns
                Result.success(makeServerBullet("server-id", "First bullet"))

            val result = performAddBullet(
                docId = "doc-1",
                content = "First bullet",
                store = store,
                context = context,
                createBulletUseCase = createBulletUseCase
            )

            assertTrue(result is AddBulletResult.Success)
            coVerify {
                store.saveDisplayState(DisplayState.CONTENT)
            }
        }

    // -------------------------------------------------------------------------
    // Test 7: pushStateToGlance called immediately after optimistic insert
    // -------------------------------------------------------------------------

    @Test
    fun `pushStateToGlance is called immediately after optimistic insert`() = runTest {
        coEvery { store.getBullets() } returns listOf(existingBullet1)
        coEvery { createBulletUseCase.invoke(any()) } returns
            Result.success(makeServerBullet("server-id", "New bullet"))

        performAddBullet(
            docId = "doc-1",
            content = "New bullet",
            store = store,
            context = context,
            createBulletUseCase = createBulletUseCase
        )

        // pushToGlanceDirect called for optimistic insert, pushStateToGlance for API result
        coVerify(atLeast = 1) { NotesWidget.pushToGlanceDirect(context, any(), any(), any(), any()) }
    }
}
