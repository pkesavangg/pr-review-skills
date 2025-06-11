package com.greatergoods.meapp.features.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.ui.shared.components.AppButton
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen() {
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(2000)
            dialogQueueViewModel.dismissLoader()
            isLoading = false
        }
    }
    Column {
        HistoryGraph()
        Spacer(modifier = Modifier.height(16.dp))
        AppButton(
            "Show Loader",
            onClick = {
                dialogQueueViewModel.showLoader("Loading...")
                isLoading = true
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
