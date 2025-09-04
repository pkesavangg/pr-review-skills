package com.greatergoods.ggInAppMessaging.core.utilities

import android.util.Log

/**
 * Simple logging utility for IAM package
 */
object IAMLogger {
  private const val TAG_PREFIX = "IAM_"

  /**
   * Log info message
   */
  fun i(tag: String, message: String) {
    Log.i("$TAG_PREFIX$tag", message)
  }

  /**
   * Log debug message
   */
  fun d(tag: String, message: String) {
    Log.d("$TAG_PREFIX$tag", message)
  }

  /**
   * Log warning message
   */
  fun w(tag: String, message: String) {
    Log.w("$TAG_PREFIX$tag", message)
  }

  /**
   * Log error message
   */
  fun e(tag: String, message: String, throwable: String? = null) {
    if (throwable != null) {
      Log.e("$TAG_PREFIX$tag", message, Exception(throwable))
    } else {
      Log.e("$TAG_PREFIX$tag", message)
    }
  }

  /**
   * Log verbose message
   */
  fun v(tag: String, message: String) {
    Log.v("$TAG_PREFIX$tag", message)
  }
}
