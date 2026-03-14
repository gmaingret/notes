package com.gmaingret.notes.widget

import com.gmaingret.notes.data.local.DataStoreCookieJar
import com.gmaingret.notes.data.local.TokenStore
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.domain.usecase.CreateBulletUseCase
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt @EntryPoint for the widget.
 *
 * GlanceAppWidget cannot use @AndroidEntryPoint, so we use the manual
 * EntryPointAccessors pattern inside provideGlance():
 *
 *   val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
 *
 * This exposes the application-scoped singletons the widget needs for
 * data fetching and authentication.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bulletRepository(): BulletRepository
    fun documentRepository(): DocumentRepository
    fun tokenStore(): TokenStore
    fun dataStoreCookieJar(): DataStoreCookieJar
    fun deleteBulletUseCase(): DeleteBulletUseCase
    fun createBulletUseCase(): CreateBulletUseCase
}
