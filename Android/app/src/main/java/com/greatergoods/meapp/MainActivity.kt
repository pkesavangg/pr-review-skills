package com.greatergoods.meapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.core.shared.utilities.AnimationUtil
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Main entry point for the MeApp application.
 * Handles UI composition and top-level navigation, including intent-based navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
        // Set up splash screen exit animation
        initializeSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MeApp() }
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
        }
    }

    /**
     * Initialize the splash screen exit animation.
     * Only available on Android 12 and above.
     */
    private fun initializeSplashScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener {
                AnimationUtil.splashScreenExitAnimation(it)
            }
        }
    }
}
