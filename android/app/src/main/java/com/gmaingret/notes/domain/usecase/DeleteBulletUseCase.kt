package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class DeleteBulletUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        bulletRepository.deleteBullet(id)
}
