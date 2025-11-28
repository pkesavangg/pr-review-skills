package com.dmdbrands.gurus.weight.features.goal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.goal.strings.EmptyGoalStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Empty goal card component that displays when no goal is set.
 * Shows a call-to-action to set a goal weight.
 *
 * @param onSetGoalClick Callback when the "Set a goal weight" button is clicked
 * @param inEditMode Whether in edit mode (disables the button)
 * @param modifier Modifier for styling
 */
@Composable
fun EmptyGoal(
  onSetGoalClick: () -> Unit,
  inEditMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppStyledCard(
      modifier =
        Modifier
          .clip(shape = RoundedCornerShape(MeTheme.borderRadius.sm))
          .background(colorScheme.primaryBackground)
          .padding(vertical = spacing.md, horizontal = 30.dp),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
      ) {
        // Title
        AppText(
          text = EmptyGoalStrings.Title,
          textType = TextType.Title,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.padding(bottom = spacing.xs))
        // Button
        AppButton(
          label = EmptyGoalStrings.ButtonText,
          type = ButtonType.SuccessFilled,
          size = ButtonSize.Small,
          onClick = onSetGoalClick,
          enabled = !inEditMode,
        )
      }
    }
  }
}

// --- Preview Section ---
@PreviewTheme
@Composable
private fun EmptyGoalLightPreview() {
  MeAppTheme {
    EmptyGoal(
      onSetGoalClick = { /* Preview action */ },
      inEditMode = false,
      modifier = Modifier.padding(16.dp)
    )
  }
}
@PreviewTheme
@Composable
private fun EmptyGoalDarkPreview() {
  MeAppTheme {
    EmptyGoal(
      onSetGoalClick = { /* Preview action */ },
      inEditMode = true,
      modifier = Modifier.padding(16.dp)
    )
  }
}
