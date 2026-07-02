package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Composable to display error content with consistent styling
 * @param errorCode The error code to display (e.g., "t163", "t204")
 * @param errorMessage Optional error message to display (will override the mapped error message if provided)
 * @param modifier Modifier to apply to the content
 */
@Composable
fun ErrorContent(
  errorCode: String,
  modifier: Modifier = Modifier,
) {
  // Get the error message from the mapping, similar to TypeScript get errorMessage()
  val mappedErrorMessage = WifiScaleSetupStrings.WifiErrors.getErrorMessage(errorCode)

  Column(
    modifier = modifier
      .fillMaxSize().verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    // Error title
    AppText(
      text = "${WifiScaleSetupStrings.ErrorDetail.Troubleshooting} - $errorCode",
      textType = TextType.Title,
      // TalkBack: error/troubleshooting title is the heading.
      modifier = Modifier.semantics { heading() },
    )

    // Error message if available (similar to TypeScript ion-card-subtitle)
    if (mappedErrorMessage != null) {
      AppNote(
        message = mappedErrorMessage,
        showNote = false,
      )
    }

    // Error-specific content
    when (errorCode.lowercase()) {
      "t204" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T204a,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T204b,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T204c,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      "t205" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T205a,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T205b,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      "t163", "t206", "t323" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T163a,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T163b,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T163c,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T163d,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      "t164", "t315", "t325" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T164a,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T164b,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      "t165" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.T165,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      "other" -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.Other,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      else -> {
        AppText(
          text = WifiScaleSetupStrings.ErrorDetail.Copy,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // Default content for non-special error codes
    if (!listOf("t163", "t206", "t323").contains(errorCode.lowercase())) {
      AppText(
        text = WifiScaleSetupStrings.ErrorDetail.Copy,
        textType = TextType.Body,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@PreviewTheme
@Composable
private fun ErrorContentPreview() {
  MeAppTheme {
    ErrorContent(
      errorCode = "t163",
    )
  }
}
