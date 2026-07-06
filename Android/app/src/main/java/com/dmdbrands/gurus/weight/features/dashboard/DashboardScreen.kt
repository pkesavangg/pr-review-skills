package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.MutatePriority
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.dmdbrands.gurus.weight.features.dashboard.components.BabyScaleEmptyDashboard
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BpDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardChartHeader
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyGraphDefaults
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyGraphRange
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyMetric
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
        onClick = { psm.showProductSheet(DashboardString.SelectGraphTitle) },
        showDropdown = hasMultipleProducts,
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
          scrollToTopSignal = state.resetSignal,
          // Goal set → goal-anchored range + goal badge; no goal → a default range so the Y axis
          // still shows (consistent with BP/Baby) instead of a bare, axis-less grid.
          emptyRange = EmptyGraphDefaults.weightGoal(
            goalDisplay = state.goal?.goalWeight,
            isKg = state.weightUnit == WeightUnit.KG,
          ) ?: EmptyGraphDefaults.weightDefault(isKg = state.weightUnit == WeightUnit.KG),
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
          emptyRange = EmptyGraphDefaults.Bp,
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
          BpDashboardContent(
            state = s,
            onConnectDevice = { vm.handleIntent(BpDashboardIntent.OnConnectDevice) },
          )
        }
      }

      is ProductSelection.Baby -> {
        val babyProduct = product as ProductSelection.Baby
        // Key by baby id so switching babies returns a distinct VM (subscribed to that baby's
        // babyId-filtered graph data) instead of reusing the first baby's instance — without a
        // key, hiltViewModel caches one instance per composition and ignores creationCallback on
        // subsequent babies, so every baby showed the first baby's entries. (MOB-598)
        val vm: BabyDashboardViewModel = hiltViewModel(
          key = "baby:${babyProduct.profile.id}",
          creationCallback = { factory: BabyDashboardViewModel.Factory -> factory.create(babyProduct) },
        )
        val state by vm.state.collectAsStateWithLifecycle()
        DashboardPage(
          vm = vm,
          product = product,
          hasPercentile = true,
          // Fill height only when there's data; the empty state needs a fixed-height
          // grid so the CONNECT DEVICE CTA stays visible below the chart (MOB-432).
          chartFillsHeight = !state.isEmpty,
          emptyRange = if (state.selectedMetric == BabyMetric.HEIGHT) EmptyGraphDefaults.BabyHeight else EmptyGraphDefaults.BabyWeight,
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
        ) { s ->
          if (s.isEmpty) {
            EmptyMetric(onConnectScaleClick = { vm.handleIntent(BabyDashboardIntent.OnConnectDevice) })
          }
        }
      }

      is ProductSelection.BabyScale -> {
        // Owns the baby product but no baby profile yet: show the baby empty dashboard
        // (zero value + W/H toggle + grid + tabs) followed by the "No babies added yet"
        // ADD A BABY card, surfaced under the "Baby Scale" title. A profile — not a
        // device — is the blocker here, so the CTA routes to add-a-baby. (MOB-592, MOB-1246)
        BabyScaleEmptyDashboard(
          onAddBaby = {
            scope.launch {
              navBackStack.addRoute(AppRoute.AccountSettings.AddBaby())
            }
          },
        )
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
  scrollToTopSignal: Int = 0,
  emptyRange: EmptyGraphRange? = null,
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

  val scrollState = rememberScrollState()
  ScrollToTopOnSignal(scrollState, scrollToTopSignal)
  val flingInterceptScope = rememberCoroutineScope()
  // Compose consumes the first Down event during a fling to stop the scroll,
  // which means a clickable child (e.g. UPDATE GOAL / METRIC INFO) misses the
  // tap if the user fingers down while the scroll is still gliding. We watch
  // the Initial pass and stop the scroll ourselves without consuming the
  // event, so the same gesture continues through to the child. (MA-2615)
  val columnModifier = if (chartFillsHeight) {
    Modifier.fillMaxSize()
  } else {
    Modifier
      .verticalScroll(scrollState)
      .pointerInput(scrollState) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && scrollState.isScrollInProgress) {
              flingInterceptScope.launch {
                scrollState.scroll(scrollPriority = MutatePriority.UserInput) { }
              }
            }
          }
        }
      }
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
        emptyRange = emptyRange,
        onSegmentChange = { vm.handleIntent(BaseGraphIntent.SetSelectedSegment(it, state.segmentAnchorTimestamp())) },
      )

      belowChart(state)
    }
  }
}

/**
 * Midpoint of the currently visible chart range, used to anchor the chart when
 * the user changes segments. Null when the visible range isn't known yet.
 */
private fun BaseDashboardState.segmentAnchorTimestamp(): Double? {
  val segment = forSegment(selectedSegment)
  return if (segment.visibleMin != null && segment.visibleMax != null) {
    (segment.visibleMin + segment.visibleMax) / 2.0
  } else {
    null
  }
}

/**
 * Scrolls [scrollState] back to the top whenever [signal] changes to a positive
 * value. Used after a RESET DASHBOARD restores the default tiles so the grid is
 * in view rather than the button cluster (MOB-445). The initial composition
 * (signal == 0) is a no-op.
 */
@Composable
private fun ScrollToTopOnSignal(scrollState: ScrollState, signal: Int) {
  LaunchedEffect(signal) {
    if (signal > 0) scrollState.animateScrollTo(0)
  }
}

