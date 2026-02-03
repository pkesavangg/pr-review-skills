package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.AppsyncSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppGifImage
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageDefaults
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageSize
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.loading.LoadingTextWithDots
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

@Composable
fun SetupContent(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  noteMessage: String? = null,
  setupFinished: Boolean = false,
  isGifImage: Boolean = false,
  supportingImage: Int? = null,
  loaderText: String? = null ,
  supportingButtonLabel: String? = null,
  onSupportingButtonClick: (() -> Unit)? = null,
  loaderClick: (() -> Unit)? = null,
  connectionState: ConnectionState? = null,
  content: (@Composable () -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
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
          canApplyUppercaseStyle = true,
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

    if (supportingImage != null) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (isGifImage) {
          AppGifImage(
            id = supportingImage,
            modifier = Modifier.size(370.dp, 211.dp),
          )
        } else {
          Image(
            painter = painterResource(id = supportingImage),
            contentDescription = null,
          )
        }

        Spacer(modifier = Modifier.height(spacing.xs))
        if (loaderText != null &&
          (connectionState == ConnectionState.Loading)) {
          Spacer(modifier = Modifier.height(spacing.xs))
          LoadingTextWithDots(
            baseText = loaderText,
            textColor = MeTheme.colorScheme.secondaryAction,
          )
        } else if(loaderText != null && connectionState is ConnectionState.Success ){
          Spacer(modifier = Modifier.height(spacing.xs))
          AppText(
            text = loaderText,
            textType = TextType.Subtitle,
          )
        }
        else if(connectionState is ConnectionState.Failed && loaderText != null) {
          Spacer(modifier = Modifier.height(spacing.xs))
          AppButton(
            label = loaderText,
            type = ButtonType.PrimaryFilled,
            onClick = {
              loaderClick?.invoke()
            },
          )
        }
        noteMessage?.let {
          AppNote(
            message = noteMessage,
            showNote = true,
            modifier = Modifier.padding(start = spacing.sm,end = spacing.sm, top = spacing.sm)
          )
        }

        if (supportingButtonLabel != null && onSupportingButtonClick != null) {
          AppButton(
            label = supportingButtonLabel,
            type = ButtonType.InlineTextPrimary,
            onClick = onSupportingButtonClick,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }
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
