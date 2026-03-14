package com.gmaingret.notes.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.gmaingret.notes.domain.usecase.DeleteBulletUseCase
import dagger.hilt.android.EntryPointAccessors
import retrofit2.HttpException

/**
 * ActionCallback that deletes a bullet from the widget using an optimistic update pattern.
 *
 * On tap:
 * 1. Removes the bullet from the local cache immediately (optimistic).
 * 2. Pushes the updated state to Glance so the widget recomposes instantly.
 * 3. Calls the server API to delete the bullet.
 * 4a. On success: nothing more to do — widget already shows the updated state.
 * 4b. On network/server failure: restores the original bullet list and re-pushes.
 * 4c. On auth failure (401): sets SESSION_EXPIRED display state.
 *
 * The testable core logic is in [performDelete] to allow pure JVM unit testing
 * without Robolectric (Toast and pushStateToGlance are the only Android-framework
 * calls and they remain in onAction).
 */
class DeleteBulletActionCallback : ActionCallback {

    companion object {
        /** ActionParameters key for passing the bullet id to this callback. */
        val BULLET_ID_PARAM = ActionParameters.Key<String>("bullet_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val bulletId = parameters[BULLET_ID_PARAM] ?: return

        val store = WidgetStateStore.getInstance(context)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        val toastMessage = performDelete(
            bulletId = bulletId,
            store = store,
            deleteBulletUseCase = entryPoint.deleteBulletUseCase()
        )

        NotesWidget.pushStateToGlance(context)

        if (toastMessage != null) {
            Toast.makeText(context.applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Core delete logic extracted for unit testability.
 *
 * Returns a toast message string if one should be shown to the user, or null on success.
 *
 * @param bulletId The id of the bullet to delete. Null means no-op (returns null).
 * @param store    The WidgetStateStore to read from and write to.
 * @param deleteBulletUseCase Use case that calls the server delete API.
 */
internal suspend fun performDelete(
    bulletId: String?,
    store: WidgetStateStore,
    deleteBulletUseCase: DeleteBulletUseCase
): String? {
    if (bulletId == null) return null

    val originalBullets = store.getBullets()
    val filtered = originalBullets.filter { it.id != bulletId }

    // Optimistic update
    store.saveBullets(filtered)
    store.saveDisplayState(if (filtered.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT)

    // API call
    val result = deleteBulletUseCase.invoke(bulletId)

    return if (result.isSuccess) {
        null
    } else {
        val ex = result.exceptionOrNull()
        if (isAuthError(ex)) {
            // Auth failure: show session expired state
            store.saveBullets(originalBullets)
            store.saveDisplayState(DisplayState.SESSION_EXPIRED)
            "Session expired"
        } else {
            // Generic failure: rollback to original list
            store.saveBullets(originalBullets)
            store.saveDisplayState(DisplayState.CONTENT)
            "Couldn't delete"
        }
    }
}

/**
 * Returns true if the exception indicates an authentication failure (HTTP 401 or
 * messages containing "401", "unauthorized", or "unauthenticated").
 */
private fun isAuthError(e: Throwable?): Boolean {
    if (e == null) return false
    if (e is HttpException && e.code() == 401) return true
    val msg = e.message?.lowercase() ?: ""
    return msg.contains("401") || msg.contains("unauthorized") || msg.contains("unauthenticated")
}
