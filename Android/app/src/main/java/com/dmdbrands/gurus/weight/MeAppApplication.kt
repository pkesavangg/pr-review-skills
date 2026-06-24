package com.dmdbrands.gurus.weight

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dmdbrands.gurus.weight.core.di.AppEntryPoint
import com.dmdbrands.gurus.weight.core.service.pushNotification.NotificationManager
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.app.Application

/**
 * Application class for MeApp.
 * Handles application-level initialization and configuration.
 */
@HiltAndroidApp
class MeAppApplication : Application(), Configuration.Provider {

  @Inject
  lateinit var notificationManager: NotificationManager

  @Inject
  lateinit var workerFactory: HiltWorkerFactory



  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

    override fun onCreate() {
    super.onCreate()

    // Disable analytics for debug/staging builds
    FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(BuildConfig.ENABLE_ANALYTICS)

    // Initialize services needed for the app
    initService()
    notificationManager.createChannels()
  }

  /**
   * Initialize services needed for the app at app startup
   */
  private fun initService() {
    val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
    val initializer = entryPoint.appInitializer()
    initializer.initialize()
  }
}
