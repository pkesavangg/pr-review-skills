package com.greatergoods.meapp.features.ScaleCustomization.screens

import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.ScaleCustomization.components.SettingsLayout
import com.greatergoods.meapp.features.ScaleCustomization.strings.CustomizeSettingsStrings

@Composable
fun DashboardMetrics(
  metricList: List<String> = emptyList(),
  onOrderChange: () -> Unit,
) {
  SettingsLayout(
    title = CustomizeSettingsStrings.DashboardMetrics.Title ,
    subtitle =  CustomizeSettingsStrings.DashboardMetrics.Subtitle,
    onBack = onOrderChange,
    onSave = {},
    onExit = {},
    onHelp = {},
  ) {
    // Dashboard Metrics
    // ScaleMode use -> ScaleModeSettings()
    // ScaleDisplayMetrics use -> ScaleMetricsSettingScreen()
    //
  }
}
