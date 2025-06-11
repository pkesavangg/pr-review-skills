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
     * Authentication-related navigation routes.
     */
    @Serializable
    sealed class Auth : AppRoute() {

        @Serializable
        data class LoginScreen(val hasAccounts: Boolean) : Auth()
    }

    @Serializable
    data object Dashboard : AppRoute()
}
