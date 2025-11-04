package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun AppDialog(
  title: String? = null,
  body: String,
  confirmAction: ActionButton,
  modifier: Modifier = Modifier,
  primaryActionType: ButtonType = ButtonType.InlineTextPrimary,
  dismissAction: ActionButton? = null,
  properties: DialogProperties = DialogProperties(),
) {
  val dismissActionEvent = dismissAction?.action ?: confirmAction.action
  Dialog(
    onDismissRequest = dismissActionEvent,
    properties = DialogProperties(
      dismissOnBackPress = properties.dismissOnBackPress,
      dismissOnClickOutside = properties.dismissOnClickOutside,
      securePolicy = properties.securePolicy,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      // Background overlay - only clickable on areas not covered by modal
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MeTheme.colorScheme.overlay)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = dismissActionEvent
          ),
      )

      // Modal content - positioned on top of overlay
      Box(modifier = Modifier.align(Alignment.Center)) {
        BaseModal(
          modifier,
          title = title,
          subtitle = body,
          primaryActionType = primaryActionType,
          primaryAction = confirmAction,
          secondaryAction = dismissAction,
          onDismiss = dismissActionEvent,
        ) { }
      }
    }
  }
}

@PreviewTheme
@Composable
fun AppDialogPreview() {
  AppDialog(
    title = "Sample Title",
    body = "This is a sample dialog body",
    confirmAction = ActionButton("OK") {},
  )

  // Preview without title
  AppDialog(
    body = "This is a dialog without a title",
    confirmAction = ActionButton("OK") {},
  )
}
