package com.dmdbrands.gurus.weight.features.dashboard.snapshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BabySnapshotCard
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BpSnapshotCard
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.WeightSnapshotCard
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel.DashboardSnapshotViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardSnapshotScreen(
  viewModel: DashboardSnapshotViewModel = hiltViewModel(),
) {
  val products by viewModel.productSelectionManager.availableProducts.collectAsStateWithLifecycle()
  val isSnapshotMode by viewModel.productSelectionManager.isSnapshotMode.collectAsStateWithLifecycle()
  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()

  // Returning user with a saved pick: skip the snapshot screen and jump straight to detail dashboard.
  LaunchedEffect(isSnapshotMode) {
    if (!isSnapshotMode) {
      navBackStack.addRoute(AppRoute.Main.Dashboard, AppRoute.Home, popUpTo = AppRoute.Main.DashboardSnapshot)
    }
  }

  // Suppress the snapshot UI when we're about to redirect — avoids a one-frame flash.
  if (!isSnapshotMode) return

  AppScaffold(
    title = null,
    topBarContent = {
      Image(
        painter = painterResource(AppIcons.Default.WgLogo),
        colorFilter = ColorFilter.tint(MeTheme.colorScheme.tertiaryAction),
        contentDescription = DashboardSnapshotStrings.AppLogoDescription,
        modifier = Modifier.size(45.dp),
      )
    },
  ) {
    // No pull-to-refresh needed — data updates reactively via Room DAO flows
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.secondaryBackground),
      contentPadding = PaddingValues(MeTheme.spacing.sm),
      verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
      items(
        items = products,
        key = { product ->
          when (product) {
            is ProductSelection.MyWeight -> "weight"
            is ProductSelection.BloodPressure -> "bp"
            is ProductSelection.Baby -> "baby-${product.profile.id}"
          }
        },
      ) { product ->
        when (product) {
          is ProductSelection.MyWeight -> WeightSnapshotCard(
            onTap = {
              scope.launch {
                viewModel.productSelectionManager.selectProduct(product)
                viewModel.productSelectionManager.setSnapshotMode(false)
                navBackStack.addRoute(AppRoute.Main.Dashboard, AppRoute.Home, popUpTo = AppRoute.Main.DashboardSnapshot)
              }
            },
          )

          is ProductSelection.BloodPressure -> BpSnapshotCard(
            viewModel = viewModel,
            onTap = {
              scope.launch {
                viewModel.productSelectionManager.selectProduct(product)
                viewModel.productSelectionManager.setSnapshotMode(false)
                navBackStack.addRoute(AppRoute.Main.Dashboard, AppRoute.Home, popUpTo = AppRoute.Main.DashboardSnapshot)
              }
            },
          )

          is ProductSelection.Baby -> BabySnapshotCard(
            product = product,
            viewModel = viewModel,
            onTap = {
              scope.launch {
                viewModel.productSelectionManager.selectProduct(product)
                viewModel.productSelectionManager.setSnapshotMode(false)
                navBackStack.addRoute(AppRoute.Main.Dashboard, AppRoute.Home, popUpTo = AppRoute.Main.DashboardSnapshot)
              }
            },
          )
        }
      }
    }
  }
}
