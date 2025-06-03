package com.greatergoods.meapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.domain.repository.ILogRepository
import com.greatergoods.meapp.core.logging.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Intent
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
    lateinit var logRepository: ILogRepository

    /**
     * Called when the activity is starting. Sets up Compose content and handles navigation intents.
     * @param savedInstanceState The previously saved instance state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Add test logs
        lifecycleScope.launch {
            try {
                // Test different log types
                logRepository.log("MainActivity", "App started", "i", null)
                logRepository.log("MainActivity", "Test warning", "w", "Test warning data")
                logRepository.log("MainActivity", "Test error", "e", "Test error data")
                
                // Log the current session ID
                logRepository.getSessionId()?.let { sessionId ->
                    AppLog.d("MainActivity", "Current session ID: $sessionId")
                }
            } catch (e: Exception) {
                AppLog.e("MainActivity", "Failed to add test logs", e.toString())
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
