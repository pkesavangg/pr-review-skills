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
     * Authentication-related navigation routes.
     */
    @Serializable
    sealed class Auth : AppRoute(), PublicRoute {

        @Serializable
        data object Landing : Auth()

        @Serializable
        data class Login(val email: String? = null) : Auth()

        @Serializable
        data object Signup : Auth()

        @Serializable
        data object MultiAccountLanding : Auth()
    }

    /**
     * Profile-related navigation routes.
     */
    @Serializable
    sealed class AccountSettings : AppRoute() {
        @Serializable
        data object ChangePassword : AccountSettings()

        @Serializable
        data object Profile : AccountSettings()

        @Serializable
        data object MyAccounts : AccountSettings()

        @Serializable
        data object Weightless : AccountSettings()

        @Serializable
        data object AddEditScales : AccountSettings()

        @Serializable
        data object ChooseScale : AccountSettings()
    }

    /**
     * Help-related navigation routes.
     */
    @Serializable
    sealed class Help : AppRoute() {
        @Serializable
        data object HelpScreen : Help()
    }
}


