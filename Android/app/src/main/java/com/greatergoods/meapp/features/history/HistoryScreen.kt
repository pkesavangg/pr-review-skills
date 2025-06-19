package com.greatergoods.meapp.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.history.components.HistoryEmptyState
import com.greatergoods.meapp.features.history.components.HistoryList
import com.greatergoods.meapp.features.history.strings.HistoryScreenStrings
import com.greatergoods.meapp.features.history.viewmodel.HistoryIntent
import com.greatergoods.meapp.features.history.viewmodel.HistoryState
import com.greatergoods.meapp.features.history.viewmodel.HistoryViewModel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun HistoryScreen() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val isRefreshing = state.isLoading
    HistoryScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.handleIntent(HistoryIntent.Refresh)
        },
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
        isRefreshing = state.isLoading,
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
                    HistoryList(
                        items = state.historyItems,
                        onItemClick = { item ->
                            println("Clicked: ${item.entryTimestamp}")
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
        val sampleItems =
            listOf(
                HistoryMonth(
                    entryTimestamp = "2023-10",
                    avgWeight = 70.5,
                    entryCount = 15,
                    change = -1.2,
                ),
                HistoryMonth(
                    entryTimestamp = "2023-09",
                    avgWeight = 71.0,
                    entryCount = 12,
                    change = 0.5,
                ),
                HistoryMonth(
                    entryTimestamp = "2023-08",
                    avgWeight = 72.0,
                    entryCount = 10,
                    change = -0.8,
                ),
            )
        HistoryScreenContent(
            state = HistoryState(historyItems = sampleItems),
            isRefreshing = false,
            onRefresh = {},
            handleIntent = {},
        )
    }
}
