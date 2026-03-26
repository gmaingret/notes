package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class ExportDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /** Returns (filename, content bytes) for the exported markdown. */
    suspend operator fun invoke(id: String): Result<Pair<String, ByteArray>> =
        documentRepository.exportDocument(id)
}
