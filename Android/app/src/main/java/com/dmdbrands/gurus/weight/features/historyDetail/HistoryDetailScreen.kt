package com.dmdbrands.gurus.weight.features.historyDetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.historyDetail.components.HistoryDetailList
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailIntent
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailState
import com.dmdbrands.gurus.weight.features.historyDetail.viewmodel.HistoryDetailViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import kotlinx.coroutines.launch

@Composable
fun HistoryDetailScreen(monthKey: String) {
    val viewModel: HistoryDetailViewModel = hiltViewModel<HistoryDetailViewModel, HistoryDetailViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(monthKey)
        },
    )
    val state by viewModel.state.collectAsState()
    val isRefreshing = state.isLoading

    HistoryDetailScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.handleIntent(HistoryDetailIntent.Refresh) },
        handleIntent = viewModel::handleIntent,
    )
}

@Composable
fun HistoryDetailScreenContent(
    state: HistoryDetailState,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    handleIntent: (HistoryDetailIntent) -> Unit,
) {
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()
    AppScaffold(
        title = state.month,
        isRefreshing = state.isLoading,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                scope.launch {
                    backStack.removeLast()
                }
            }
        },
        onRefresh = onRefresh,
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            when {
                state.isLoading && !isRefreshing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    HistoryDetailList(
                        historyDetails = state.historyItems,
                        onItemDelete = {
                            handleIntent(HistoryDetailIntent.DeleteEntry(it))
                        },
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun HistoryDetailScreenPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                ScaleEntry(
                    entry = EntryEntity(
                        id = 478,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-19T06:30:00.000Z",
                        serverTimestamp = "2025-06-19T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = WeightUnit.LB,
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 478,
                            weight = 50.0,
                            bodyFat = 0.0,
                            muscleMass = 0.0,
                            water = 0.0,
                            bmi = 0.0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 478,
                            bmr = 12.0,
                            metabolicAge = 0,
                            proteinPercent = 0.0,
                            pulse = 0,
                            skeletalMusclePercent = 0.0,
                            subcutaneousFatPercent = 0.0,
                            visceralFatLevel = 12.0,
                            boneMass = 0.0,
                            impedance = 0,
                        ),
                    ),
                ),
                ScaleEntry(
                    entry = EntryEntity(
                        id = 479,
                        accountId = "4SWOWDAP9t2gS50MFp9HQS",
                        entryTimestamp = "2025-06-20T06:30:00.000Z",
                        serverTimestamp = "2025-06-20T10:29:13.914Z",
                        opTimestamp = null,
                        operationType = "create",
                        deviceType = "scale",
                        deviceId = "manual",
                        attempts = 0,
                        unit = WeightUnit.KG,
                        isSynced = true,
                    ),
                    scale = ScaleEntryWithMetrics(
                        scaleEntry = BodyScaleEntryEntity(
                            id = 479,
                            weight = 70.0,
                            bodyFat = 0.0,
                            muscleMass = 0.0,
                            water = 0.0,
                            bmi = 0.0,
                            source = "manual",
                        ),
                        scaleEntryMetric = BodyScaleEntryMetricEntity(
                            id = 479,
                            bmr = 12.0,
                            metabolicAge = 0,
                            proteinPercent = 0.0,
                            pulse = 0,
                            skeletalMusclePercent = 0.0,
                            subcutaneousFatPercent = 0.0,
                            visceralFatLevel = 12.0,
                            boneMass = 0.0,
                            impedance = 0,
                        ),
                    ),
                ),
            )
        HistoryDetailScreenContent(
            state =
                HistoryDetailState(
                    month = "Dec 2022",
                    historyItems = sampleItems,
                ),
            isRefreshing = false,
            onRefresh = {},
            handleIntent = {},
        )
    }
}
