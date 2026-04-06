package com.dmdbrands.gurus.weight.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.app.components.NavHost
import com.dmdbrands.gurus.weight.app.string.AppString.SCALEDISCOVEREDTIMEOUT
import com.dmdbrands.gurus.weight.app.viewmodel.AppIntent
import com.dmdbrands.gurus.weight.app.viewmodel.AppViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.DialogHost
import com.dmdbrands.gurus.weight.features.common.components.ProductSelectionBottomSheet
import com.dmdbrands.gurus.weight.features.common.components.ScaleDiscoveredModal
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.example.nav3integration.rememberTopLevelBackStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main app composable. Sets up theme, navigation, and global dialog queue host.
 * Handles window insets properly for the entire app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeApp() {
  val appViewModel: AppViewModel = hiltViewModel()
  val uiState by appViewModel.state.collectAsStateWithLifecycle()

  // Keep navigation stack stable - don't let it be affected by theme changes
  val topLevelBackStack =
    rememberTopLevelBackStack(
      Pair(AppRoute.App, AppRoute.Init.Loading),
      AppRoute.Auth.Login(),
      Pair(AppRoute.Home, AppRoute.Main.DashboardSnapshot),
    )

  MeAppTheme(themeMode = uiState.themeMode) {

    Surface(
      modifier =
        Modifier
          .fillMaxSize()
          .imePadding(),
      color = colorScheme.primaryBackground,
    ) {
      CompositionLocalProvider(LocalNavBackStack provides topLevelBackStack) {
        DialogHost()
        NavHost(topLevelBackStack, appViewModel)
      }
    }
    // Product Selection Bottom Sheet — controlled by ProductSelectionManager
    val productManager = appViewModel.productSelectionManager
    val showProductSheet by productManager.showSheet.collectAsStateWithLifecycle()
    val productSheetTitle by productManager.sheetTitle.collectAsStateWithLifecycle()
    val availableProducts by productManager.availableProducts
      .collectAsStateWithLifecycle()
    val selectedProduct by productManager.selectedProduct
      .collectAsStateWithLifecycle()

    if (showProductSheet) {
      ProductSelectionBottomSheet(
        title = productSheetTitle,
        availableProducts = availableProducts,
        selectedProduct = selectedProduct,
        onSelect = { selection ->
          appViewModel.viewModelScope.launch {
            productManager.selectProduct(selection)
          }
        },
        onDismiss = { productManager.dismissProductSheet() },
      )
    }

    if (uiState.isScaleDiscovered && uiState.hasScanStarted) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      val discoveryTimestamp = uiState.scaleDiscoveredTimestamp
      val shouldShowModal = discoveryTimestamp?.let { timestamp ->
        val timeSinceDiscovery = System.currentTimeMillis() - timestamp
        // Only show modal if discovered within the last 15 seconds
        timeSinceDiscovery <= SCALEDISCOVEREDTIMEOUT
      } ?: false

      LaunchedEffect(discoveryTimestamp) {
        if (discoveryTimestamp != null) {
          val timeSinceDiscovery = System.currentTimeMillis() - discoveryTimestamp
          val remainingTime = maxOf(0, SCALEDISCOVEREDTIMEOUT - timeSinceDiscovery)
          if (remainingTime > 0) {
            delay(remainingTime)
            appViewModel.handleIntent(AppIntent.OnPopUpDismiss)
          } else {
            // Discovery was too old, dismiss immediately
            appViewModel.handleIntent(AppIntent.OnPopUpDismiss)
          }
        }
      }

      if (shouldShowModal) {
        ModalBottomSheet(
          sheetState = sheetState,
          modifier = Modifier.navigationBarsPadding(),
          onDismissRequest = { appViewModel.handleIntent(AppIntent.OnPopUpDismiss) },
          containerColor = colorScheme.primaryBackground,
          scrimColor = colorScheme.overlay,
          dragHandle = null,
        ) {
          ApplySystemBarsForDialog()
          ScaleDiscoveredModal(
            sku = uiState.sku,
            onConnect = {
              appViewModel.handleIntent(AppIntent.OnPopUpConnect)
            },
            onClose = { appViewModel.handleIntent(AppIntent.OnPopUpDismiss) },
          )
        }
      }
    }
  }
}

@Composable
fun ApplySystemBarsForDialog() {
  val view = LocalView.current
  val isDarkTheme = isSystemInDarkTheme()
  (view.parent as? DialogWindowProvider)?.window?.let { window ->
    SideEffect {
      val controller =
        WindowCompat.getInsetsController(window, view)

      controller.isAppearanceLightStatusBars = !isDarkTheme
      controller.isAppearanceLightNavigationBars = !isDarkTheme
    }
  }
}
