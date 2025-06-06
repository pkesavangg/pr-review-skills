package com.greatergoods.meapp.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Defines all navigation routes for the app using sealed classes for type safety and serialization.
 */
sealed class AppRoute : NavKey {
    /**
     * Initial navigation routes (e.g., splash, onboarding).
     */
    @Serializable
    sealed class Init : AppRoute() {
        @Serializable
        data object SampleScreen : Init()
    }

    /**
     * Main navigation routes for primary app sections.
     */
    @Serializable
    sealed class Main : AppRoute() {
        @Serializable
        data object Feeds : Main()

        @Serializable
        data object MyScales : Main()

        @Serializable
        data object Entries : Main()

        @Serializable
        data object AddEntry : Main()

        /**
         * Nested routes for device details.
         */
        sealed class DeviceDetail : Main() {
            @Serializable
            data object Overview : DeviceDetail()

            @Serializable
            data object Settings : DeviceDetail()
        }
    }

    /**
     * Product-related navigation routes.
     */
    @Serializable
    sealed class Product : AppRoute() {
        @Serializable
        data object ProductList : Product()

        @Serializable
        data class ProductDetail(val id: String) : Product()
    }

    /**
     * Home navigation route.
     */
    @Serializable
    sealed class Home : AppRoute() {
        @Serializable
        data object HomeScreen : Home()
    }
}

/**
 * Interface for top-level navigation routes, each with an icon and associated AppRoute.
 */
sealed interface TopLevelRoute {
    val icon: ImageVector
    val route: AppRoute
}

/**
 * Top-level route for the initial screen.
 */
object InitRoute : TopLevelRoute {
    override val icon = Icons.Default.Home
    override val route = AppRoute.Init.SampleScreen
}

/**
 * Top-level route for the main section.
 */
object MainRoute : TopLevelRoute {
    override val icon = Icons.Default.Face
    override val route = AppRoute.Main.Feeds
}

/**
 * Top-level route for the product section.
 */
object ProductRoute : TopLevelRoute {
    override val icon = Icons.Default.PlayArrow
    override val route = AppRoute.Product.ProductList
}

/**
 * List of all top-level routes in the app.
 */
val TOP_LEVEL_ROUTES = listOf(InitRoute, MainRoute, ProductRoute)
