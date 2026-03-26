package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.DocumentRepository
import javax.inject.Inject

class ExportAllDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /** Returns the ZIP file bytes for all documents. */
    suspend operator fun invoke(): Result<ByteArray> =
        documentRepository.exportAll()
}
