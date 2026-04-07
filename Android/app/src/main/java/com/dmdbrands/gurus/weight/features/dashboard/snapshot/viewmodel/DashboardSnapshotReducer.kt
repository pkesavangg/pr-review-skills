package com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit

data class SnapshotChartData(
    val label: String = "",
    val secondaryLabel: String = "",
    val yStep: Double? = null,
    val yMin: Double? = null,
    val yMax: Double? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val hasPercentile: Boolean = false,
)

@Stable
data class DashboardSnapshotState(
    val isLoading: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val weight: SnapshotChartData = SnapshotChartData(),
    val bp: SnapshotChartData = SnapshotChartData(),
    val baby: Map<String, SnapshotChartData> = emptyMap(),
) : IReducer.State

sealed interface DashboardSnapshotIntent : IReducer.Intent {
    data class SetLoading(val isLoading: Boolean) : DashboardSnapshotIntent
    data class SetWeightUnit(val unit: WeightUnit) : DashboardSnapshotIntent
    data class SetWeightChart(val data: SnapshotChartData) : DashboardSnapshotIntent
    data class SetBpChart(val data: SnapshotChartData) : DashboardSnapshotIntent
    data class SetBabyChart(val profileId: String, val data: SnapshotChartData) : DashboardSnapshotIntent
    data class OnCardTap(val product: ProductSelection) : DashboardSnapshotIntent
}

class DashboardSnapshotReducer : IReducer<DashboardSnapshotState, DashboardSnapshotIntent> {
    override fun reduce(
        state: DashboardSnapshotState,
        intent: DashboardSnapshotIntent,
    ): DashboardSnapshotState? = when (intent) {
        is DashboardSnapshotIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
        is DashboardSnapshotIntent.SetWeightUnit -> state.copy(weightUnit = intent.unit)
        is DashboardSnapshotIntent.SetWeightChart -> state.copy(weight = intent.data)
        is DashboardSnapshotIntent.SetBpChart -> state.copy(bp = intent.data)
        is DashboardSnapshotIntent.SetBabyChart -> state.copy(baby = state.baby + (intent.profileId to intent.data))
        else -> null
    }
}
