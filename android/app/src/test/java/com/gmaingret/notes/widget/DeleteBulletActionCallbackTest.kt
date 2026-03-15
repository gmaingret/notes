package com.gmaingret.notes.widget

import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for [performDelete] — the testable core of DeleteBulletActionCallback.
 *
 * The optimistic store update is now done by the caller (onAction), so these tests
 * focus on the API call and rollback behaviour only.
 */
class DeleteBulletActionCallbackTest {

    private lateinit var store: WidgetStateStore
    private lateinit var deleteBulletUseCase: DeleteBulletUseCase

    private val bullet1 = WidgetBullet(id = "b1", content = "First", isComplete = false)
    private val bullet2 = WidgetBullet(id = "b2", content = "Second", isComplete = true)
    private val bullet3 = WidgetBullet(id = "b3", content = "Third", isComplete = false)

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        deleteBulletUseCase = mockk()
    }

    // -------------------------------------------------------------------------
    // Test 1: Successful delete — no rollback, returns null
    // -------------------------------------------------------------------------

    @Test
    fun `on successful delete, returns null (no toast) and does not rollback`() = runTest {
        val original = listOf(bullet1, bullet2, bullet3)
        coEvery { deleteBulletUseCase.invoke("b2") } returns Result.success(Unit)

        val toastMessage = performDelete(
            bulletId = "b2",
            originalBullets = original,
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        assertNull(toastMessage)
        coVerify(exactly = 0) { store.saveBullets(any()) }
    }

    // -------------------------------------------------------------------------
    // Test 2: Calls DeleteBulletUseCase with correct bulletId
    // -------------------------------------------------------------------------

    @Test
    fun `calls DeleteBulletUseCase with the correct bulletId`() = runTest {
        coEvery { deleteBulletUseCase.invoke("b1") } returns Result.success(Unit)

        performDelete(
            bulletId = "b1",
            originalBullets = listOf(bullet1, bullet2),
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        coVerify(exactly = 1) { deleteBulletUseCase.invoke("b1") }
    }

    // -------------------------------------------------------------------------
    // Test 3: Rollback on failure
    // -------------------------------------------------------------------------

    @Test
    fun `on DeleteBulletUseCase failure, original bullet list is restored to WidgetStateStore`() =
        runTest {
            val original = listOf(bullet1, bullet2, bullet3)
            coEvery { deleteBulletUseCase.invoke("b1") } returns
                Result.failure(RuntimeException("Network error"))

            val toastMessage = performDelete(
                bulletId = "b1",
                originalBullets = original,
                store = store,
                deleteBulletUseCase = deleteBulletUseCase
            )

            assertEquals("Couldn't delete", toastMessage)
            coVerify { store.saveBullets(original) }
            coVerify { store.saveDisplayState(DisplayState.CONTENT) }
        }

    // -------------------------------------------------------------------------
    // Test 4: Auth error transitions to SESSION_EXPIRED
    // -------------------------------------------------------------------------

    @Test
    fun `on auth error, display state transitions to SESSION_EXPIRED`() = runTest {
        val authException = HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(401, it)
            }
        )
        val original = listOf(bullet1)
        coEvery { deleteBulletUseCase.invoke("b1") } returns Result.failure(authException)

        val toastMessage = performDelete(
            bulletId = "b1",
            originalBullets = original,
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        assertEquals("Session expired", toastMessage)
        coVerify { store.saveDisplayState(DisplayState.SESSION_EXPIRED) }
    }

    // -------------------------------------------------------------------------
    // Test 5: Missing bulletId returns without side effects
    // -------------------------------------------------------------------------

    @Test
    fun `with null bulletId returns null without side effects`() = runTest {
        val toastMessage = performDelete(
            bulletId = null,
            originalBullets = emptyList(),
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        assertNull(toastMessage)
        coVerify(exactly = 0) { store.saveBullets(any()) }
        coVerify(exactly = 0) { deleteBulletUseCase.invoke(any()) }
    }
}
