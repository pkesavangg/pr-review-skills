package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.enum.NotifyScaleMode
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.strings.ScaleMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun ScaleMetricsNotes(
  scale: Device,
  modifier: Modifier = Modifier,
  onUpdateScaleMode: () -> Unit = {},
) {
  val notifyScaleMode = ScaleMetricsHelper.getNotifyScaleMode(scale)

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
            message = ScaleMetricsSettingStrings.WeightOnlyNotes.Message,
            icon = AppIcons.Default.WeightOnlyMode,
            buttonText = ScaleMetricsSettingStrings.WeightOnlyNotes.UpdateButton,
            onButtonClick = onUpdateScaleMode,
          )
        }

        NotifyScaleMode.UserWeightOnlyModeOn, NotifyScaleMode.UserWeightOnlyModeOnWithHeartRateOff -> {
          AppNote(
            title = ScaleMetricsSettingStrings.OtherUserWeightOnlyModeNote.Title,
            message = ScaleMetricsSettingStrings.OtherUserWeightOnlyModeNote.Message,
            icon = AppIcons.Default.WeightOnlyMode,
          )
        }

        else -> Unit
      }

      if (notifyScaleMode == NotifyScaleMode.HeartRateOff ||
        notifyScaleMode == NotifyScaleMode.UserWeightOnlyModeOnWithHeartRateOff
      ) {
        AppNote(
          message = ScaleMetricsSettingStrings.HeartRateOffNotes.Title,
          icon = AppIcons.Metrics.Pulse,
          iconType = AppIconType.Tertiary,
          buttonText = ScaleMetricsSettingStrings.HeartRateOffNotes.UpdateButton,
          onButtonClick = onUpdateScaleMode,
        )
      }
    }
  }
}
