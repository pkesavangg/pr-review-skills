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
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Horizontal pager with 4 graph segments (WEEK, MONTH, YEAR, TOTAL).
 * Receives VMs from parent — does not create them.
 * No data callbacks — parent observes VM state directly.
 *
 * @param pagerState Pager state managed by the parent.
 * @param viewModels 4 GraphViewModels, one per segment (indexed by GraphSegment.entries).
 * @param selectedProduct Current product selection for chart config and header.
 * @param state Dashboard state for scroll target and segment info.
 * @param header Composable slot for the product-specific header (above chart).
 * @param onSegmentChange Callback when user taps a segment button.
 * @param onChartConsuming Callback for chart scroll consuming state.
 * @param onScrollTargetConsumed Callback when scroll target has been applied.
 */
@Composable
fun GraphPagerView(
  pagerState: PagerState,
  viewModels: List<GraphViewModel>,
  selectedProduct: ProductSelection,
  state: DashboardState,
  header: @Composable (GraphViewModel, GraphSegment) -> Unit,
  onSegmentChange: (GraphSegment, Long?) -> Unit = { _, _ -> },
  onChartConsuming: (Boolean) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
) {
  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) { page ->
      val currentSegment = GraphSegment.entries.getOrNull(page) ?: GraphSegment.WEEK
      val viewmodel = viewModels.getOrNull(page) ?: return@HorizontalPager
      val graphState by viewmodel.state.collectAsState()
      val productType = selectedProduct.productType
      val productState = graphState.forProduct(productType)
      val chartConfig = rememberChartConfig(product = selectedProduct, goal = graphState.goal)

      GraphPageContent(
        header = { header(viewmodel, currentSegment) },
        chartConfig = chartConfig,
        graphState = graphState,
        productState = productState,
        productType = productType,
        segment = currentSegment,
        viewModel = viewmodel,
        page = page,
        currentPage = pagerState.currentPage,
        scrollTarget = state.scrollTarget,
        canScrollToAnchor = state.selectedSegment == currentSegment && !state.isScrollTargetConsumed,
        isConsuming = state.isConsuming,
        onChartConsuming = onChartConsuming,
        onScrollTargetConsumed = onScrollTargetConsumed,
      )
    }

    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = GraphSegment.entries[pagerState.currentPage],
      key = GraphSegment::name,
      onSelected = { segment ->
        onChartConsuming(true)
        onScrollTargetConsumed(false)
        onSegmentChange(segment, null)
      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
