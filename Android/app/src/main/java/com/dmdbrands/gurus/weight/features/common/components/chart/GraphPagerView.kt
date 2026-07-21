package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.config.rememberChartConfig
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphLabelHelper
import com.dmdbrands.gurus.weight.features.common.strings.ChartHeaderStrings
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyDashboardGraph
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyGraphRange
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

/**
 * Horizontal pager with 4 graph segments. Pure display — no VM reference.
 * Receives state + callbacks.
 */
@Composable
fun GraphPagerView(
  pagerState: PagerState,
  state: BaseDashboardState,
  selectedProduct: ProductSelection,
  goal: Goal? = null,
  hasPercentile: Boolean = false,
  chartFillsHeight: Boolean = false,
  handleGraphIntent: (BaseGraphIntent) -> Unit,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary? = { _, _, _ -> null },
  header: @Composable (GraphSegment) -> Unit,
  emptyRange: EmptyGraphRange? = null,
  onSegmentChange: (GraphSegment) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
) {
  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {
    val pagerModifier = if (chartFillsHeight) {
      Modifier
        .fillMaxWidth()
        .weight(1f)
    } else {
      Modifier.fillMaxWidth()
    }
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = pagerModifier,
    ) { page ->
      GraphSegmentPage(
        page = page,
        state = state,
        selectedProduct = selectedProduct,
        goal = goal,
        hasPercentile = hasPercentile,
        chartFillsHeight = chartFillsHeight,
        handleGraphIntent = handleGraphIntent,
        createFallbackEntry = createFallbackEntry,
        header = header,
        emptyRange = emptyRange,
        onScrollTargetConsumed = onScrollTargetConsumed,
      )
    }

    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = GraphSegment.entries[pagerState.currentPage],
      key = GraphSegment::name,
      onSelected = { segment ->
        onScrollTargetConsumed(false)
        onSegmentChange(segment)
      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}

/**
 * A single graph segment page: header label, header crossfade, and the chart (or empty grid).
 * Extracted from [GraphPagerView]'s pager so each stays focused and short.
 */
@Composable
private fun GraphSegmentPage(
  page: Int,
  state: BaseDashboardState,
  selectedProduct: ProductSelection,
  goal: Goal?,
  hasPercentile: Boolean,
  chartFillsHeight: Boolean,
  handleGraphIntent: (BaseGraphIntent) -> Unit,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary?,
  header: @Composable (GraphSegment) -> Unit,
  emptyRange: EmptyGraphRange?,
  onScrollTargetConsumed: (Boolean) -> Unit,
) {
  val currentSegment = GraphSegment.entries.getOrNull(page) ?: GraphSegment.WEEK
  val segmentState = state.forSegment(currentSegment)
  val bpTarget = segmentState.target.filterIsInstance<PeriodBpmSummary>()
  val chartConfig = rememberChartConfig(
    product = selectedProduct,
    goal = goal,
    avgSystolic = bpTarget.takeIf { it.isNotEmpty() }?.map { it.avgSystolic }?.average()?.toInt(),
    avgDiastolic = bpTarget.takeIf { it.isNotEmpty() }?.map { it.avgDiastolic }?.average()?.toInt(),
    avgPulse = bpTarget.takeIf { it.isNotEmpty() }?.map { it.avgPulse }?.average()?.toInt(),
    hasPercentile = hasPercentile,
  )
  val producer = state.producerForSegment(currentSegment)
  val isChartReady by producer.isReady.collectAsStateWithLifecycle()
  // No entries for this product/segment → show the static first-run grid (MOB-432).
  val isEmpty = segmentState.isEmptyGraph

  Column(modifier = Modifier.padding(vertical = MeTheme.spacing.x3s)) {
    ChartHeaderLabel(
      segment = currentSegment,
      hasData = segmentState.target.isNotEmpty() && !isEmpty,
      isLoading = !isEmpty && !isChartReady,
      markerIndex = state.markerIndex,
      isLatestDaySelected = GraphLabelHelper.isLatestDaySelected(state.markerIndex, segmentState.data),
    )

    // Header: crossfade between skeleton and real content (empty state is "ready").
    Crossfade(
      targetState = isEmpty || isChartReady,
      animationSpec = tween(300),
    ) { ready ->
      if (ready) header(currentSegment) else HeaderSkeletonView()
    }

    // Chart: always composed (producer needs it), skeleton overlays on top
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (chartFillsHeight) Modifier.weight(1f) else Modifier),
    ) {
      if (isEmpty) {
        EmptyDashboardGraph(
          modifier = Modifier.fillMaxWidth(),
          height = 300.dp,
          range = emptyRange,
        )
      } else {
        SegmentChartContent(
          state = state,
          segmentState = segmentState,
          chartConfig = chartConfig,
          producer = producer,
          currentSegment = currentSegment,
          isChartReady = isChartReady,
          chartFillsHeight = chartFillsHeight,
          handleGraphIntent = handleGraphIntent,
          createFallbackEntry = createFallbackEntry,
          onScrollTargetConsumed = onScrollTargetConsumed,
        )
      }
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}

/**
 * The chart itself plus its crossfading skeleton overlay for a non-empty segment.
 * A [BoxScope] receiver so the skeleton can [Modifier.matchParentSize] over the chart.
 */
@Composable
private fun BoxScope.SegmentChartContent(
  state: BaseDashboardState,
  segmentState: com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState,
  chartConfig: com.dmdbrands.gurus.weight.features.common.components.chart.config.ChartConfig,
  producer: CartesianChartModelProducer,
  currentSegment: GraphSegment,
  isChartReady: Boolean,
  chartFillsHeight: Boolean,
  handleGraphIntent: (BaseGraphIntent) -> Unit,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary?,
  onScrollTargetConsumed: (Boolean) -> Unit,
) {
  GraphView(
    modifier = Modifier.fillMaxWidth(),
    state = state,
    segmentState = segmentState,
    chartConfig = chartConfig,
    modelProducer = producer,
    segment = currentSegment,
    scrollTarget = state.scrollTarget,
    canScrollToAnchor = state.selectedSegment == currentSegment,
    handleGraphIntent = handleGraphIntent,
    createFallbackEntry = createFallbackEntry,
    onScrollTargetConsumed = onScrollTargetConsumed,
    chartFillsHeight = chartFillsHeight,
  )

  // Chart skeleton: crossfade overlay
  Crossfade(
    targetState = isChartReady,
    animationSpec = tween(300),
    modifier = Modifier.matchParentSize(),
  ) { ready ->
    if (!ready) GraphSkeletonView(modifier = Modifier.fillMaxSize())
  }
}

/**
 * Always-visible chart label: "week average", "day average", "latest entry",
 * "no entries", etc. Routes through [GraphLabelHelper] so it stays in lockstep with
 * the metric-info sheet. Per MA-3965, selecting the most recent day on Week/Month
 * reads "latest entry" (the plotted point shows that day's latest entry, not its
 * average); any earlier day reads "day average".
 */
@Composable
private fun ChartHeaderLabel(
  segment: GraphSegment,
  hasData: Boolean,
  isLoading: Boolean,
  markerIndex: Double? = null,
  isLatestDaySelected: Boolean = false,
) {
  val text = if (!hasData && !isLoading) {
    ChartHeaderStrings.NoEntries
  } else {
    GraphLabelHelper.selectionLabel(
      segment = segment,
      hasSelection = markerIndex != null,
      isLatestDaySelected = isLatestDaySelected,
    )
  }

  Text(
    text = text,
    style = MeTheme.typography.subHeading1,
    color = MeTheme.colorScheme.textSubheading,
    modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
  )
}
