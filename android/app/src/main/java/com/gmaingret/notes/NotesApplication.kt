package com.gmaingret.notes

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application class for Hilt, Coil, and WorkManager initialization.
 *
 * Implements [SingletonImageLoader.Factory] so Coil uses the same OkHttpClient
 * as the rest of the app — including the AuthInterceptor that attaches Bearer tokens.
 * This ensures protected attachment image URLs are loaded without 401 errors.
 *
 * Implements [Configuration.Provider] to wire [HiltWorkerFactory] into WorkManager.
 * This is required when using @HiltWorker — the default WorkManager auto-initializer
 * must be disabled in AndroidManifest.xml and replaced with this manual configuration
 * so that Hilt can inject dependencies into CoroutineWorker subclasses.
 */
@HiltAndroidApp
class NotesApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}
