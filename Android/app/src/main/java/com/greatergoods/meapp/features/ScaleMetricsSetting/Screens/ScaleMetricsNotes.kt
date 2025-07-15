package com.greatergoods.meapp.features.ScaleMetricsSetting.Screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.greatergoods.meapp.features.ScaleMetricsSetting.enum.NotifyScaleMode
import com.greatergoods.meapp.features.ScaleMetricsSetting.strings.ScaleMetricsSettingStrings
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppNote
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme.spacing

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
