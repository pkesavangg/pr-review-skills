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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromPeriodSummaries
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ProductTypeHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.components.chart.bp.BpChartHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.ProductGraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.weight.WeightChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.getSourceFromSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.BpDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.components.WeightDashboardContent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardViewModel
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
  val viewmodel: DashboardViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsState()

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewmodel.onResume(lifecycleOwner)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DashboardScreenContent(
    state = state,
    productSelectionManager = viewmodel.productSelectionManager,
    showDialog = viewmodel.dialogQueueService::showDialog,
    handleIntent = viewmodel::handleIntent,
  )
}

@Composable
private fun DashboardScreenContent(
  state: DashboardState,
  productSelectionManager: com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager? = null,
  showDialog: (DialogModel) -> Unit,
  handleIntent: (DashboardIntent) -> Unit,
) {
  val selectedProduct = productSelectionManager?.selectedProduct
    ?.collectAsStateWithLifecycle()
  val availableProducts = productSelectionManager?.availableProducts
    ?.collectAsStateWithLifecycle()
  val hasMultipleProducts = (availableProducts?.value?.size ?: 0) > 1
  val product = selectedProduct?.value ?: ProductSelection.MyWeight

  val scrollState = rememberScrollState()
  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val activity = context as? AppCompatActivity

  // ── Graph VMs: 4 instances, one per segment ──
  val graphVms = GraphSegment.entries.mapIndexed { index, segment ->
    hiltViewModel<GraphViewModel, GraphViewModel.Factory>(
      key = "GraphViewModel-$index",
    ) { factory ->
      val anchoredTarget = state.scrollTarget?.let {
        GraphUtil.getStartOnAnchored(segment, it.toLong())
      }
      factory.create(segment, anchoredTarget?.toDouble())
    }
  }

  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) {
      pagerState.scrollToPage(targetPage)
    }
  }

  LaunchedEffect(pagerState.currentPage) {
    handleIntent(DashboardIntent.SetPagerState(pagerState.currentPage))
  }

  // ── Active VM state for below-chart content ──
  val activeVm = graphVms.getOrNull(pagerState.currentPage)
  val activeGraphState = activeVm?.state?.collectAsState()?.value
  val activeProductState = activeGraphState?.forProduct(product.productType) ?: ProductGraphState()

  // Sync selected entries to DashboardViewModel (for weight metrics)
  LaunchedEffect(activeProductState.target) {
    if (product is ProductSelection.MyWeight) {
      handleIntent(DashboardIntent.SetData(activeProductState.target))
    }
  }

  // ── Weight edit mode state ──
  var inEditMode by remember { mutableStateOf(false) }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }
  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  fun goBackToSnapshot() {
    scope.launch {
      productSelectionManager?.setSnapshotMode(true)
      navBackStack.addRoute(AppRoute.Main.DashboardSnapshot, AppRoute.Home, popUpTo = AppRoute.Main.Dashboard)
    }
  }

  BackHandler {
    if (!inEditMode && activity != null) {
      showDialog(
        DialogModel.Confirm(
          title = "Exit Dashboard",
          message = "Are you sure you want to exit the dashboard?",
          onConfirm = { scope.launch { activity.finishAffinity() } },
        ),
      )
    } else {
      scope.launch { scrollState.animateScrollTo(0) }
      if (inEditMode) {
        inEditMode = false
        currentVisibleMetrics = state.visibleKeys.filter { it is DashboardKey.Metric }
        currentVisibleMilestones = state.visibleKeys.filter { it is DashboardKey.Milestone }
      }
    }
  }

  AppScaffold(
    title = null,
    navigationIcon = if (hasMultipleProducts) {
      {
        IconButton(onClick = { goBackToSnapshot() }) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to snapshot",
            modifier = Modifier.size(24.dp),
            tint = MeTheme.colorScheme.textHeading,
          )
        }
      }
    } else null,
    topBarContent = if (productSelectionManager != null) {
      {
        ProductTypeHeader(
          selectedProduct = selectedProduct?.value,
          onClick = { productSelectionManager.showProductSheet("Dashboard") },
        )
      }
    } else null,
    onRefresh = { handleIntent(DashboardIntent.Refresh) },
    isRefreshing = state.isRefreshing,
  ) {
    Column(
      modifier = if (state.isEmpty) Modifier.fillMaxHeight() else Modifier.verticalScroll(scrollState),
      verticalArrangement = if (state.isEmpty) Arrangement.SpaceBetween else Arrangement.Top,
    ) {
      // ── Chart section (shared across all products) ──
      GraphPagerView(
        pagerState = pagerState,
        viewModels = graphVms,
        selectedProduct = product,
        state = state,
        header = { vm, segment ->
          val gState by vm.state.collectAsState()
          val pState = gState.forProduct(product.productType)
          when (product) {
            is ProductSelection.MyWeight -> {
              val avg = if (pState.target.isEmpty()) 0.0 else pState.target.map { it.weight }.average()
              val label = if (pState.target.isEmpty()) "000.0" else formatWeightValue(avg)
              val rangeText = pState.minTarget?.let { min ->
                pState.maxTarget?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
              } ?: ""
              WeightChartHeader(
                state = gState,
                productState = pState,
                segment = segment,
                weightData = label,
                rangeData = rangeText,
                weightValue = avg,
              )
            }
            is ProductSelection.BloodPressure -> {
              val target = pState.target
              val avgSys = target.map { it.weight.toInt() }.takeIf { it.isNotEmpty() }?.average()?.toInt()
              val avgDia = target.map { it.bodyFat?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
              val avgPulse = target.map { it.pulse?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
              val rangeText = pState.minTarget?.let { min ->
                pState.maxTarget?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
              } ?: ""
              BpChartHeader(
                state = gState,
                productState = pState,
                segment = segment,
                systolic = avgSys,
                diastolic = avgDia,
                pulse = avgPulse,
                rangeData = rangeText,
              )
            }
            is ProductSelection.Baby -> {
              val avg = if (pState.target.isEmpty()) 0.0 else pState.target.map { it.weight }.average()
              val label = if (pState.target.isEmpty()) "000.0" else formatWeightValue(avg)
              val rangeText = pState.minTarget?.let { min ->
                pState.maxTarget?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
              } ?: ""
              WeightChartHeader(
                state = gState,
                productState = pState,
                segment = segment,
                weightData = label,
                rangeData = rangeText,
                weightValue = avg,
              ) // TODO: Replace with BabyChartHeader
            }
          }
        },
        onSegmentChange = { segment, _ ->
          handleIntent(DashboardIntent.SetSelectedSegment(segment, null))
        },
        onChartConsuming = { handleIntent(DashboardIntent.SetIsChartConsuming(it)) },
        onScrollTargetConsumed = { handleIntent(DashboardIntent.SetIsScrollTargetConsumed(it)) },
      )

      // ── Below-chart content (product-specific) ──
      when (product) {
        is ProductSelection.MyWeight -> WeightDashboardContent(
          state = state,
          inEditMode = inEditMode,
          currentVisibleMetrics = currentVisibleMetrics,
          currentVisibleMilestones = currentVisibleMilestones,
          onEditModeChange = { editing ->
            if (!editing) {
              currentVisibleMetrics = state.visibleKeys.filterIsInstance<DashboardKey.Metric>()
              currentVisibleMilestones = state.visibleKeys.filterIsInstance<DashboardKey.Milestone>()
            }
            inEditMode = editing
          },
          onMetricsChanged = { currentVisibleMetrics = it },
          onMilestonesChanged = { currentVisibleMilestones = it },
          onMetricClick = { handleIntent(DashboardIntent.SetSelectedStat(it)) },
          onLongClick = {
            if (!inEditMode) {
              handleIntent(DashboardIntent.SetSelectedStat(null))
              inEditMode = true
            }
          },
          onResetClick = {
            handleIntent(DashboardIntent.SetSelectedStat(null))
            handleIntent(DashboardIntent.ResetDashboard(onConfirm = {
              inEditMode = false
              currentVisibleMetrics = state.visibleKeys.filter { it is DashboardKey.Metric }
              currentVisibleMilestones = state.visibleKeys.filter { it is DashboardKey.Milestone }
            }))
          },
          onEditClick = { editMode ->
            if (editMode && !inEditMode) {
              handleIntent(DashboardIntent.SetSelectedStat(null))
            } else if (!editMode && inEditMode) {
              val allVisibleKeys = currentVisibleMetrics + currentVisibleMilestones
              handleIntent(DashboardIntent.UpdateVisibleKeys(allVisibleKeys, state.dashboardType))
            }
            inEditMode = editMode
          },
          onUpdateGoalClick = {
            scope.launch { navBackStack.addRoute(AppRoute.AccountSettings.Goal) }
          },
          onMetricInfoClick = {
            val isSingleEntry = activeProductState.markerIndex != null
            val rangeText = activeProductState.minTarget?.let { min ->
              activeProductState.maxTarget?.let { max ->
                GraphUtil.formatDateRange(min, max, state.selectedSegment)
              }
            }
            scope.launch {
              navBackStack.addRoute(
                route = AppRoute.Dashboard.MetricInfo(
                  info = fromPeriodSummaries(state.data, isSingleEntry = isSingleEntry, rangeText = rangeText),
                  key = (state.selectedStat?.key as DashboardKey.Metric?)?.key ?: MetricKey.WEIGHT,
                  source = getSourceFromSegment(state.selectedSegment),
                ),
              )
            }
          },
          onConnectScaleClick = { handleIntent(DashboardIntent.OnConnectScale) },
        )

        is ProductSelection.BloodPressure -> BpDashboardContent(
          productState = activeProductState,
          state = state,
        )

        is ProductSelection.Baby -> {
          // TODO: BabyDashboardContent — weight/height toggle, no metrics
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        }
      }
    }
  }
}

@PreviewTheme
@Composable
private fun DashboardPreview() {
  MeAppTheme {
    DashboardScreenContent(
      state = DashboardState(),
      showDialog = {},
      handleIntent = {},
    )
  }
}
