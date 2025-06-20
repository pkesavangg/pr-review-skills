package com.greatergoods.meapp.features.historyDetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.historyDetail.components.HistoryDetailItemModel
import com.greatergoods.meapp.features.historyDetail.components.HistoryDetailList
import com.greatergoods.meapp.features.historyDetail.viewmodel.HistoryDetailIntent
import com.greatergoods.meapp.features.historyDetail.viewmodel.HistoryDetailState
import com.greatergoods.meapp.features.historyDetail.viewmodel.HistoryDetailViewModel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun HistoryDetailScreen(monthKey: String) {
    val viewModel: HistoryDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val isRefreshing = state.isLoading

    HistoryDetailScreenContent(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.loadHistoryDetail() },
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
    AppScaffold(
        title = state.month,
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
    ) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            when {
                state.isLoading && !isRefreshing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    HistoryDetailList(
                        items = state.historyItems,
                        onItemClick = { item ->
                            println("Clicked: ${item.date}")
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
                HistoryDetailItemModel(
                    date = "Dec 16",
                    time = "2:10 PM",
                    weight = "149.2",
                ),
                HistoryDetailItemModel(
                    date = "Dec 10",
                    time = "2:10 PM",
                    weight = "148.7",
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
