package com.greatergoods.meapp

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.greatergoods.meapp.app.MeApp
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.repository.AppRepository
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.data.storage.datastore.UserDataStore
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.proto.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat

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
    lateinit var eventService: IAppEventService

    /**
     * Called when the activity is starting. Sets up Compose content and handles navigation intents.
     * @param savedInstanceState The previously saved instance state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        applyInitialTheme()
        super.onCreate(savedInstanceState)

        // Configure window to handle insets properly
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        initializeSplashScreen()
        enableEdgeToEdge()
        setContent {
            MeApp()
        }
        observeThemeChanges()
        handleIntentNavigationIfNeeded(intent)
    }

    /**
     * Called when a new intent is delivered to the activity.
     * @param intent The new intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentNavigationIfNeeded(intent)
    }

    /**
     * Handles navigation based on the provided intent, if a destination is specified.
     * @param intent The intent to check for navigation extras.
     */
    private fun handleIntentNavigationIfNeeded(intent: Intent?) {
        lifecycleScope.launch {
            val destination = intent?.getStringExtra("destination")
            AppLog.i("MainActivity", "Destination: $destination")
            when (destination) {
                // TODO "Add navigation routes here"
            }
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
                    .map { it.toNightMode() }
                    .distinctUntilChanged()
                    .collect { mode ->
                        applyNightMode(mode)
                    }
            }
        }
    }

    private fun applyNightMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyInitialTheme() {
        appRepository =
            AppRepository(
                UserDataStore(applicationContext),
                FcmDataStore(applicationContext),
            )

        val initialMode =
            runBlocking {
                appRepository.themeModeFlow.first() // blocks briefly, safe here
            }
        applyNightMode(initialMode.toNightMode())
    }

    private fun ThemeMode.toNightMode(): Int =
        when (this) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
}
