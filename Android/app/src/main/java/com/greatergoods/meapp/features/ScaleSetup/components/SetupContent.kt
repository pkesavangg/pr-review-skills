package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.greatergoods.meapp.features.ScaleSetup.strings.AppsyncSetupStrings
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleImageDefaults
import com.greatergoods.meapp.features.common.components.ScaleImageSize
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun SetupContent(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  setupFinished: Boolean = false,
  supportingImage: Int? = null,
  content: (@Composable () -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = spacing.md, horizontal = spacing.sm)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AppText(
        text = title,
        textType = TextType.Title,
        modifier = Modifier.fillMaxWidth(),
      )
      subtitle?.let {
        AppText(
          text = subtitle,
          textType = TextType.Body,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    if (setupFinished) {
      Image(
        painter = painterResource(id = AppIcons.Setup.SetupCompleteCheck),
        contentDescription = "Setup Completed",
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .size(ScaleImageDefaults.size(ScaleImageSize.Large)),
      )
    }

    supportingImage?.let {
      Image(
        painter = painterResource(id = supportingImage),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }

    content?.let {
      content()
    }

  }
}

@PreviewTheme
@Composable
private fun SetupContentPreview() {
  MeAppTheme {
    SetupContent(
      title = AppsyncSetupStrings.SetupComplete.Title,
      subtitle = AppsyncSetupStrings.SetupComplete.Message,
      setupFinished = true,
      supportingImage = AppIcons.Setup.AppSyncNavBar,
    )
  }
}
