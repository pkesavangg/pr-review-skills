package com.greatergoods.meapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.core.logging.LogManager
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
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

    @Inject
    lateinit var logManager: LogManager

    /**
     * Called when the activity is starting. Sets up Compose content and handles navigation intents.
     * @param savedInstanceState The previously saved instance state, if any.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splashScreen
        // Clean up logs older than 5 days
        lifecycleScope.launch {
            try {
                logManager.cleanupOldLogs(5)
                AppLog.i("MainActivity", "Cleaning up old logs")
            } catch (e: Exception) {
                AppLog.e("MainActivity", "Failed to cleanup old logs", e.toString())
            }
        }

        setContent {
            MeApp()

        }
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
                "productDetail" -> {
                    eventService.addTopLevelRoute(AppRoute.Product.ProductList)
                    eventService.navigateTo(AppRoute.Product.ProductList)
                }
            }
        }
    }
}
