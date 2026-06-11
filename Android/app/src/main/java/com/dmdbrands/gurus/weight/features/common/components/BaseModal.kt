package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * CustomDialog composable matching the Figma spec (node 6598-69580).
 *
 * @param title The dialog title.
 * @param subtitle The dialog body text.
 * @param primaryAction The primary action button (right, blue).
 * @param secondaryAction The secondary action button (left, tertiary).
 * @param onDismiss Called when the dialog is dismissed (optional, for outside click).
 * @param modifier Modifier for styling.
 */
@Composable
fun BaseModal(
  modifier: Modifier = Modifier,
  primaryActionType: ButtonType = ButtonType.InlineTextPrimary,
  primaryAction: ActionButton? = null,
  title: String? = null,
  subtitle: String? = null,
  body: String? = null,
  error: String? = null,
  secondaryAction: ActionButton? = null,
  onDismiss: (() -> Unit)? = null,
  testTag: String = "modal_card",
  content: @Composable (() -> Unit)? = null,
) {
  val cardColors =
    CardDefaults
      .cardColors(
        containerColor = MeTheme.colorScheme.primaryBackground,
      )

  Card(
    modifier = modifier
      .testTag(testTag)
      .width(316.dp),
    shape = RoundedCornerShape(28.dp), // Figma: radius-xl = 28dp (no token found)
    colors = cardColors,
  ) {
    Column(
      modifier = Modifier
        .padding(MeTheme.spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
      title?.let {
        Text(
          text = it,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textHeading,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      subtitle?.let {
        Text(
          text = subtitle,
          style = MeTheme.typography.body2,
          color = MeTheme.colorScheme.textBody,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      body?.let {
        Text(
          text = body,
          style = MeTheme.typography.body3,
          color = MeTheme.colorScheme.textBody,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Column(Modifier.fillMaxWidth()) {
        content?.let {
          it()
        }
      }
error?.let { s ->
Text(
  text = s.lowercase(),
  style = MeTheme.typography.subHeading2,
  color = MeTheme.colorScheme.textError,
  modifier = Modifier.fillMaxWidth(),
  textAlign = TextAlign.Center,
  )
}

    }
    if (secondaryAction != null || primaryAction != null) {
      Column(
        modifier =
          Modifier
            .padding(top = spacing.xs, bottom = spacing.md, end = spacing.md, start = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        BoxWithConstraints {
          maxWidth

          // Calculate if we need to stack buttons vertically based on text length
          val shouldStackVertically = remember {
            derivedStateOf {
              val totalTextLength = (secondaryAction?.text?.length ?: 0) + (primaryAction?.text?.length ?: 0)
              // If total text length is more than ~25 characters, stack vertically
              // This is a rough estimate - you can adjust this threshold
              totalTextLength >= 25
            }
          }

          if (shouldStackVertically.value) {
            // Stack buttons vertically when text is too long
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.End,
            ) {
              if (secondaryAction != null) {
                AppButton(
                  label = secondaryAction.text,
                  onClick = secondaryAction.action,
                  type = ButtonType.InlineTextTertiary,
                  size = ButtonSize.Small,
                  enabled = secondaryAction.enabled,
                )
              }
              if (primaryAction != null) {
                AppButton(
                  label = primaryAction.text,
                  onClick = primaryAction.action,
                  type = primaryActionType,
                  size = ButtonSize.Small,
                  enabled = primaryAction.enabled,
                )
              }
            }
          } else {
            // Use horizontal layout when text is short enough
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              if (secondaryAction != null) {
                AppButton(
                  label = secondaryAction.text,
                  onClick = secondaryAction.action,
                  type = ButtonType.InlineTextTertiary,
                  size = ButtonSize.Small,
                  enabled = secondaryAction.enabled,
                  modifier = Modifier,
                )
                Spacer(modifier = Modifier.width(MeTheme.spacing.xs))
              }
              if (primaryAction != null) {
                AppButton(
                  label = primaryAction.text,
                  onClick = primaryAction.action,
                  type = primaryActionType,
                  size = ButtonSize.Small,
                  enabled = primaryAction.enabled,
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Preview for CustomDialog in light and dark mode.
 */
@PreviewTheme
@Composable
fun BaseModelPreview() {
  MeAppTheme {
    Column {
      BaseModal(
        title = "Header",
        subtitle = "Body content goes here. This is a sample dialog body.",
        primaryAction = ActionButton(text = "loooooooooooo", action = {}),
        secondaryAction = ActionButton(text = "loooooooooooo", action = {}),
      )
      BaseModal(
        title = "Header",
        primaryAction = ActionButton(text = "Button", action = {}),
      ) {
        Text("Custom content")
      }

      BaseModal(
        title = "Header",
      ) {
        Text("Custom content")
      }
    }
  }
}
