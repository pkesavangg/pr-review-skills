package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Set a Goal popup modal that encourages users to set a weight goal.
 * Displays information about goal setting and navigation to goal screen.
 *
 * @param onSetGoal Called when the user taps "LET'S DO IT" to set a goal
 * @param onClose Called when the close button is pressed
 * @param modifier Modifier for styling
 */
@Composable
fun SetGoalPopup(
    onSetGoal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {

        BaseModal(
          onDismiss = onClose,
          content = {
            Box(modifier = Modifier.fillMaxWidth()) {
              // Close button (top right)
              AppIconButton(
                id = AppIcons.Default.Close,
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .size(20.dp),
                contentDescription = AppPopupStrings.SetGoalPopup.CloseContentDescription,
                type = AppIconButtonType.Primary,
                onClick = onClose,
              )

              Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                AppText(
                  text = AppPopupStrings.SetGoalPopup.Title,
                  textType = TextType.Title,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

                AppText(
                  text = AppPopupStrings.SetGoalPopup.Message,
                  textType = TextType.Body,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

                AppButton(
                  label = AppPopupStrings.SetGoalPopup.ConfirmButton,
                  type = ButtonType.PrimaryFilled,
                  size = ButtonSize.Large,
                  onClick = onSetGoal,
                )

                Spacer(modifier = Modifier.height(MeTheme.spacing.md))
              }
            }
          },
        )
      }

@PreviewTheme
@Composable
fun SetGoalPopupPreview() {
    MeAppTheme {
        SetGoalPopup(
            onSetGoal = {},
            onClose = {},
        )
    }
}
