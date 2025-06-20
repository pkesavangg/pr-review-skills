package com.greatergoods.meapp.features.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardIntent
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardViewModel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun DashboardScreen() {
    val viewmodel: DashboardViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    DashboardScreenContent(state, viewmodel::handleIntent)
}

@Composable
private fun DashboardScreenContent(state: DashboardState, handleIntent: (DashboardIntent) -> Unit) {
    AppScaffold(title = null) {
        Column {
            HistoryGraph(state)
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
