package com.greatergoods.meapp.core.navigation

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
        data object Loading : Init()
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

    /**
     * Authentication-related navigation routes.
     */
    @Serializable
    sealed class Auth : AppRoute() {
        @Serializable
        data object AuthScreen : Auth()

        @Serializable
        data object LoginScreen : Auth()

        @Serializable
        data object UserListScreen : Auth()
    }
}
