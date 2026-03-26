package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.CreateDocumentRequest
import com.gmaingret.notes.data.model.DocumentDto
import com.gmaingret.notes.data.model.ImportDocumentRequest
import com.gmaingret.notes.data.model.RenameDocumentRequest
import com.gmaingret.notes.data.model.ReorderDocumentRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for all 6 document CRUD + reorder + open-tracking endpoints.
 *
 * IMPORTANT: [openDocument] and [deleteDocument] return [Response]<Unit> (not plain Unit)
 * because the server returns 204 No Content with an empty body.
 * Retrofit's Gson converter throws on empty body if the return type is plain Unit.
 */
interface DocumentApi {

    @GET("api/documents")
    suspend fun getDocuments(): List<DocumentDto>

    @POST("api/documents")
    suspend fun createDocument(@Body request: CreateDocumentRequest): DocumentDto

    @PATCH("api/documents/{id}")
    suspend fun renameDocument(
        @Path("id") id: String,
        @Body request: RenameDocumentRequest
    ): DocumentDto

    @PATCH("api/documents/{id}/position")
    suspend fun reorderDocument(
        @Path("id") id: String,
        @Body request: ReorderDocumentRequest
    ): DocumentDto

    @POST("api/documents/{id}/open")
    suspend fun openDocument(@Path("id") id: String): Response<Unit>

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String): Response<Unit>

    @GET("api/documents/{id}/export")
    suspend fun exportDocument(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @GET("api/documents/export-all")
    suspend fun exportAll(): Response<okhttp3.ResponseBody>

    @POST("api/documents/import")
    suspend fun importDocument(@Body request: ImportDocumentRequest): DocumentDto
}
