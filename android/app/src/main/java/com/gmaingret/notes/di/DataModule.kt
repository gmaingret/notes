package com.gmaingret.notes.di

import com.gmaingret.notes.data.repository.AttachmentRepositoryImpl
import com.gmaingret.notes.data.repository.AuthRepositoryImpl
import com.gmaingret.notes.data.repository.BookmarkRepositoryImpl
import com.gmaingret.notes.data.repository.BulletRepositoryImpl
import com.gmaingret.notes.data.repository.DocumentRepositoryImpl
import com.gmaingret.notes.data.repository.SearchRepositoryImpl
import com.gmaingret.notes.data.repository.TagRepositoryImpl
import com.gmaingret.notes.data.repository.VoiceRepositoryImpl
import com.gmaingret.notes.domain.repository.AttachmentRepository
import com.gmaingret.notes.domain.repository.AuthRepository
import com.gmaingret.notes.domain.repository.BookmarkRepository
import com.gmaingret.notes.domain.repository.BulletRepository
import com.gmaingret.notes.domain.repository.DocumentRepository
import com.gmaingret.notes.domain.repository.SearchRepository
import com.gmaingret.notes.domain.repository.TagRepository
import com.gmaingret.notes.domain.repository.VoiceRepository
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

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindBulletRepository(impl: BulletRepositoryImpl): BulletRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(impl: VoiceRepositoryImpl): VoiceRepository
}
