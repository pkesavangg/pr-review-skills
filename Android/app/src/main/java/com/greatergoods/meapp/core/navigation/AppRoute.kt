package com.greatergoods.meapp.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed class AppRoute : NavKey {
    @Serializable
    sealed class Init : AppRoute() {
        @Serializable
        data object SampleScreen : Init()
    }

    @Serializable
    sealed class Main : AppRoute() {
        @Serializable
        data object Feeds : Main()

        @Serializable
        data object MyScales : Main()

        sealed class DeviceDetail : Main() {
            @Serializable
            data object Overview : DeviceDetail()

            @Serializable
            data object Settings : DeviceDetail()
        }
    }

    @Serializable
    sealed class Product : AppRoute() {
        @Serializable
        data object ProductList : Product()

        @Serializable
        data class ProductDetail(val id: String) : Product()
    }

    @Serializable
    sealed class Home : AppRoute() {
        @Serializable
        data object HomeScreen : Home()
    }
}


sealed interface TopLevelRoute {
    val icon: ImageVector
    val route: AppRoute
}

object InitRoute : TopLevelRoute {
    override val icon = Icons.Default.Home
    override val route = AppRoute.Init.SampleScreen
}

object MainRoute : TopLevelRoute {
    override val icon = Icons.Default.Face
    override val route = AppRoute.Main.Feeds
}

object ProductRoute : TopLevelRoute {
    override val icon = Icons.Default.PlayArrow
    override val route = AppRoute.Product.ProductList
}

val TOP_LEVEL_ROUTES = listOf(InitRoute, MainRoute, ProductRoute)
