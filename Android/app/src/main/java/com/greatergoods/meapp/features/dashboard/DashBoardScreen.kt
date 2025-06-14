package com.greatergoods.meapp.features.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.MainBottomNav
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph

@Composable
fun DashBoardScreen() {
    Scaffold(
        bottomBar = {
            MainBottomNav()
        },
    ) {
        Column(modifier = Modifier.padding(it)) {
            HistoryGraph()
        }
    }
}
