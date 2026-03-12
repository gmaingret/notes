package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.data.model.MoveBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class MoveBulletUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(id: String, request: MoveBulletRequest): Result<Bullet> =
        bulletRepository.moveBullet(id, request)
}
