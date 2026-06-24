package com.dmdbrands.gurus.weight.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.LocalActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.app.components.HomeNavHost
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.AppFab
import com.dmdbrands.gurus.weight.features.common.components.MainBottomNav
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.WeightOnlyModePopup
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.dmdbrands.gurus.weight.features.home.reducer.HomeState
import com.dmdbrands.gurus.weight.features.home.viewmodel.HomeViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

/**
 * Home screen displaying current user data, logout option, and switch account section.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  val isSnapshotMode by viewModel.productSelectionManager.isSnapshotMode.collectAsStateWithLifecycle()

  HomeScreenContent(
    state = state,
    handleIntent = viewModel::handleIntent,
    showUnreadFeedIndicator = state.showUnreadFeedIndicator,
    isSnapshotMode = isSnapshotMode,
  )
}

@Composable
fun HomeScreenContent(
  state: HomeState,
  handleIntent: (HomeIntent) -> Unit,
  showUnreadFeedIndicator: Boolean = false,
  isSnapshotMode: Boolean = false,
) {
  val topLevelBackStack = LocalNavBackStack.current
  val activity = LocalActivity.current
  LocalSoftwareKeyboardController.current
  // Observe shouldAskForReview state and launch review when true
  LaunchedEffect(state.shouldAskForReview) {
    if (state.shouldAskForReview) {
      activity?.let { handleIntent(HomeIntent.LaunchAppReview(it)) }
    }
  }

  Scaffold(
    bottomBar = {
      if (!isKeyboardOpen())
        MainBottomNav(
          showAppsync = state.showAppsync,
          showUnreadFeedIndicator = showUnreadFeedIndicator,
          isSnapshotMode = isSnapshotMode,
          onOpenAppSync = {
            handleIntent(
              HomeIntent.CheckAndRequestPermission { isEnabled ->
                if (isEnabled) {
                  // Run the scan on the ViewModel scope so it survives recomposition/disposal
                  // after the device has been idle (MOB-710).
                  activity?.let { handleIntent(HomeIntent.StartAppSyncScan(it)) }
                }
              },
            )
          },
        )
    },
    floatingActionButton = {
      if (state.showWeightOnlyModeBottomSheet && !state.isWeightOnlyModeDismissed) {
        AppFab(
          showWeightOnlyModeAlert = true,
          onClick = {
            handleIntent(HomeIntent.OpenWeightOnlyModePopup(true))
          },
        )
      }
    },
  ) {
    Surface(
      modifier =
        Modifier
          .padding(bottom = it.calculateBottomPadding())
          .background(Red),
    ) {
      HomeNavHost(
        topLevelBackStack = topLevelBackStack,
      )
    }
  }

  // Weight-only mode bottom sheet
  if (state.openWeightOnlyModePopup) {
    OpenWeightOnlyModePopup(
      onEnable = {
        handleIntent(HomeIntent.OnWeightOnlyModeEnable)
      },
      onClose = {
        handleIntent(HomeIntent.OpenWeightOnlyModePopup(false))
      },
      onDismiss = {
        handleIntent(HomeIntent.OnWeightOnlyModeAlertDismiss)
      },
    )
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OpenWeightOnlyModePopup(
  onEnable: () -> Unit,
  onClose: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    sheetState = sheetState,
    modifier = Modifier.navigationBarsPadding(),
    onDismissRequest = onClose,
    containerColor = colorScheme.primaryBackground,
    scrimColor = colorScheme.overlay,
    dragHandle = null,
  ) {
    WeightOnlyModePopup(
      onEnable = onEnable,
      onClose = onClose,
      onDismiss = onDismiss,
    )
  }
}

@Composable
fun isKeyboardOpen(): Boolean {
  return WindowInsets.ime.getBottom(LocalDensity.current) > 0
}

@PreviewTheme
@Composable
fun HomeScreenPreview() {
  MeAppTheme {
    val dummyHomeState = HomeState(
      showAppsync = false,
      showWeightOnlyModeBottomSheet = true,
    )
    HomeScreenContent(
      state = dummyHomeState,
      handleIntent = {},
      showUnreadFeedIndicator = false,
    )
  }
}
