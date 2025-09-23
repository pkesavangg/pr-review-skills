package com.dmdbrands.gurus.weight.features.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.history.components.HistoryEmptyState
import com.dmdbrands.gurus.weight.features.history.components.HistoryList
import com.dmdbrands.gurus.weight.features.history.strings.HistoryScreenStrings
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryIntent
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryState
import com.dmdbrands.gurus.weight.features.history.viewmodel.HistoryViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch

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
  val navBackStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()
  AppScaffold(
    title = HistoryScreenStrings.Title,
    actions = {
      AppIcon(
        id = AppIcons.Default.Export,
        contentDescription = HistoryScreenStrings.ExportDataDescription,
        type = AppIconType.Primary,
        modifier = Modifier.padding(end = spacing.sm),
        enabled = state.historyItems.isNotEmpty(),
      ) {
        handleIntent(HistoryIntent.Export)
      }
    },
    isRefreshing = state.isLoading,
    onRefresh = onRefresh,
  ) { modifier ->
    Box(modifier = modifier.fillMaxSize()) {
      when {
        state.isLoading && !isRefreshing -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.historyItems.isEmpty() -> {
          HistoryEmptyState(
            onRetry = { handleIntent(HistoryIntent.Retry) },
            onConnectScale = { handleIntent(HistoryIntent.OnConnectScale) },
          )
        }

        else -> {
          HistoryList(
            items = state.historyItems,
            onItemClick = { item ->
              if (item.entryTimestamp != null) {
                coroutineScope.launch {
                  navBackStack.addRoute(AppRoute.History.MonthDetails(item.entryTimestamp))
                }
              }
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
