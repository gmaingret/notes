package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Handles opening a document and persisting it as the last-opened document.
 *
 * [invoke] calls openDocument (fire-and-forget — failure is intentionally ignored)
 * and always saves the docId to local persistence so the app can reopen it on cold start.
 *
 * [getLastDocId] delegates to repository for cold-start restoration.
 */
class OpenDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(id: String) {
        // Fire-and-forget: POST /documents/{id}/open may fail (no network, etc.)
        // We still persist the last opened doc ID so cold-start can restore it.
        documentRepository.openDocument(id) // ignore Result
        documentRepository.saveLastDocId(id)
    }

    suspend fun getLastDocId(): String? = documentRepository.getLastDocId()
}
