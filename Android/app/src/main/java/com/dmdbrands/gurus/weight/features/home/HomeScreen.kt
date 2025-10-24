package com.dmdbrands.gurus.weight.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.greatergoods.libs.appsync.AppSyncResultHolder
import com.greatergoods.libs.appsync.startAppSyncScan
import com.greatergoods.libs.appsync.utility.AppSyncResultFactory
import kotlinx.coroutines.launch
import android.app.Activity
import android.util.Log

/**
 * Home screen displaying current user data, logout option, and switch account section.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsState()

  HomeScreenContent(
    state = state,
    handleIntent = viewModel::handleIntent,
    showUnreadFeedIndicator = state.showUnreadFeedIndicator,
  )
}

@Composable
fun HomeScreenContent(
  state: HomeState,
  handleIntent: (HomeIntent) -> Unit,
  showUnreadFeedIndicator: Boolean = false,
) {
  val topLevelBackStack = LocalNavBackStack.current
  val context = LocalContext.current
  var isScanning by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  // Observe shouldAskForReview state and launch review when true
  LaunchedEffect(state.shouldAskForReview) {
    if (state.shouldAskForReview) {
      handleIntent(HomeIntent.LaunchAppReview(context as Activity))
    }
  }

  Scaffold(
    bottomBar = {
      MainBottomNav(
        showAppsync = state.showAppsync,
        showUnreadFeedIndicator = showUnreadFeedIndicator,
        onOpenAppSync = {
          handleIntent(
            HomeIntent.CheckAndRequestPermission { isEnabled ->
              if (isEnabled && !isScanning) {
                isScanning = true
                coroutineScope.launch {
                  try {
                    val result = startAppSyncScan(
                      context = context,
                      zoom = 2,
                      showManualEntryButton = true,
                      onBack = {
                        // Create cancelled result and call intent handler immediately
                        Log.d("APPSYNC RES", "APPSYNC CANCELLED")
                        val cancelResult = AppSyncResultFactory.createCancelResult(2)
                        AppSyncResultHolder.result = cancelResult
                        handleIntent(HomeIntent.HandleAppSyncResult(cancelResult))
                      },
                    )
                    handleIntent(HomeIntent.HandleAppSyncResult(result))
                  } catch (e: Exception) {
                    // Handle error
                  } finally {
                    isScanning = false
                  }
                } else {
                  // Optional: show alert or log permission not granted
                }
              },
            )
          },
        )
      }
    },
    floatingActionButton = {
      if (state.showWeightOnlyModeBottomSheet && !state.isWeightOnlyModeDismissed && !state.isBodyMetricsEnabled) {
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
        handleIntent(HomeIntent.SetBodyMetricsEnabled(true))
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
    containerColor = MeTheme.colorScheme.primaryBackground,
  ) {
    WeightOnlyModePopup(
      onEnable = onEnable,
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
