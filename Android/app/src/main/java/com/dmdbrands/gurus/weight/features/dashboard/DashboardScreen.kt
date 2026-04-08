package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BpDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardChartHeader
import com.dmdbrands.gurus.weight.features.dashboard.components.WeightDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardViewModel
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
  // Use a lightweight VM just to access productSelectionManager
  val weightVm: WeightDashboardViewModel = hiltViewModel()
  val psm = weightVm.productSelectionManager
  val selectedProductState = psm.selectedProduct.collectAsStateWithLifecycle()
  val availableProductsState = psm.availableProducts.collectAsStateWithLifecycle()
  val product = selectedProductState.value
  val hasMultipleProducts = availableProductsState.value.size > 1

  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()

  fun goBackToSnapshot() {
    scope.launch {
      psm.setSnapshotMode(true)
      navBackStack.addRoute(AppRoute.Main.DashboardSnapshot, AppRoute.Home, popUpTo = AppRoute.Main.Dashboard)
    }
  }

  when (product) {
    is ProductSelection.MyWeight -> WeightDashboardScreen(
      hasMultipleProducts = hasMultipleProducts,
      onBackToSnapshot = { goBackToSnapshot() },
    )
    is ProductSelection.BloodPressure -> BpDashboardScreen(
      hasMultipleProducts = hasMultipleProducts,
      onBackToSnapshot = { goBackToSnapshot() },
    )
    is ProductSelection.Baby -> {
      // TODO: BabyDashboardScreen
      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    }
  }
}

@Composable
private fun WeightDashboardScreen(
  hasMultipleProducts: Boolean,
  onBackToSnapshot: () -> Unit,
) {
  val vm: WeightDashboardViewModel = hiltViewModel()
  val state by vm.state.collectAsState()
  val scrollState = rememberScrollState()

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
  }

  AppScaffold(
    title = null,
    navigationIcon = if (hasMultipleProducts) {
      {
        IconButton(onClick = onBackToSnapshot) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp), MeTheme.colorScheme.textHeading)
        }
      }
    } else null,
    topBarContent = {
      ProductTypeHeader(
        selectedProduct = ProductSelection.MyWeight,
        onClick = { vm.productSelectionManager.showProductSheet("Dashboard") },
      )
    },
    onRefresh = { vm.refresh() },
    isRefreshing = state.isRefreshing,
  ) {
    Column(
      modifier = if (state.isEmpty) Modifier.fillMaxHeight() else Modifier.verticalScroll(scrollState),
      verticalArrangement = if (state.isEmpty) Arrangement.SpaceBetween else Arrangement.Top,
    ) {
      GraphPagerView(
        pagerState = pagerState,
        viewModel = vm,
        selectedProduct = ProductSelection.MyWeight,
        goal = state.goal,
        header = { segment ->
          DashboardChartHeader(viewModel = vm, segment = segment, product = ProductSelection.MyWeight)
        },
        onSegmentChange = { segment ->
          vm.handleIntent(WeightDashboardIntent.SetSelectedSegment(segment))
        },
      )

      // Active segment state for below-chart content
      val activeSegment = state.forSegment(state.selectedSegment)

      WeightDashboardContent(
        state = state,
        activeSegmentState = activeSegment,
        viewModel = vm,
      )
    }
  }
}

@Composable
private fun BpDashboardScreen(
  hasMultipleProducts: Boolean,
  onBackToSnapshot: () -> Unit,
) {
  val vm: BpDashboardViewModel = hiltViewModel()
  val state by vm.state.collectAsState()
  val scrollState = rememberScrollState()

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
  }

  AppScaffold(
    title = null,
    navigationIcon = if (hasMultipleProducts) {
      {
        IconButton(onClick = onBackToSnapshot) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp), MeTheme.colorScheme.textHeading)
        }
      }
    } else null,
    topBarContent = {
      ProductTypeHeader(
        selectedProduct = ProductSelection.BloodPressure,
        onClick = { vm.productSelectionManager.showProductSheet("Dashboard") },
      )
    },
    onRefresh = { vm.refresh() },
    isRefreshing = state.isRefreshing,
  ) {
    Column(modifier = Modifier.verticalScroll(scrollState)) {
      GraphPagerView(
        pagerState = pagerState,
        viewModel = vm,
        selectedProduct = ProductSelection.BloodPressure,
        header = { segment ->
          DashboardChartHeader(viewModel = vm, segment = segment, product = ProductSelection.BloodPressure)
        },
        onSegmentChange = { segment ->
          vm.handleIntent(com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardIntent.SetSelectedSegment(segment))
        },
      )

      val activeSegment = state.forSegment(state.selectedSegment)
      BpDashboardContent(
        segmentState = activeSegment,
        state = state,
      )
    }
  }
}
