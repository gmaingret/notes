package com.gmaingret.notes.widget

import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Unit tests for [performDelete] — the testable core of DeleteBulletActionCallback.
 *
 * Tests target the extracted [performDelete] function directly, which avoids
 * any dependency on Glance's GlanceId or Android Toast (not testable on JVM).
 * Focus: state mutations in WidgetStateStore and interactions with DeleteBulletUseCase.
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
    // Test 1: Optimistic removal from store
    // -------------------------------------------------------------------------

    @Test
    fun `onAction with valid bulletId removes that bullet from WidgetStateStore optimistically`() =
        runTest {
            coEvery { store.getBullets() } returns listOf(bullet1, bullet2, bullet3)
            coEvery { deleteBulletUseCase.invoke("b2") } returns Result.success(Unit)

            performDelete(
                bulletId = "b2",
                store = store,
                deleteBulletUseCase = deleteBulletUseCase
            )

            coVerify {
                store.saveBullets(
                    withArg { saved ->
                        assertFalse(saved.any { it.id == "b2" })
                        assertEquals(2, saved.size)
                    }
                )
            }
        }

    // -------------------------------------------------------------------------
    // Test 2: Calls DeleteBulletUseCase with correct bulletId
    // -------------------------------------------------------------------------

    @Test
    fun `onAction calls DeleteBulletUseCase with the correct bulletId`() = runTest {
        coEvery { store.getBullets() } returns listOf(bullet1, bullet2)
        coEvery { deleteBulletUseCase.invoke("b1") } returns Result.success(Unit)

        performDelete(
            bulletId = "b1",
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
            coEvery { store.getBullets() } returns original
            coEvery { deleteBulletUseCase.invoke("b1") } returns
                Result.failure(RuntimeException("Network error"))

            performDelete(
                bulletId = "b1",
                store = store,
                deleteBulletUseCase = deleteBulletUseCase
            )

            // First saveBullets call is optimistic (filtered list)
            // Second saveBullets call is rollback (original list)
            coVerify(atLeast = 2) { store.saveBullets(any()) }
            coVerify {
                store.saveBullets(original)
            }
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
        coEvery { store.getBullets() } returns listOf(bullet1)
        coEvery { deleteBulletUseCase.invoke("b1") } returns Result.failure(authException)

        performDelete(
            bulletId = "b1",
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        coVerify {
            store.saveDisplayState(DisplayState.SESSION_EXPIRED)
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: Missing bulletId returns without side effects
    // -------------------------------------------------------------------------

    @Test
    fun `onAction with missing bulletId parameter returns without side effects`() = runTest {
        performDelete(
            bulletId = null,
            store = store,
            deleteBulletUseCase = deleteBulletUseCase
        )

        coVerify(exactly = 0) { store.getBullets() }
        coVerify(exactly = 0) { store.saveBullets(any()) }
        coVerify(exactly = 0) { deleteBulletUseCase.invoke(any()) }
    }

    // -------------------------------------------------------------------------
    // Test 6: After successful delete, bullet not in store
    // -------------------------------------------------------------------------

    @Test
    fun `after successful delete, bullet list in store does not contain the deleted bullet`() =
        runTest {
            coEvery { store.getBullets() } returns listOf(bullet1, bullet2, bullet3)
            coEvery { deleteBulletUseCase.invoke("b3") } returns Result.success(Unit)

            performDelete(
                bulletId = "b3",
                store = store,
                deleteBulletUseCase = deleteBulletUseCase
            )

            // Verify exactly one saveBullets call (no rollback) and it excludes b3
            coVerify(exactly = 1) {
                store.saveBullets(
                    withArg { saved ->
                        assertFalse(saved.any { it.id == "b3" })
                        assertTrue(saved.any { it.id == "b1" })
                        assertTrue(saved.any { it.id == "b2" })
                    }
                )
            }
        }
}
