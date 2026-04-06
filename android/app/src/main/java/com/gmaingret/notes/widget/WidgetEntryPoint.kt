package com.gmaingret.notes.widget

import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bulletRepository(): BulletRepository
    fun documentRepository(): DocumentRepository
    fun tokenStore(): TokenStore
    fun deleteBulletUseCase(): DeleteBulletUseCase
    fun createBulletUseCase(): CreateBulletUseCase
}
