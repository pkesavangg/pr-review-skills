package com.greatergoods.libs.appsync

import android.util.Log

/**
 * Logger bridge for the appsync library.
 *
 * The library has no dependency on the app module, so it cannot reach `AppLog`
 * directly. The host app installs a [Sink] at startup that forwards records into
 * the app's persistent log (so AppSync diagnostics show up in user-submitted
 * debug bundles). Until a sink is installed, calls fall back to logcat only.
 */
object AppSyncLogger {
  interface Sink {
    fun d(tag: String, message: String, data: String? = null)
    fun i(tag: String, message: String, data: String? = null)
    fun w(tag: String, message: String, data: String? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
  }

  @Volatile
  private var sink: Sink? = null

  fun install(sink: Sink) {
    this.sink = sink
  }

  fun d(tag: String, message: String, data: String? = null) {
    Log.d(tag, message)
    sink?.d(tag, message, data)
  }

  fun i(tag: String, message: String, data: String? = null) {
    Log.i(tag, message)
    sink?.i(tag, message, data)
  }

  fun w(tag: String, message: String, data: String? = null) {
    Log.w(tag, message)
    sink?.w(tag, message, data)
  }

  fun e(tag: String, message: String, throwable: Throwable? = null) {
    if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    sink?.e(tag, message, throwable)
  }
}
