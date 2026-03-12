package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.BulletApi
import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.data.model.UndoCheckpointRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.UndoStatus
import com.gmaingret.notes.domain.repository.BulletRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [BulletRepository].
 *
 * Each network operation wraps the Retrofit call in try/catch and returns
 * Result.success / Result.failure so that ViewModels never need to handle exceptions.
 *
 * [getBullets] returns bullets sorted by position ascending — consistent ordering
 * without relying on server-side ORDER BY guarantees.
 */
@Singleton
class BulletRepositoryImpl @Inject constructor(
    private val bulletApi: BulletApi
) : BulletRepository {

    override suspend fun getBullets(docId: String): Result<List<Bullet>> = try {
        val bullets = bulletApi.getBullets(docId)
            .map { it.toDomain() }
            .sortedBy { it.position }
        Result.success(bullets)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun createBullet(request: CreateBulletRequest): Result<Bullet> = try {
        val dto = bulletApi.createBullet(request)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun patchBullet(id: String, request: PatchBulletRequest): Result<Bullet> = try {
        val dto = bulletApi.patchBullet(id, request)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteBullet(id: String): Result<Unit> = try {
        val response = bulletApi.deleteBullet(id)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun indentBullet(id: String): Result<Bullet> = try {
        val dto = bulletApi.indentBullet(id)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun outdentBullet(id: String): Result<Bullet> = try {
        val dto = bulletApi.outdentBullet(id)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun moveBullet(id: String, request: MoveBulletRequest): Result<Bullet> = try {
        val dto = bulletApi.moveBullet(id, request)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun undo(): Result<UndoStatus> = try {
        val dto = bulletApi.undo()
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun redo(): Result<UndoStatus> = try {
        val dto = bulletApi.redo()
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getUndoStatus(): Result<UndoStatus> = try {
        val dto = bulletApi.getUndoStatus()
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun undoCheckpoint(id: String, request: UndoCheckpointRequest): Result<Unit> = try {
        val response = bulletApi.undoCheckpoint(id, request)
        if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
