package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.BulletDto
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.data.model.UndoCheckpointRequest
import com.gmaingret.notes.data.model.UndoStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for all 11 bullet CRUD + structural + undo/redo endpoints.
 *
 * IMPORTANT: [deleteBullet] and [undoCheckpoint] return [Response]<Unit> because the
 * server returns 200 with {ok:true} or 204 empty body — Gson throws on empty body
 * with plain Unit return type (same pattern as DocumentApi.deleteDocument).
 *
 * [indentBullet] and [outdentBullet] are fire-and-forget structural operations that
 * return the updated bullet after the server-side position recalculation.
 */
interface BulletApi {

    @GET("api/bullets/documents/{docId}/bullets")
    suspend fun getBullets(@Path("docId") docId: String): List<BulletDto>

    @POST("api/bullets")
    suspend fun createBullet(@Body request: CreateBulletRequest): BulletDto

    @PATCH("api/bullets/{id}")
    suspend fun patchBullet(
        @Path("id") id: String,
        @Body request: PatchBulletRequest
    ): BulletDto

    @DELETE("api/bullets/{id}")
    suspend fun deleteBullet(@Path("id") id: String): Response<Unit>

    @POST("api/bullets/{id}/indent")
    suspend fun indentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/outdent")
    suspend fun outdentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/move")
    suspend fun moveBullet(
        @Path("id") id: String,
        @Body request: MoveBulletRequest
    ): BulletDto

    @POST("api/undo")
    suspend fun undo(): UndoStatusDto

    @POST("api/redo")
    suspend fun redo(): UndoStatusDto

    @GET("api/undo/status")
    suspend fun getUndoStatus(): UndoStatusDto

    @POST("api/bullets/{id}/undo-checkpoint")
    suspend fun undoCheckpoint(
        @Path("id") id: String,
        @Body request: UndoCheckpointRequest
    ): Response<Unit>
}
