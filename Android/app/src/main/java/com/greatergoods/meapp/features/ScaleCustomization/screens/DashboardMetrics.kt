package com.greatergoods.meapp.features.ScaleCustomization.screens

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.ScaleCustomization.components.SettingsLayout
import com.greatergoods.meapp.features.ScaleCustomization.strings.CustomizeSettingsStrings
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.dashboard.components.DashboardMetrics

@Composable
fun DashboardMetrics(
  metricList: List<String> = emptyList(),
  onOrderChange: () -> Unit,
) {
  SettingsLayout(
    title = CustomizeSettingsStrings.DashboardMetrics.Title,
    subtitle = CustomizeSettingsStrings.DashboardMetrics.Subtitle,
    onBack = onOrderChange,
    onSave = {},
    onExit = {},
    onHelp = {},
  ) {

  }
}
