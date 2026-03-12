package com.gmaingret.notes

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application class for Hilt and Coil initialization.
 *
 * Implements [SingletonImageLoader.Factory] so Coil uses the same OkHttpClient
 * as the rest of the app — including the AuthInterceptor that attaches Bearer tokens.
 * This ensures protected attachment image URLs are loaded without 401 errors.
 */
@HiltAndroidApp
class NotesApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}
