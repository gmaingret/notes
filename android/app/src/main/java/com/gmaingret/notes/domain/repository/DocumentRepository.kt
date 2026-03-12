package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.Document

/**
 * Contract for document CRUD, reorder, open-tracking, and last-opened persistence.
 *
 * All network operations return [Result] so callers can handle errors without try/catch.
 * [getLastDocId] and [saveLastDocId] use local persistence (DataStore) and do not return Result.
 */
interface DocumentRepository {
    suspend fun getDocuments(): Result<List<Document>>
    suspend fun createDocument(title: String): Result<Document>
    suspend fun renameDocument(id: String, title: String): Result<Document>
    suspend fun deleteDocument(id: String): Result<Unit>
    suspend fun reorderDocument(id: String, afterId: String?): Result<Document>
    suspend fun openDocument(id: String): Result<Unit>
    suspend fun getLastDocId(): String?
    suspend fun saveLastDocId(docId: String)
}
