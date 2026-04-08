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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.config.rememberChartConfig
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Horizontal pager with 4 graph segments (WEEK, MONTH, YEAR, TOTAL).
 * Receives one product VM — reads segment states and producers from it.
 * No data callbacks — parent observes VM state directly.
 *
 * @param pagerState Pager state managed by the parent.
 * @param viewModel The active product's dashboard VM (holds graph state).
 * @param selectedProduct Current product selection for chart config and header.
 * @param header Composable slot for the product-specific header (above chart).
 * @param onSegmentChange Callback when user taps a segment button.
 * @param onScrollTargetConsumed Callback when scroll target has been applied.
 */
@Composable
fun <S : BaseDashboardState> GraphPagerView(
  pagerState: PagerState,
  viewModel: BaseDashboardViewModel<S, *>,
  selectedProduct: ProductSelection,
  goal: com.dmdbrands.gurus.weight.domain.model.goal.Goal? = null,
  header: @Composable (GraphSegment) -> Unit,
  onSegmentChange: (GraphSegment) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
) {
  val state by viewModel.state.collectAsState()

  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) { page ->
      val currentSegment = GraphSegment.entries.getOrNull(page) ?: GraphSegment.WEEK
      val segmentState = state.forSegment(currentSegment)
      val chartConfig = rememberChartConfig(product = selectedProduct, goal = goal)
      val producer = viewModel.getProducerForSegment(currentSegment)

      Column {
        header(currentSegment)

        GraphView(
          modifier = Modifier.fillMaxWidth(),
          state = state,
          segmentState = segmentState,
          chartConfig = chartConfig,
          modelProducer = producer,
          segment = currentSegment,
          scrollTarget = state.scrollTarget,
          canScrollToAnchor = state.selectedSegment == currentSegment,
          handleGraphIntent = viewModel::handleGraphIntent,
          onScrollTargetConsumed = onScrollTargetConsumed,
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
