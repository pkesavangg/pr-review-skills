package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardIntent
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Baby-specific below-chart content: Weight/Height metric toggle.
 */
@Composable
fun BabyDashboardContent(
  state: BabyDashboardState,
  handleIntent: (BaseGraphIntent) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm),
  ) {
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    SegmentButtonGroup(
      data = BabyMetric.entries.toList(),
      selectedData = state.selectedMetric,
      key = { it.name },
      onSelected = { metric -> handleIntent(BabyDashboardIntent.SetSelectedMetric(metric)) },
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
