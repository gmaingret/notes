package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.data.model.PatchBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class PatchBulletUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(id: String, request: PatchBulletRequest): Result<Bullet> =
        bulletRepository.patchBullet(id, request)
}
