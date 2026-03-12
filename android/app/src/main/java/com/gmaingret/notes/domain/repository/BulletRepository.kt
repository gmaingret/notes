package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.data.model.UndoCheckpointRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.UndoStatus

/**
 * Repository contract for all bullet tree operations.
 *
 * All methods return Result<T> so that ViewModels/use cases never need to catch exceptions.
 * Structural operations (indent, outdent, move) return the updated bullet after server recalculation.
 */
interface BulletRepository {

    suspend fun getBullets(docId: String): Result<List<Bullet>>

    suspend fun createBullet(request: CreateBulletRequest): Result<Bullet>

    suspend fun patchBullet(id: String, request: PatchBulletRequest): Result<Bullet>

    suspend fun deleteBullet(id: String): Result<Unit>

    suspend fun indentBullet(id: String): Result<Bullet>

    suspend fun outdentBullet(id: String): Result<Bullet>

    suspend fun moveBullet(id: String, request: MoveBulletRequest): Result<Bullet>

    suspend fun undo(): Result<UndoStatus>

    suspend fun redo(): Result<UndoStatus>

    suspend fun getUndoStatus(): Result<UndoStatus>

    suspend fun undoCheckpoint(id: String, request: UndoCheckpointRequest): Result<Unit>
}
