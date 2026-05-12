package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.core.navigation.LocalDialogQueueService
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.navigation.LocalProductSelectionManager
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BpDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardChartHeader
import com.dmdbrands.gurus.weight.features.dashboard.components.WeightDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BabyDashboardContent
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
  val activity = LocalActivity.current as? AppCompatActivity

  BackHandler {
    if (activity != null) {
      dialogService.showDialog(
        DialogModel.Confirm(
          title = DashboardString.ExitDialog.Title,
          message = DashboardString.ExitDialog.Message,
          onConfirm = { scope.launch { activity.finishAffinity() } },
        ),
      )
    }
  }

  AppScaffold(
    title = null,
    navigationIcon = if (hasMultipleProducts) {
      {
        IconButton(
          onClick = {
            scope.launch {
              psm.setSnapshotMode(true)
              navBackStack.replaceStack(listOf(AppRoute.Main.DashboardSnapshot), AppRoute.Home)
            }
          },
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, DashboardString.BackContentDescription, Modifier.size(24.dp), MeTheme.colorScheme.textHeading)
        }
      }
    } else null,
    topBarContent = {
      ProductTypeHeader(
        selectedProduct = product,
        onClick = { psm.showProductSheet(DashboardString.DashboardSource) },
      )
    },
  ) {
    when (product) {
      is ProductSelection.MyWeight -> {
        val vm: WeightDashboardViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        DashboardPage(
          vm = vm,
          product = product,
          goal = state.goal,
          onRefresh = { vm.handleIntent(WeightDashboardIntent.Refresh) },
          createFallbackEntry = { ts, yValues, seg ->
            val y = yValues.firstOrNull() ?: return@DashboardPage null
            val period = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).let { dt ->
              if (seg == GraphSegment.WEEK || seg == GraphSegment.MONTH) dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
              else dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            }
            PeriodBodyScaleSummary(
              period = period,
              entryTimestamp = DateTimeConverter.timestampToIso(ts),
              weight = y,
              unit = WeightUnit.LB,
            )
          },
        ) { s ->
          WeightDashboardContent(
            state = s,
            activeSegmentState = s.forSegment(s.selectedSegment),
            handleIntent = vm::handleIntent,
          )
        }
      }

      is ProductSelection.BloodPressure -> {
        val vm: BpDashboardViewModel = hiltViewModel()
        DashboardPage(
          vm = vm,
          product = product,
          onRefresh = { vm.handleIntent(BpDashboardIntent.Refresh) },
          createFallbackEntry = { ts, yValues, seg ->
            if (yValues.size < 3) return@DashboardPage null
            val period = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).let { dt ->
              if (seg == GraphSegment.WEEK || seg == GraphSegment.MONTH) dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
              else dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            }
            PeriodBpmSummary(
              period = period,
              entryTimestamp = DateTimeConverter.timestampToIso(ts),
              avgSystolic = yValues[0].toInt(),
              avgDiastolic = yValues[1].toInt(),
              avgPulse = yValues[2].toInt(),
            )
          },
        ) { s ->
          BpDashboardContent(state = s)
        }
      }

      is ProductSelection.Baby -> {
        val babyProduct = product as ProductSelection.Baby
        val vm: BabyDashboardViewModel = hiltViewModel(
          creationCallback = { factory: BabyDashboardViewModel.Factory -> factory.create(babyProduct) },
        )
        val state by vm.state.collectAsStateWithLifecycle()
        DashboardPage(
          vm = vm,
          product = product,
          hasPercentile = true,
          chartFillsHeight = true,
          onRefresh = { vm.handleIntent(BabyDashboardIntent.Refresh) },
          createFallbackEntry = { ts, yValues, seg ->
            val y = yValues.firstOrNull() ?: return@DashboardPage null
            val period = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).let { dt ->
              if (seg == GraphSegment.WEEK || seg == GraphSegment.MONTH) dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
              else dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            }
            // Chart plots ONE metric at a time (weight in lbs OR height in inches).
            // Convert the interpolated Y back to storage units for PeriodBabySummary.
            val isWeight = state.selectedMetric == BabyMetric.WEIGHT
            PeriodBabySummary(
              period = period,
              entryTimestamp = DateTimeConverter.timestampToIso(ts),
              babyId = babyProduct.profile.id,
              avgWeightDecigrams = if (isWeight) ConversionTools.convertLbToDecigrams(y) else null,
              avgLengthMillimeters = if (!isWeight) ConversionTools.convertInchesToMm(y) else null,
            )
          },
        ) { _ -> }
      }
    }
  }
}

/**
 * Shared dashboard page wrapper: pager + pull-to-refresh + chart + below-chart slot.
 * Each product provides its own below-chart content via [belowChart].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <S : BaseDashboardState> DashboardPage(
  vm: BaseDashboardViewModel<S, BaseGraphIntent>,
  product: ProductSelection,
  goal: Goal? = null,
  hasPercentile: Boolean = false,
  chartFillsHeight: Boolean = false,
  onRefresh: () -> Unit,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary? = { _, _, _ -> null },
  belowChart: @Composable (S) -> Unit,
) {
  val state by vm.state.collectAsStateWithLifecycle()

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) pagerState.scrollToPage(targetPage)
  }

  val columnModifier = if (chartFillsHeight) {
    Modifier.fillMaxSize()
  } else {
    Modifier.verticalScroll(rememberScrollState())
  }

  PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = onRefresh,
  ) {
    Column(modifier = columnModifier) {
      GraphPagerView(
        pagerState = pagerState,
        state = state,
        selectedProduct = product,
        goal = goal,
        hasPercentile = hasPercentile,
        chartFillsHeight = chartFillsHeight,
        handleGraphIntent = vm::handleIntent,
        createFallbackEntry = createFallbackEntry,
        header = { segment -> DashboardChartHeader(state = state, segment = segment, product = product, handleIntent = vm::handleIntent) },
        onSegmentChange = {
          val currentSegmentState = state.forSegment(state.selectedSegment)
          val anchorTimeStamp = if (currentSegmentState.visibleMin != null && currentSegmentState.visibleMax != null) {
            (currentSegmentState.visibleMin + currentSegmentState.visibleMax) / 2.0
          } else {
            null
          }
          vm.handleIntent(BaseGraphIntent.SetSelectedSegment(it, anchorTimeStamp))
        },
      )

      belowChart(state)
    }
  }
}

