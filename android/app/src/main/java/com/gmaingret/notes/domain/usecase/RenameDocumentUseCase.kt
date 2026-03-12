package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class RenameDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(id: String, title: String): Result<Document> =
        documentRepository.renameDocument(id, title)
}
