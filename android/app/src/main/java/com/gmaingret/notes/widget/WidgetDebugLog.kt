package com.gmaingret.notes.widget

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent file logger for widget operations.
 *
 * Writes to app-internal storage (Context.filesDir/widget_debug.log).
 * Keeps the last 500 lines to avoid unbounded growth.
 * Survives process death — can be pulled via `adb pull` whenever convenient.
 *
 * Pull with: adb shell run-as com.gmaingret.notes cat files/widget_debug.log
 */
internal object WidgetDebugLog {

    private const val FILE_NAME = "widget_debug.log"
    private const val MAX_LINES = 500
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp $tag: $message\n"
            Log.d(tag, message) // still log to logcat too

            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line)

            // Trim if too large
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e("WidgetDebugLog", "Failed to write log", e)
        }
    }

    fun warn(context: Context, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp WARN $tag: $message\n"
            Log.w(tag, message)

            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line)
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e("WidgetDebugLog", "Failed to write log", e)
        }
    }

    fun error(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = dateFormat.format(Date())
            val errDetail = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: ""
            val line = "$timestamp ERROR $tag: $message $errDetail\n"
            Log.e(tag, message, throwable)

            val file = File(context.filesDir, FILE_NAME)
            file.appendText(line)
            trimIfNeeded(file)
        } catch (e: Exception) {
            Log.e("WidgetDebugLog", "Failed to write log", e)
        }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists()) return
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }
}
