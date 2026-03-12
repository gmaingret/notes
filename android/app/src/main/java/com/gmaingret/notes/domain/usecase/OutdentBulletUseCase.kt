package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class OutdentBulletUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(id: String): Result<Bullet> =
        bulletRepository.outdentBullet(id)
}
