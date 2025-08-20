package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun DashboardControlPanel(
  inEditMode: Boolean = false,
  onResetClick: () -> Unit = {},
  onEditClick: (Boolean) -> Unit = {},
  onMetricInfoClick: () -> Unit = {},
  onUpdateGoalClick: () -> Unit = {}
) {
  val string = DashboardString.ControlPanel
  Column(
    modifier = Modifier
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
  ) {
    if (!inEditMode) {
      AppButton(label = string.EditDashboard, type = ButtonType.PrimaryOutlined, size = ButtonSize.Large) {
        onEditClick(true)
      }
      AppButton(label = string.UpdateGoal, type = ButtonType.TextPrimary, size = ButtonSize.Large) {
        onUpdateGoalClick()
      }
      AppButton(label = string.MetricInfo, type = ButtonType.TextPrimary, size = ButtonSize.Large) {
        onMetricInfoClick()
      }
    } else {
      AppButton(label = string.SaveChanges, type = ButtonType.PrimaryOutlined, size = ButtonSize.Large) {
        onEditClick(false)
      }
      AppButton(label = string.ResetDashboard, type = ButtonType.TextPrimary, size = ButtonSize.Large) {
        onResetClick()
      }
    }
  }
}

@PreviewTheme
@Composable
fun DashboardControlPanelPreview() {
  MeAppTheme {
    DashboardControlPanel()
  }
}
