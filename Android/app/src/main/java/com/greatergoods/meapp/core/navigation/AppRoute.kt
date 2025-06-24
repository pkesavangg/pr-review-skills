package com.greatergoods.meapp.core.navigation

import androidx.navigation3.runtime.NavKey
import com.example.nav3integration.PublicRoute
import kotlinx.serialization.Serializable

/**
 * Defines all navigation routes for the app using sealed classes for type safety and serialization.
 */
sealed class AppRoute : NavKey {
    /**
     * Initial navigation routes (e.g., splash, onboarding).
     */
    @Serializable
    sealed class Init : AppRoute(), PublicRoute {
        @Serializable
        data object Loading : Init()
    }

    @Serializable
    data object Home : AppRoute()

    @Serializable
    data object App : AppRoute()

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

    @Serializable
    data class MonthDetails(
        val month: String,
    ) : AppRoute()

    /**
     * Profile-related navigation routes.
     */
    @Serializable
    sealed class Profile : AppRoute() {
        @Serializable
        data object Edit : Profile()
    }

    /**
     * Authentication-related navigation routes.
     */
    @Serializable
    sealed class Auth : AppRoute(), PublicRoute {

        @Serializable
        data object Landing : Auth()

        @Serializable
        data object Login : Auth()

        @Serializable
        data object Signup : Auth()

        @Serializable
        data object UserList : Auth()
    }

    /**
     * Profile-related navigation routes.
     */
    @Serializable
    sealed class AccountSettings : AppRoute() {
        @Serializable
        data object ChangePassword : AccountSettings()
    }
}


