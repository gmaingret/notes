package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.DocumentApi
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.data.model.CreateDocumentRequest
import com.gmaingret.notes.data.model.ImportDocumentRequest
import com.gmaingret.notes.data.model.RenameDocumentRequest
import com.gmaingret.notes.data.model.ReorderDocumentRequest
import com.gmaingret.notes.domain.model.Document
import com.gmaingret.notes.domain.repository.DocumentRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [DocumentRepository].
 *
 * Each network operation wraps the Retrofit call in try/catch and returns
 * Result.success / Result.failure so that ViewModels never need to handle exceptions.
 *
 * [getDocuments] sorts results by position ascending to match the backend's
 * fractional-indexing order without relying on server-side ORDER BY guarantees.
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentApi: DocumentApi,
    private val tokenStore: TokenStore
) : DocumentRepository {

    override suspend fun getDocuments(): Result<List<Document>> = try {
        val documents = documentApi.getDocuments()
            .map { it.toDomain() }
            .sortedBy { it.position }
        Result.success(documents)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun createDocument(title: String): Result<Document> = try {
        val dto = documentApi.createDocument(CreateDocumentRequest(title))
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun renameDocument(id: String, title: String): Result<Document> = try {
        val dto = documentApi.renameDocument(id, RenameDocumentRequest(title))
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteDocument(id: String): Result<Unit> = try {
        val response = documentApi.deleteDocument(id)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun reorderDocument(id: String, afterId: String?): Result<Document> = try {
        val dto = documentApi.reorderDocument(id, ReorderDocumentRequest(afterId))
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun openDocument(id: String): Result<Unit> = try {
        val response = documentApi.openDocument(id)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun importDocument(markdown: String): Result<Document> = try {
        val dto = documentApi.importDocument(ImportDocumentRequest(markdown))
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun exportDocument(id: String): Result<Pair<String, ByteArray>> = try {
        val response = documentApi.exportDocument(id)
        if (response.isSuccessful) {
            val body = response.body()?.bytes() ?: ByteArray(0)
            val disposition = response.headers()["Content-Disposition"] ?: ""
            val filename = Regex("""filename="(.+?)"""").find(disposition)?.groupValues?.get(1) ?: "export.md"
            Result.success(filename to body)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun exportAll(): Result<ByteArray> = try {
        val response = documentApi.exportAll()
        if (response.isSuccessful) {
            Result.success(response.body()?.bytes() ?: ByteArray(0))
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getLastDocId(): String? = tokenStore.getLastDocId()

    override suspend fun saveLastDocId(docId: String) = tokenStore.saveLastDocId(docId)
}
