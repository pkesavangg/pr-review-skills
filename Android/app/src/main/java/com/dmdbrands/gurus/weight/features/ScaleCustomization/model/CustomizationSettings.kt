package com.dmdbrands.gurus.weight.features.ScaleSetup.model

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.resources.AppIcons

data class CustomizeSettingsCard(
  val iconId: Int,
  val title: String,
  val subtitle: String,
  val step: CustomizeSettings,
  val isVisited: Boolean
)

val CustomizeSettingsList = listOf(
  CustomizeSettingsCard(
    iconId = AppIcons.Setup.MetricCard,
    title = "Dashboard Metrics",
    subtitle = "Customize which metrics you’ll see on your app’s dashboard.",
    step = CustomizeSettings.DASHBOARD_METRICS,
    isVisited = false,
  ),
  CustomizeSettingsCard(
    iconId = AppIcons.Setup.Graph,
    title = "Scale Metrics",
    subtitle = "Customize the metrics you’ll see when weighing-in.",
    step = CustomizeSettings.SCALE_METRICS,
    isVisited = false,
  ),
  CustomizeSettingsCard(
    iconId = AppIcons.Default.WeightOnlyMode,
    title = "Scale Modes",
    subtitle = "Those with specific medical conditions may want to change modes.",
    step = CustomizeSettings.SCALE_MODE,
    isVisited = false,
  ),
  CustomizeSettingsCard(
    iconId = AppIcons.Setup.Scale,
    title = "User Name",
    subtitle = "Change how your name appears on the scale.",
    step = CustomizeSettings.SCALE_USERNAME,
    isVisited = false,
  ),
)
