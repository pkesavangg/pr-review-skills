package com.dmdbrands.gurus.weight.features.ScaleModeSettings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AnnotationPosition
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.AppToggle
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.scaleMode.strings.ScaleModeStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Reusable mode screen content that displays mode selection and heart rate toggle.
 *
 * @param isAllBodyMetrics Current mode selection (true for all body metrics, false for weight only)
 * @param isHeartRateOn Current heart rate toggle state
 * @param onModeSelected Callback when mode selection changes
 * @param onHeartRateToggle Callback when heart rate toggle changes
 * @param onBioimpedanceClick Callback when bioimpedance link is clicked
 */
@Composable
fun ScaleModeSettingsScreen(
  isAllBodyMetrics: Boolean,
  isHeartRateOn: Boolean,
  onModeSelected: (Boolean) -> Unit,
  onHeartRateToggle: (Boolean) -> Unit,
  onBioimpedanceClick: () -> Unit,
) {
  val modeOptions =
    listOf(
      SegmentButtonData(0, ScaleModeStrings.AllBodyMetrics),
      SegmentButtonData(1, ScaleModeStrings.WeightOnly),
    )
  val selectedMode = if (isAllBodyMetrics) modeOptions[0] else modeOptions[1]

  Column(
    modifier =
      Modifier
        .fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    // Description with link
    Column(verticalArrangement = Arrangement.Center) {
      AppText(
        text = ScaleModeStrings.BioimpedanceDescription.format(ScaleModeStrings.BioimpedanceTitle),
        annotatedText = ScaleModeStrings.BioimpedanceTitle,
        annotationPosition = AnnotationPosition.Middle,
        spanStyle =
          SpanStyle(
            color = colorScheme.primaryAction,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
          ),
        textType = TextType.Body,
        onAnnotationClick = { onBioimpedanceClick() },
      )
    }
    // Mode selector
    SegmentButtonGroup(
      data = modeOptions,
      key = SegmentButtonData::label,
      selectedData = selectedMode,
      onSelected = {
        onModeSelected(it.id == 0)
      },
      size = SegmentButtonSize.Large,
      type = SegmentButtonType.Single,
    )
    if (isAllBodyMetrics) {
      // Heart Rate toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
      ) {
        AppIcon(
          id = AppIcons.Metrics.Pulse,
          contentDescription = ScaleModeStrings.HeartRate(isHeartRateOn),
          type = if(isHeartRateOn) AppIconType.Default else AppIconType.Tertiary,
        )
        AppText(
          text = ScaleModeStrings.HeartRate(isHeartRateOn),
          textType = TextType.Body,
        )
        Spacer(modifier = Modifier.weight(1f))
        AppToggle(
          checked = isHeartRateOn,
          onCheckedChange = { onHeartRateToggle(it) },
        )
      }
      AppText(
        text = ScaleModeStrings.HeartRateDescription,
        textType = TextType.Body,
      )
      Surface(
        color = colorScheme.inverseAction,
        shape = RoundedCornerShape(borderRadius.sm),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
      ) {
        AppText(
          text = ScaleModeStrings.NoteMedical,
          annotatedText = ScaleModeStrings.Note,
          annotationPosition = AnnotationPosition.Start,
          spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
          textType = TextType.Body,
          modifier = Modifier.padding(spacing.md),
        )
      }
    } else {
      // Weight Only Mode UI
      Column(
      ) {
        Row(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
          AppIcon(
            id = AppIcons.Default.WeightOnlyMode,
            contentDescription = "Weight Only Mode",
            type = AppIconType.Default,
          )
          AppText(
            text = ScaleModeStrings.WeightOnlyIndicator,
            textType = TextType.Body,

          )
        }
        Column(
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
          Image(
            painter =
              painterResource(
                id = AppIcons.Default.WeightOnlyModeScale,
              ),
            contentDescription = null,
            modifier = Modifier.height(240.dp).align(Alignment.CenterHorizontally).padding(bottom = spacing.lg),
          )

          AppNote(
            message = ScaleModeStrings.NoteOtherUsers,
            showNote = true,
          )
        }
      }
    }
  }
}

@PreviewTheme
@Composable
fun ScaleModeSettingsScreenPreview() {
  ScaleModeSettingsScreen(
    isAllBodyMetrics = true,
    isHeartRateOn = false,
    onModeSelected = {},
    onHeartRateToggle = {},
    onBioimpedanceClick = {},
  )
}
