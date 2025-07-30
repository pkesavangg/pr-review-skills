package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * A simple note component for displaying informational messages with optional title, icon, and action button.
 *
 * @param message The main text content of the note.
 * @param modifier Modifier for the note container.
 * @param showNote Whether to prefix the message with "Note: " (annotated and bold).
 * @param title Optional title text displayed prominently above the message.
 * @param icon Optional icon resource ID displayed at the start.
 * @param buttonText Optional button text. When provided, displays a TextPrimary button.
 * @param onButtonClick Click handler for the button (required if buttonText is provided).
 */
@Composable
fun AppNote(
  message: String,
  modifier: Modifier = Modifier,
  showNote: Boolean = false,
  title: String? = null,
  icon: Int? = null,
  iconType: AppIconType = AppIconType.Primary,
  messageType: TextType = TextType.SubHeading,
  buttonText: String? = null,
  onButtonClick: (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = colorScheme.inverseAction,
    shape = RoundedCornerShape(borderRadius.sm),
    shadowElevation = 0.dp,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(spacing.sm),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
      ) {
        // Icon
        if (icon != null) {
          AppIcon(
            id = icon,
            contentDescription = title ?: "Note",
            modifier = Modifier.size(24.dp),
            type = iconType,
          )
        }

        // Title or message
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          if (title != null) {
            AppText(
              text = title,
              textType = TextType.NoteTitle,
            )
          } else {
            AppText(
              text = message,
              annotatedText = if (showNote) "Note: " else null,
              annotationPosition = AnnotationPosition.Start,
              spanStyle = if (showNote) SpanStyle(fontWeight = FontWeight.Bold) else null,
              textType = messageType,
            )
          }
        }

        // Inline button
        if (buttonText != null && onButtonClick != null) {
          AppButton(
            label = buttonText,
            type = ButtonType.InlineTextPrimary,
            size = ButtonSize.Small,
            onClick = onButtonClick,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
        }
      }

      // Optional second-line message (only if title is given)
      if (title != null) {
        AppText(
          text = message,
          annotatedText = if (showNote) "Note:" else null,
          annotationPosition = AnnotationPosition.Start,
          spanStyle = if (showNote) SpanStyle(fontWeight = FontWeight.Bold) else null,
          textType = TextType.SubHeading,
        )
      }
    }
  }
}

@PreviewTheme
@Composable
private fun AppNotePreview() {
  MeAppTheme {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. showNote=true
      AppNote(
        message = "Other users can temporarily enable All Body Metrics for one session.",
        showNote = true,
      )

      // 2. Heart Rate: OFF
      AppNote(
        message = "Heart Rate: OFF",
        icon = AppIcons.Metrics.Pulse,
      )

      // 3. Weight Only with button
      AppNote(
        message = "Weight Only: On",
        icon = AppIcons.Default.WeightOnlyMode,
        buttonText = "ENABLE BODY METRICS",
        onButtonClick = {},
      )

      // 4. Full example with button
      AppNote(
        title = "A user has Weight Only Mode on",
        message = "Only weight and BMI will be collected. You can temporarily enable All Body Metrics from scale settings.",
        icon = AppIcons.Default.WeightOnlyMode,
      )
    }
  }
}
