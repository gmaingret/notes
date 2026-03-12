package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class ReorderDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(id: String, afterId: String?): Result<Document> =
        documentRepository.reorderDocument(id, afterId)
}
