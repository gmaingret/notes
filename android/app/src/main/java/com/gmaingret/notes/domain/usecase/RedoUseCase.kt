package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.UndoStatus
import com.gmaingret.notes.domain.repository.BulletRepository
import javax.inject.Inject

class RedoUseCase @Inject constructor(
    private val bulletRepository: BulletRepository
) {
    suspend operator fun invoke(): Result<UndoStatus> =
        bulletRepository.redo()
}
