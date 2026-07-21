package com.dmdbrands.gurus.weight.features.DeviceSetup.components

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoaderConstants.FailedIndicatorOnlySpacerHeight
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoaderConstants.SetupGifImageHeight
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoaderConstants.SetupGifImageWidth
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupLoaderDefaults.getIcon
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupLoaderDefaults.getIndicationStatus
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LoaderIconType
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.SetupLoaderStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppGifImage
import com.dmdbrands.gurus.weight.features.common.components.AppDeviceImage
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.ConnectionIndicator
import com.dmdbrands.gurus.weight.features.common.components.ConnectionIndicatorState
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.DeviceImageSize
import com.dmdbrands.gurus.weight.features.common.components.SetupLoader
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Default values and helpers for DeviceSetupLoader component.
 */
object SetupLoaderDefaults {
  /**
   * Returns the appropriate icon resource for the given loader icon type.
   */
  @Composable
  fun getIcon(iconType: LoaderIconType): Int = when (iconType) {
    LoaderIconType.Measurement -> AppIcons.Default.WgLogo
    LoaderIconType.Error -> AppIcons.Default.ErrorIndicator
    LoaderIconType.Bluetooth -> AppIcons.Default.BluetoothIndicator
    LoaderIconType.Wifi -> AppIcons.Default.WifiIndicator
  }

  @Composable
  fun getIndicationStatus(state: ConnectionState): ConnectionIndicatorState =
    when (state) {
      ConnectionState.Loading,
      ConnectionState.Success -> ConnectionIndicatorState.Connecting

      else -> ConnectionIndicatorState.Failed
    }
}

/**
 * SetupLoader component that displays scale setup progress with various states.
 *
 * @param connectionState The current connection state
 * @param modifier The modifier to be applied to the component
 * @param scaleImageSku Optional scale SKU/model identifier for displaying scale image
 * @param title Optional main title text
 * @param subtitle Optional subtitle text
 * @param errorCode Optional error code to display
 * @param showIndicationOnly Whether to show connection indicator instead of dots
 * @param indicatorIcon Optional indicator icon type for connection indicator
 * @param primaryButtonText Optional primary button text
 * @param secondaryButtonText Optional secondary button text
 * @param primaryButtonClick Optional primary button click handler
 * @param secondaryButtonClick Optional secondary button click handler
 */
@Composable
fun DeviceSetupLoader(
  connectionState: ConnectionState? = null,
  modifier: Modifier = Modifier,
  scaleImageSku: String? = null,
  title: String? = null,
  subtitle: String? = null,
  errorCode: String? = null,
  showIndicationOnly: Boolean = false,
  setupImage: Int? = null,
  isGifImage: Boolean = false,
  indicatorIcon: LoaderIconType = LoaderIconType.Bluetooth,
  primaryButtonText: String = DeviceSetupStrings.SetupButtons.TryAgain,
  secondaryButtonText: String = DeviceSetupStrings.SetupButtons.Support,
  contentButtonText: String? = null,
  primaryButtonClick: (() -> Unit)? = null,
  secondaryButtonClick: (() -> Unit)? = null,
  contentButtonClick: (() -> Unit)? = null
) {
  val isFailedWithIndicatorOnly = connectionState is ConnectionState.Failed && showIndicationOnly

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()).padding(horizontal = spacing.sm, vertical = spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    // Main content centered vertically
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Title and subtitle section
      DeviceSetupLoaderTexts(
        title = title,
        subtitle = subtitle,
        errorCode = errorCode,
        connectionState = connectionState,
        isFailedWithIndicatorOnly = isFailedWithIndicatorOnly,
      )

      Spacer(modifier = Modifier.height(spacing.lg))

      DeviceSetupLoaderScaleImage(scaleImageSku = scaleImageSku)

      if (setupImage != null) {
        DeviceSetupLoaderSetupImage(
          setupImage = setupImage,
          isGifImage = isGifImage,
          contentButtonText = contentButtonText,
          contentButtonClick = contentButtonClick,
        )
      }

      // Connection Indicator or Setup Loader
      connectionState?.let {
        DeviceSetupLoaderIndicator(
          connectionState = it,
          showIndicationOnly = showIndicationOnly,
          indicatorIcon = indicatorIcon,
        )
      }

      // Buttons at the bottom (if provided)
      if (primaryButtonClick != null) {
        DeviceSetupLoaderButtons(
          isFailedWithIndicatorOnly = isFailedWithIndicatorOnly,
          primaryButtonText = primaryButtonText,
          primaryButtonClick = primaryButtonClick,
          secondaryButtonText = secondaryButtonText,
          secondaryButtonClick = secondaryButtonClick,
        )
      }

    }

  }
}

@Composable
private fun DeviceSetupLoaderScaleImage(scaleImageSku: String?) {
  // Scale Image - map SKU for display (e.g., 0022 -> 0383)
  scaleImageSku?.let { rawSku ->
    AppDeviceImage(
      sku = DeviceHelper.mapSkuForDisplay(rawSku) ?: rawSku,
      scaleImageSize = DeviceImageSize.Large,
    )
    Spacer(modifier = Modifier.height(spacing.lg))
  }
}

@Composable
private fun DeviceSetupLoaderTexts(
  title: String?,
  subtitle: String?,
  errorCode: String?,
  connectionState: ConnectionState?,
  isFailedWithIndicatorOnly: Boolean,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if(isFailedWithIndicatorOnly) {
      Spacer(modifier = Modifier.height(spacing.x2l))
    }
    // Title
    title?.let {
      AppText(
        text = title,
        textType = TextType.Title,
        textAlign = TextAlign.Center,
        // TalkBack: loader/status title is the heading.
        modifier = Modifier
          .fillMaxWidth()
          .semantics { heading() }
          .then(
            if (connectionState is ConnectionState.Failed) {
              Modifier.padding(top = spacing.md)
            } else {
              Modifier
            },
          ),
      )
    }

    Spacer(modifier = Modifier.height(spacing.xs))

    // Subtitle
    subtitle?.let {
      AppText(
        text = subtitle,
        textType = TextType.Body,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    // Error Code (if provided)
    errorCode?.let {
      AppText(
        text = "${SetupLoaderStrings.ErrorCodeLabel}$errorCode",
        textType = TextType.Body,
        textAlign = TextAlign.Center,
        // TalkBack: announce the error code when it appears.
        modifier = Modifier
          .fillMaxWidth()
          .semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

@Composable
private fun DeviceSetupLoaderSetupImage(
  setupImage: Int,
  isGifImage: Boolean,
  contentButtonText: String?,
  contentButtonClick: (() -> Unit)?,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (isGifImage) {
      AppGifImage(
        id = setupImage,
        modifier = Modifier.size(SetupGifImageWidth, SetupGifImageHeight),
      )
    } else {
      Image(
        painter = painterResource(id = setupImage),
        contentDescription = null,
      )
    }

    Spacer(modifier = Modifier.height(spacing.xs))
    if (contentButtonText != null && contentButtonClick != null) {
      AppButton(
        label = contentButtonText,
        type = ButtonType.InlineTextPrimary,
        onClick = contentButtonClick,
        modifier = Modifier.padding(top = spacing.xs)
      )
    }
  }
}

@Composable
private fun DeviceSetupLoaderIndicator(
  connectionState: ConnectionState,
  showIndicationOnly: Boolean,
  indicatorIcon: LoaderIconType,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (showIndicationOnly) {
      ConnectionIndicator(
        indicatorIcon = getIcon(indicatorIcon),
        connectionState = getIndicationStatus(connectionState),
      )
    } else {
      SetupLoader(
        connectionState = connectionState,
      )
      Spacer(modifier = Modifier.height(spacing.md))
      ConnectionIndicator(
        indicatorIcon = getIcon(indicatorIcon),
        connectionState = getIndicationStatus(connectionState),
        showIndicatorAlone = true,
      )
    }
  }
}

@Composable
private fun DeviceSetupLoaderButtons(
  isFailedWithIndicatorOnly: Boolean,
  primaryButtonText: String,
  primaryButtonClick: () -> Unit,
  secondaryButtonText: String,
  secondaryButtonClick: (() -> Unit)?,
) {
  Column(
    modifier = Modifier
      .padding(top = spacing.x2l),
  ) {
    if(isFailedWithIndicatorOnly) {
      Spacer(modifier = Modifier.height(FailedIndicatorOnlySpacerHeight))
    }
    AppButton(
      label = primaryButtonText,
      type = ButtonType.PrimaryFilled,
      onClick = primaryButtonClick,
    )
    if (secondaryButtonClick != null) {
      Spacer(modifier = Modifier.height(spacing.xs))
      AppButton(
        label = secondaryButtonText,
        type = ButtonType.InlineTextPrimary,
        onClick = secondaryButtonClick,
      )
    }
  }
}

@PreviewTheme
@Composable
private fun PreviewScaleSetupLoaderConnectionError() {
  MeAppTheme {
    DeviceSetupLoader(
      scaleImageSku = "0412",
      title = "Connection Error",
      subtitle = "Something went wrong during setup",
      errorCode = "ERR_001",
      connectionState = ConnectionState.Failed.Error,
      indicatorIcon = LoaderIconType.Error,
      primaryButtonText = SetupLoaderStrings.TryAgainButton,
      secondaryButtonText = SetupLoaderStrings.SupportButton,
      primaryButtonClick = { },
      secondaryButtonClick = { },
    )
  }
}

@PreviewTheme
@Composable
private fun PreviewScaleSetupLoaderWifiConnecting() {
  MeAppTheme {
    DeviceSetupLoader(
      scaleImageSku = "0397",
      title = "Connecting to WiFi",
      subtitle = "Please wait while we connect your scale to WiFi",
      connectionState = ConnectionState.Failed.Error,
      showIndicationOnly = true,
      indicatorIcon = LoaderIconType.Wifi,
    )
  }
}

@PreviewTheme
@Composable
private fun PreviewScaleSetupLoaderWithButtons() {
  MeAppTheme {
    DeviceSetupLoader(
      title = "Connecting to Scale",
      subtitle = "Please wait while we establish connection",
      connectionState = ConnectionState.Failed.Error,
      showIndicationOnly = true,
      indicatorIcon = LoaderIconType.Measurement,
      primaryButtonText = "Try Again",
      primaryButtonClick = { },
      secondaryButtonText = "Support",
      secondaryButtonClick = { },
    )
  }
}
