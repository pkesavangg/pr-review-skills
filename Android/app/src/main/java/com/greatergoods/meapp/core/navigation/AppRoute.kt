package com.greatergoods.meapp.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed class AppRoute : NavKey {
    @Serializable
    sealed class Init : AppRoute() {
        @Serializable
        data object Login : Init()

        @Serializable
        data object Overview : Init()

        @Serializable
        data object Feeds : Init()

        @Serializable
        data object MyScales : Init()
    }

    @Serializable
    data object Feeds : AppRoute()

    @Serializable
    data object MyScales : AppRoute()

    @Serializable
    data object ScaleSetup : AppRoute()

    @Serializable
    data object StatsModal : AppRoute()

    @Serializable
    data object MetricModal : AppRoute()

    @Serializable
    data object HistoryDetail : AppRoute()

    @Serializable
    data object Height : AppRoute()

    @Serializable
    data object PairedDevice : AppRoute()

    @Serializable
    data class AppDetail(val appType: String) : AppRoute()

    @Serializable
    data object History : AppRoute()

    @Serializable
    data object MonthDetail : AppRoute()

    @Serializable
    data object EntryDetail : AppRoute()

    @Serializable
    data object Scale : AppRoute()

    @Serializable
    data object ScaleDetails : AppRoute()

    sealed class DeviceDetailRoute : AppRoute() {
        object Overview : DeviceDetailRoute()
        object Settings : DeviceDetailRoute()
        object Logs : DeviceDetailRoute()
    }
}