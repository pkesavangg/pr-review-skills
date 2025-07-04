package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun DashboardControlPanel(inEditMode: Boolean = false, onEditClick: (Boolean) -> Unit = {}) {
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
            AppButton(label = string.UpdateGoal, type = ButtonType.TextPrimary, size = ButtonSize.Large) { }
            AppButton(label = string.MetricInfo, type = ButtonType.TextPrimary, size = ButtonSize.Large) { }
        } else {
            AppButton(label = string.SaveChanges, type = ButtonType.PrimaryOutlined, size = ButtonSize.Large) {
                onEditClick(false)
            }
            AppButton(label = string.ResetDashboard, type = ButtonType.TextPrimary, size = ButtonSize.Large) {
                onEditClick(false)
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
