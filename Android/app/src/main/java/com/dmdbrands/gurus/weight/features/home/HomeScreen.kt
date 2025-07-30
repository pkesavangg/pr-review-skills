package com.dmdbrands.gurus.weight.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.libs.appsync.startAppSyncScan
import com.dmdbrands.gurus.weight.app.components.HomeNavHost
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.MainBottomNav
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.dmdbrands.gurus.weight.features.home.reducer.HomeState
import com.dmdbrands.gurus.weight.features.home.viewmodel.HomeViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import kotlinx.coroutines.launch

/**
 * Home screen displaying current user data, logout option, and switch account section.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsState()
  HomeScreenContent(state, viewModel::handleIntent)
}

@Composable
fun HomeScreenContent(
  state: HomeState,
  handleIntent: (HomeIntent) -> Unit,
) {
  val topLevelBackStack = LocalNavBackStack.current
  val context = LocalContext.current
  var isScanning by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    bottomBar = {
      MainBottomNav(
        showAppsync = state.showAppsync,
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
                    )
                    handleIntent(HomeIntent.HandleAppSyncResult(result))
                  } catch (e: Exception) {
                    // Handle error
                  } finally {
                    isScanning = false
                  }
                }
              } else {
                // Optional: show alert or log permission not granted
              }
            },
          )
        },
      )
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
}

@PreviewTheme
@Composable
fun HomeScreenPreview() {
  MeAppTheme {
    val dummyHomeState = HomeState(showAppsync = false)
    HomeScreenContent(
      state = dummyHomeState,
      handleIntent = {},
    )
  }
}
