package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.config.rememberChartConfig
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.theme.MeTheme

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
  onSegmentChange: (GraphSegment) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
) {
  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {
    val pagerModifier = if (chartFillsHeight) {
      Modifier.fillMaxWidth().weight(1f)
    } else {
      Modifier.fillMaxWidth()
    }
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = pagerModifier,
    ) { page ->
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

      Column {
        header(currentSegment)

        GraphView(
          modifier = Modifier.fillMaxWidth().then(if (chartFillsHeight) Modifier.weight(1f) else Modifier),
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

        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
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
