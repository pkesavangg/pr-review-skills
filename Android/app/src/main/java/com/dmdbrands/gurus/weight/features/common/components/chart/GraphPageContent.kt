package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.components.chart.config.ChartConfig
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.ProductGraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Content for a single graph pager page.
 * Pure layout: header slot + GraphView. No data callbacks.
 * Parent observes VM state directly for metrics/range/marker info.
 */
@Composable
fun GraphPageContent(
  header: @Composable () -> Unit,
  chartConfig: ChartConfig,
  graphState: GraphState,
  productState: ProductGraphState,
  productType: ProductType,
  segment: GraphSegment,
  viewModel: GraphViewModel,
  page: Int,
  currentPage: Int,
  scrollTarget: Double? = null,
  canScrollToAnchor: Boolean = false,
  isConsuming: Boolean = false,
  onChartConsuming: (Boolean) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
) {
  Column {
    header()

    GraphView(
      modifier = Modifier.fillMaxWidth(),
      graphState = graphState,
      productState = productState,
      chartConfig = chartConfig,
      productType = productType,
      segment = segment,
      scrollTarget = scrollTarget,
      canScrollToAnchor = canScrollToAnchor,
      viewModel = viewModel,
      onChartConsuming = onChartConsuming,
      onScrollTargetConsumed = onScrollTargetConsumed,
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
