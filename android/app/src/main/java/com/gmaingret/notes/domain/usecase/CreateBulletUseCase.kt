package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.data.model.CreateBulletRequest
import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class CreateBulletUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(request: CreateBulletRequest): Result<Bullet> =
        bulletRepository.createBullet(request)
}
