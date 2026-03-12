package com.gmaingret.notes.di

import com.gmaingret.notes.data.repository.AuthRepositoryImpl
import com.gmaingret.notes.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository interfaces to their implementations.
 *
 * TokenStore and DataStoreCookieJar are already @Singleton with @Inject constructors,
 * so Hilt constructs them automatically — no explicit @Provides needed.
 * AuthRepositoryImpl is similarly @Singleton with @Inject constructor; this module
 * provides the @Binds binding so injection sites can use the AuthRepository interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
