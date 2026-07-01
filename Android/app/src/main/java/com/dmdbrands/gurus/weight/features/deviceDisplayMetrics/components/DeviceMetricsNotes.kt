package com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper.DeviceMetricsHelper
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.enum.NotifyScaleMode
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.strings.DeviceMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun DeviceMetricsNotes(
  scale: Device,
  modifier: Modifier = Modifier,
  onUpdateScaleMode: () -> Unit = {},
) {
  val notifyScaleMode = DeviceMetricsHelper.getNotifyScaleMode(scale)

  if (notifyScaleMode != NotifyScaleMode.None) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(bottom = spacing.md),
      verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
      // Notification Cards
      when (notifyScaleMode) {
        NotifyScaleMode.WeightOnlyModeOn -> {
          AppNote(
            message = DeviceMetricsSettingStrings.WeightOnlyNotes.Message,
            icon = AppIcons.Default.WeightOnlyMode,
            buttonText = DeviceMetricsSettingStrings.WeightOnlyNotes.UpdateButton,
            onButtonClick = onUpdateScaleMode,

          )
        }

        NotifyScaleMode.UserWeightOnlyModeOn, NotifyScaleMode.UserWeightOnlyModeOnWithHeartRateOff -> {
          AppNote(
            title = DeviceMetricsSettingStrings.OtherUserWeightOnlyModeNote.Title,
            message = DeviceMetricsSettingStrings.OtherUserWeightOnlyModeNote.Message,
            icon = AppIcons.Default.WeightOnlyMode,

          )
        }

        else -> Unit
      }

      if (notifyScaleMode == NotifyScaleMode.HeartRateOff ||
        notifyScaleMode == NotifyScaleMode.UserWeightOnlyModeOnWithHeartRateOff
      ) {
        AppNote(
          message = DeviceMetricsSettingStrings.HeartRateOffNotes.Title,
          icon = AppIcons.Metrics.Pulse,
          iconType = AppIconType.Tertiary,
          buttonText = DeviceMetricsSettingStrings.HeartRateOffNotes.UpdateButton,
          onButtonClick = onUpdateScaleMode,
        )
      }
    }
  }
}
