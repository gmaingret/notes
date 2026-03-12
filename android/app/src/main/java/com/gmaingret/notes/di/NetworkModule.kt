package com.gmaingret.notes.di

import com.gmaingret.notes.data.api.AttachmentApi
import com.gmaingret.notes.data.api.AuthApi
import com.gmaingret.notes.data.api.BulletApi
import com.gmaingret.notes.data.api.BookmarkApi
import com.gmaingret.notes.data.api.DocumentApi
import com.gmaingret.notes.data.api.AuthInterceptor
import com.gmaingret.notes.data.api.SearchApi
import com.gmaingret.notes.data.api.TokenAuthenticator
import com.gmaingret.notes.data.local.DataStoreCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.google.gson.GsonBuilder
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing the network layer: OkHttpClient, Retrofit, AuthApi.
 *
 * Key wiring decisions (all locked in STATE.md):
 * - Base URL: hardcoded to https://notes.gregorymaingret.fr/ (no BuildConfig override)
 * - Timeouts: 15 seconds for connect/read/write
 * - retryOnConnectionFailure: false — each screen handles its own retry logic
 * - Circular dependency resolution: TokenAuthenticator uses dagger.Lazy<AuthApi>
 *   so Hilt can construct OkHttpClient before AuthApi is instantiated
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        cookieJar: DataStoreCookieJar
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://notes.gregorymaingret.fr/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideDocumentApi(retrofit: Retrofit): DocumentApi =
        retrofit.create(DocumentApi::class.java)

    @Provides
    @Singleton
    fun provideBulletApi(retrofit: Retrofit): BulletApi =
        retrofit.create(BulletApi::class.java)

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi =
        retrofit.create(SearchApi::class.java)

    @Provides
    @Singleton
    fun provideBookmarkApi(retrofit: Retrofit): BookmarkApi =
        retrofit.create(BookmarkApi::class.java)

    @Provides
    @Singleton
    fun provideAttachmentApi(retrofit: Retrofit): AttachmentApi =
        retrofit.create(AttachmentApi::class.java)
}
