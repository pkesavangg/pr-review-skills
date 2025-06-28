package com.greatergoods.meapp.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.dashboard.components.DashboardMetrics
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardIntent
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardViewModel
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
    val viewmodel: DashboardViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    BackHandler {
        viewmodel.dialogQueueService.showDialog(
            DialogModel.Confirm(
                title = "Exit Dashboard",
                message = "Are you sure you want to exit the dashboard?",
                onConfirm = {
                    scope.launch {
                        activity?.finish()
                    }
                },
            ),
        )
    }
    DashboardScreenContent(state, viewmodel::handleIntent)
}

@Composable
private fun DashboardScreenContent(state: DashboardState, handleIntent: (DashboardIntent) -> Unit) {
    val scrollState = rememberScrollState()
    var metricData: List<PeriodBodyScaleSummary> by remember {
        mutableStateOf(listOf())
    }
    var selectedMetric: Metric? by remember {
        mutableStateOf(null)
    }
    AppScaffold(title = null) {

        Column(modifier = Modifier.verticalScroll(scrollState)) {
            HistoryGraph(state, selectedMetric) {
                metricData = it
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
            DashboardMetrics(metricData = metricData, selectedMetric = selectedMetric) {
                selectedMetric = it
            }
        }
    }
}

@PreviewTheme
@Composable
private fun DashboardPreview() {
    MeAppTheme {
        DashboardScreenContent(
            state = DashboardState(),
            handleIntent = {},
        )
    }
}
