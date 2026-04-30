package com.dmdbrands.gurus.weight.core.shared.utilities

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Utility for obtaining the current FCM registration token from the Firebase SDK.
 *
 * Use this when the app does not have the token in local storage (e.g. after migrating
 * from an older version that did not persist the FCM token). The token is held by
 * Google Play Services / Firebase for the app instance; this fetches it on demand.
 */
object FcmTokenUtil {

  /**
   * Fetches the current FCM registration token from Firebase.
   *
   * @return The current token string, or empty string if the task fails or is cancelled.
   */
  suspend fun getCurrentToken(): String = suspendCoroutine { cont ->
    FirebaseMessaging.getInstance().token
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          cont.resume(task.result ?: "")
        } else {
          cont.resumeWithException(task.exception ?: Exception("FCM getToken failed"))
        }
      }
  }
}
