package com.greatergoods.meapp.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.history.components.HistoryEmptyState
import com.greatergoods.meapp.features.history.components.HistoryItemModel
import com.greatergoods.meapp.features.history.components.HistoryList
import com.greatergoods.meapp.features.history.strings.HistoryScreenStrings
import com.greatergoods.meapp.features.history.viewmodel.HistoryIntent
import com.greatergoods.meapp.features.history.viewmodel.HistoryState
import com.greatergoods.meapp.features.history.viewmodel.HistoryViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun HistoryScreen() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val isRefreshing = state.isLoading
    HistoryScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.loadHistory() },
        handleIntent = viewModel::handleIntent,
    )
}

@Composable
fun HistoryScreenContent(
    state: HistoryState,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    handleIntent: (HistoryIntent) -> Unit,
) {
    AppScaffold(
        title = HistoryScreenStrings.Title,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            when {
                state.isLoading && !isRefreshing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.historyItems.isEmpty() -> {
                    HistoryEmptyState(onRetry = { handleIntent(HistoryIntent.Retry) })
                }
                else -> {
                    val items = state.historyItems.filterIsInstance<HistoryItemModel>()
                    HistoryList(
                        items = items,
                        onItemClick = { item ->
                            println("Clicked: ${item.month}")
                        },
                    )
                }
            }
        }
    }
}

@PreviewTheme
@Composable
fun HistoryScreenPreview() {
    MeAppTheme {
        val sampleItems = listOf(
            HistoryItemModel("Dec 2022", "5 Entries", "148.6 lbs", "-1.4 lbs"),
            HistoryItemModel("Nov 2022", "6 Entries", "150.0 lbs", "+0.2 lbs"),
            HistoryItemModel("Oct 2022", "4 Entries", "140.0 lbs", "+0.2 lbs"),
        )
        HistoryScreenContent(
            state = HistoryState(historyItems = sampleItems),
            isRefreshing = false,
            onRefresh = {},
            handleIntent = {},
        )
    }
}
