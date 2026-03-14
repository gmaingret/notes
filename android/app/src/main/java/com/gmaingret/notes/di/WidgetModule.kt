package com.gmaingret.notes.di

import android.content.Context
import com.gmaingret.notes.widget.WidgetStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides widget-scoped dependencies.
 *
 * WidgetStateStore uses a manual singleton pattern (not @Inject constructor)
 * because it requires a Context and an Aead that are constructed lazily.
 * This module bridges the manual singleton to Hilt's dependency graph so
 * WidgetConfigViewModel can receive it via @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideWidgetStateStore(
        @ApplicationContext context: Context
    ): WidgetStateStore = WidgetStateStore.getInstance(context)
}
