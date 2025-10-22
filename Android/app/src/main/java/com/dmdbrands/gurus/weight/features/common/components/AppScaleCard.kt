package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import android.util.Log

/**
 * Card composable for displaying a ScaleInfo's info as per Figma.
 *
 * @param scale The scale info to display.
 * @param isSavedScale Whether the scale is saved (shows connection status and right caret if true).
 * @param onClick Callback when the card is clicked, provides the selected scale info.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppScaleCard(
  scale: ScaleInfo,
  modifier: Modifier = Modifier,
  horizontalSpacing: Dp? = null,
  isSavedScale: Boolean,
  enabled: Boolean = true,
  onClick: (ScaleInfo) -> Unit,
) {
  val cardSpacing = if (isSavedScale) spacing.md else spacing.sm
  val connectionIcon = ScaleDataHelper.scaleTypeIcon(scale.setupType)
  val isWifiSetup =
    scale.setupType == ScaleSetupType.Wifi ||
      scale.setupType == ScaleSetupType.EspTouchWifi ||
      scale.setupType == ScaleSetupType.BtWifiR4
  val isBluetoothSetup =
    scale.setupType == ScaleSetupType.Bluetooth ||
      scale.setupType == ScaleSetupType.Lcbt ||
      scale.setupType == ScaleSetupType.BtWifiR4
  val showConnectionStatus =
    isSavedScale && isBluetoothSetup

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { onClick(scale) },
    color = colorScheme.secondaryBackground,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = horizontalSpacing ?: cardSpacing, vertical = cardSpacing),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // Placeholder image
      AppScaleImage(sku = scale.sku)

      Spacer(modifier = Modifier.width(spacing.sm))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
      ) {
        AppText(
          text = scale.sku,
          textType = if (isSavedScale) TextType.ListTitle1 else TextType.ListTitle2,
        )
        AppText(
          text = scale.productName.lowercase(),
          textType = TextType.ListSubtitle,
          textOverflow = TextOverflow.Ellipsis
        )
        if (showConnectionStatus) {
          Spacer(modifier = Modifier.height(spacing.x3s))
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Show exclamation only when we're certain WiFi setup is incomplete
            // For BtWifiR4 scales, show exclamation if:
            // 1. Scale is connected (so we can check WiFi status)
            // 2. WiFi is explicitly not configured (isWifiConfigured == false)
            // 3. Scale type is BtWifiR4 (which requires WiFi setup)
            val showExclamation =
              scale.isWifiConfigured != true &&
              scale.isConnected == true &&
              scale.setupType == ScaleSetupType.BtWifiR4
            val setupIndicationIcon =
              if (showExclamation) {
                AppIcons.Default.Exclamation
              } else {
                AppIcons.Connection.Bluetooth
              }
            val iconType = when {
              showExclamation -> AppIconType.Danger
              scale.isConnected == false -> AppIconType.Tertiary
              else -> AppIconType.Primary
            }
            AppIcon(
              id = setupIndicationIcon,
              contentDescription = "Connection type icon",
              type = iconType,
              enabled = scale.isConnected == true,
              onClick = null,
            )
            Spacer(modifier = Modifier.width(spacing.x3s))
            AppText(
              text =
                when {
                  !scale.isConnected!! && isBluetoothSetup -> AppListStrings.NotConnected
                  showExclamation -> AppListStrings.SetupIncomplete
                  scale.isConnected && isBluetoothSetup -> AppListStrings.Connected
                  else -> ""
                },
              textType = TextType.Body,
            )
          }
        }
      }
      Spacer(modifier = Modifier.width(spacing.md))
      if (!isSavedScale) {
        AppIcon(
          id = connectionIcon,
          contentDescription = "Scale type icon",
          type = AppIconType.Primary,
          modifier = Modifier.size(32.dp),
          enabled = enabled,
          onClick = null,
        )
        Spacer(modifier = Modifier.width(spacing.sm))
      }

      AppIcon(
        id = AppIcons.Default.RightCaret,
        contentDescription = if (isSavedScale) "Navigate" else "Scale type icon",
        type = AppIconType.Primary,
        modifier = Modifier.size(32.dp),
        enabled = enabled,
        onClick = { onClick(scale) },
      )
    }
  }
  HorizontalDivider(thickness = 0.5.dp, color = colorScheme.utility)
}

@PreviewTheme
@Composable
fun PreviewAppScaleCard() {
  MeAppTheme {
    Column {
      // ScaleInfo example 1
      AppScaleCard(
        scale =
          ScaleInfo(
            productName = "AccuCheck Verve Smart Scale",
            sku = "0412",
            setupType = ScaleSetupType.BtWifiR4,
            bodyComp = true,
            isConnected = true,
            isWifiConfigured = true,
            scaleId = "scaleId1",
          ),
        isSavedScale = true,
        onClick = {},
      )

      // ScaleInfo example 2
      AppScaleCard(
        scale =
          ScaleInfo(
            productName = "Bluetooth Smart Scale",
            sku = "0375",
            setupType = ScaleSetupType.Bluetooth,
            bodyComp = false,
            isConnected = false,
            isWifiConfigured = false,
            scaleId = "scaleId2",
          ),
        isSavedScale = true,
        onClick = {},
      )

      // ScaleInfo example 3
      AppScaleCard(
        scale =
          ScaleInfo(
            productName = "Wi-Fi Smart Scale",
            sku = "0384",
            setupType = ScaleSetupType.Wifi,
            bodyComp = true,
            isConnected = false,
            isWifiConfigured = false,
            scaleId = null,
          ),
        isSavedScale = false,
        onClick = {},
      )
    }
  }
}

