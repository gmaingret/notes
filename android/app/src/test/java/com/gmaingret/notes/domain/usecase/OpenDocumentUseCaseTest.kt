package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenDocumentUseCaseTest {

    private lateinit var documentRepository: DocumentRepository
    private lateinit var openDocumentUseCase: OpenDocumentUseCase

    @Before
    fun setUp() {
        documentRepository = mockk()
        openDocumentUseCase = OpenDocumentUseCase(documentRepository)
    }

    @Test
    fun `invoke calls openDocument and saveLastDocId on success`() = runTest {
        coEvery { documentRepository.openDocument("1") } returns Result.success(Unit)
        coEvery { documentRepository.saveLastDocId("1") } returns Unit

        openDocumentUseCase("1")

        coVerify(exactly = 1) { documentRepository.openDocument("1") }
        coVerify(exactly = 1) { documentRepository.saveLastDocId("1") }
    }

    @Test
    fun `invoke saves lastDocId even when openDocument fails`() = runTest {
        val exception = RuntimeException("Network error")
        coEvery { documentRepository.openDocument("1") } returns Result.failure(exception)
        coEvery { documentRepository.saveLastDocId("1") } returns Unit

        // Should not throw — openDocument failure is fire-and-forget
        openDocumentUseCase("1")

        coVerify(exactly = 1) { documentRepository.openDocument("1") }
        coVerify(exactly = 1) { documentRepository.saveLastDocId("1") }
    }

    @Test
    fun `getLastDocId delegates to repository`() = runTest {
        coEvery { documentRepository.getLastDocId() } returns "doc-123"

        val result = openDocumentUseCase.getLastDocId()

        assertTrue(result == "doc-123")
        coVerify(exactly = 1) { documentRepository.getLastDocId() }
    }

    @Test
    fun `getLastDocId returns null when no last doc`() = runTest {
        coEvery { documentRepository.getLastDocId() } returns null

        val result = openDocumentUseCase.getLastDocId()

        assertTrue(result == null)
    }
}
