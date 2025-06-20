package com.greatergoods.meapp.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardIntent
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.launch
import android.app.Activity

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
