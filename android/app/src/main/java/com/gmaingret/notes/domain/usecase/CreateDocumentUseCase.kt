package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class CreateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(title: String): Result<Document> =
        documentRepository.createDocument(title)
}
