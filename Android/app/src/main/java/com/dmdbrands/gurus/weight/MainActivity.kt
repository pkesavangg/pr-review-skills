package com.dmdbrands.gurus.weight

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dmdbrands.gurus.weight.app.MeApp
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.service.WifiScaleService
import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.repository.AppRepository
import com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.greatergoods.blewrapper.GGBLEService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Main entry point for the MeApp application.
 * Handles UI composition and top-level navigation, including intent-based navigation.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  lateinit var appRepository: IAppRepository

  /**
   * Injected service for handling app-level navigation events.
   */
  @Inject
  lateinit var eventService: IAppNavigationService

  @Inject
  lateinit var healthConnectService: IHealthConnectService

  @Inject
  lateinit var gGBLEService: GGBLEService

  @Inject
  lateinit var wifiScaleService: WifiScaleService

  @Inject
  lateinit var appReviewManager: IAppReviewManager

  @Inject
  lateinit var customTabManager: ICustomTabManager

  /**
   * Called when the activity is starting. Sets up Compose content and handles navigation intents.
   * @param savedInstanceState The previously saved instance state, if any.
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    applyInitialTheme()
    initializeSplashScreen()
    super.onCreate(savedInstanceState)

    // Enable edge-to-edge display and proper system bar handling
    WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge()

    // Initialize services
    healthConnectService.initializeHealthConnect(this)
    gGBLEService.createInstance(this)
    wifiScaleService.initialise(this)

    setContent {
      MeApp()
    }

    observeThemeChanges()
    // Handle initial intent
    handleHealthConnectIntent(intent)

    // Pre-warm Custom Tabs service so support links and OAuth open in browser without delay
    lifecycleScope.launch {
      customTabManager.bindService()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
      customTabManager.unbind()
    }
  }

  /**
   * Called when a new intent is delivered to the activity.
   * @param intent The new intent.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent) // Save the new intent
    handleHealthConnectIntent(intent)
    if (intent.action == "ACTION_HANDLE_NOTIFICATION") {
      AppLog.d("MainActivity", "Notification tapped. Destination: ${intent.getStringExtra("destination")}")
      lifecycleScope.launch {
        AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_TAPPED)
      }
      // Emit event to shared flow or handle navigation
    }
  }

  /**
   * Handles Health Connect related intents, including privacy policy.
   * @param intent The intent to handle
   */
  private fun handleHealthConnectIntent(intent: Intent?) {
    if (intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
      intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
    ) {
      healthConnectService.handleOnNewIntent(intent)
    }
  }

  /**
   * Initialize the splash screen exit animation.
   * Only available on Android 12 and above.
   */
  private fun initializeSplashScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
        // Example: fade out
        splashScreenViewProvider
          .animate()
          .alpha(0f)
          .setDuration(300L)
          .withEndAction { splashScreenViewProvider.remove() }
          .start()
      }
    }
  }

  private fun observeThemeChanges() {
    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        appRepository.themeModeFlow
          .distinctUntilChanged()
          .collect { mode ->
            applyNightMode(mode)
          }
      }
    }
  }

  private fun applyNightMode(mode: ThemeMode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val ui = getSystemService(UI_MODE_SERVICE) as UiModeManager
      ui.setApplicationNightMode(mode.toUiMode())
    } else {
      AppCompatDelegate.setDefaultNightMode(mode.toAppCompatNightMode())
    }
  }

  private fun applyInitialTheme() {
    appRepository =
      AppRepository(
        UserDataStore(applicationContext),
        FcmDataStore(applicationContext),
      )
    val initialMode = runBlocking {
      appRepository.themeModeFlow.first()
    }
    applyNightMode(initialMode)
  }

  private fun ThemeMode.toAppCompatNightMode(): Int =
    when (this) {
      ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

  private fun ThemeMode.toUiMode(): Int =
    when (this) {
      ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
      ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
      else -> UiModeManager.MODE_NIGHT_AUTO
    }
}
