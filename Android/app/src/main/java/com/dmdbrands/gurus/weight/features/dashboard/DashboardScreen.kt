package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.dmdbrands.gurus.weight.core.navigation.LocalDialogQueueService
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.navigation.LocalProductSelectionManager
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BpDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardChartHeader
import com.dmdbrands.gurus.weight.features.dashboard.components.WeightDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardViewModel
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
  val psm = LocalProductSelectionManager.current
  val dialogService = LocalDialogQueueService.current
  val product by psm.selectedProduct.collectAsStateWithLifecycle()
  val hasMultipleProducts = psm.availableProducts.collectAsStateWithLifecycle().value.size > 1

  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()
  val activity = LocalContext.current as? AppCompatActivity

  BackHandler {
    if (activity != null) {
      dialogService.showDialog(
        DialogModel.Confirm(
          title = "Exit Dashboard",
          message = "Are you sure you want to exit the dashboard?",
          onConfirm = { scope.launch { activity.finishAffinity() } },
        ),
      )
    }
  }

  AppScaffold(
    title = null,
    navigationIcon = if (hasMultipleProducts) {
      {
        IconButton(onClick = {
          scope.launch {
            psm.setSnapshotMode(true)
            navBackStack.addRoute(AppRoute.Main.DashboardSnapshot, AppRoute.Home, popUpTo = AppRoute.Main.Dashboard)
          }
        }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp), MeTheme.colorScheme.textHeading)
        }
      }
    } else null,
    topBarContent = {
      ProductTypeHeader(
        selectedProduct = product,
        onClick = { psm.showProductSheet("Dashboard") },
      )
    },
  ) {
    when (product) {
      is ProductSelection.MyWeight -> WeightDashboardPage()
      is ProductSelection.BloodPressure -> BpDashboardPage()
      is ProductSelection.Baby -> Spacer(modifier = Modifier.height(MeTheme.spacing.sm)) // TODO
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightDashboardPage() {
  val vm: WeightDashboardViewModel = hiltViewModel()
  val state by vm.state.collectAsState()

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
  }

  PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = { vm.refresh() },
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      GraphPagerView(
        pagerState = pagerState,
        viewModel = vm,
        selectedProduct = ProductSelection.MyWeight,
        goal = state.goal,
        header = { segment -> DashboardChartHeader(state = state, segment = segment, product = ProductSelection.MyWeight) },
        onSegmentChange = { segment -> vm.handleIntent(WeightDashboardIntent.SetSelectedSegment(segment)) },
      )

      WeightDashboardContent(
        state = state,
        activeSegmentState = state.forSegment(state.selectedSegment),
        viewModel = vm,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BpDashboardPage() {
  val vm: BpDashboardViewModel = hiltViewModel()
  val state by vm.state.collectAsState()

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
  }

  PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = { vm.refresh() },
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      GraphPagerView(
        pagerState = pagerState,
        viewModel = vm,
        selectedProduct = ProductSelection.BloodPressure,
        header = { segment -> DashboardChartHeader(state = state, segment = segment, product = ProductSelection.BloodPressure) },
        onSegmentChange = { segment -> vm.handleIntent(BpDashboardIntent.SetSelectedSegment(segment)) },
      )

      BpDashboardContent(
        segmentState = state.forSegment(state.selectedSegment),
        state = state,
      )
    }
  }
}
