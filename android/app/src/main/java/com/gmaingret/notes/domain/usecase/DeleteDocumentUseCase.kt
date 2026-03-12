package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        documentRepository.deleteDocument(id)
}
