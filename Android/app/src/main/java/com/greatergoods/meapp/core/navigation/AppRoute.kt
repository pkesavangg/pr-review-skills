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
     * Main navigation routes for the app.
     */
    @Serializable
    sealed class Main : AppRoute() {
        @Serializable
        data object Dashboard : Main()

        @Serializable
        data object Entry : Main()

        @Serializable
        data object History : Main()

        @Serializable
        data object Settings : Main()

        @Serializable
        data object AppSync : Main()
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
