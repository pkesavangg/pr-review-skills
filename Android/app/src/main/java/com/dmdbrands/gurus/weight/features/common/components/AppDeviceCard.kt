package com.dmdbrands.gurus.weight.features.common.components

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
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Card composable for displaying a DeviceModelInfo's info as per Figma.
 *
 * @param scale The scale info to display.
 * @param isSavedScale Whether the scale is saved (shows connection status and right caret if true).
 * @param onClick Callback when the card is clicked, provides the selected scale info.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppDeviceCard(
  scale: DeviceModelInfo,
  modifier: Modifier = Modifier,
  horizontalSpacing: Dp? = null,
  isSavedScale: Boolean,
  enabled: Boolean = true,
  canShowRightCaret: Boolean = true,
  onClick: (DeviceModelInfo) -> Unit,
) {
  val cardSpacing = if (isSavedScale) spacing.md else spacing.sm
  val connectionIcon = DeviceDataHelper.scaleTypeIcon(scale.setupType)
  val isBpm = DeviceHelper.isBpmDevice(scale.sku)
  val isWifiSetup =
    scale.setupType == DeviceSetupType.Wifi ||
      scale.setupType == DeviceSetupType.EspTouchWifi ||
      scale.setupType == DeviceSetupType.BtWifiR4
  val isBluetoothSetup =
    scale.setupType == DeviceSetupType.Bluetooth ||
      scale.setupType == DeviceSetupType.Lcbt ||
      scale.setupType == DeviceSetupType.BtWifiR4
  val showConnectionStatus =
    isSavedScale && isBluetoothSetup && !isBpm

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .debounceClick { onClick(scale) },
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
      AppDeviceImage(sku = scale.sku)

      Spacer(modifier = Modifier.width(spacing.sm))
      val displaySku = scale.sku
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
      ) {
        AppText(
          text = displaySku,
          textType = TextType.ListTitle1,
        )
        AppText(
          text = scale.productName.lowercase(),
          textType = TextType.ListSubtitle,
          textOverflow = TextOverflow.Ellipsis,
          softWrap = false,
        )
        if (isBpm && isSavedScale && scale.userNumber != null) {
          val displayUser = DeviceDataHelper.formatUserDisplay(scale.hasNumericUsers, scale.userNumber)
          if (displayUser.isNotEmpty()) {
            Spacer(modifier = Modifier.height(spacing.x3s))
            AppText(
              text = "${AppListStrings.User} $displayUser",
              textType = TextType.Body,
            )
          }
        }
        if (showConnectionStatus) {
          Spacer(modifier = Modifier.height(spacing.x3s))
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Only flag incomplete WiFi setup when we are sure: BtWifiR4 scale connected over BT
            // but reporting WiFi unconfigured. We require isConnected==true so that "unknown"
            // (null isWifiConfigured prior to first connection) does not trigger a false alarm.
            val showExclamation =
              scale.isWifiConfigured != true &&
              scale.isConnected == true &&
              scale.setupType == DeviceSetupType.BtWifiR4
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
              // Decorative: the connection status is already announced by the adjacent
              // "Connected"/"Not Connected"/"Setup Incomplete" text within this card.
              contentDescription = null,
              type = iconType,
              enabled = scale.isConnected == true,
              onClick = null,
            )
            Spacer(modifier = Modifier.width(spacing.x3s))
            AppText(
              text =
                when {
                  scale.isConnected == false && isBluetoothSetup -> AppListStrings.NotConnected
                  showExclamation -> AppListStrings.SetupIncomplete
                  scale.isConnected == true && isBluetoothSetup -> AppListStrings.Connected
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
          // Decorative: the scale type/name is already announced by the SKU and
          // product-name text in this card.
          contentDescription = null,
          type = AppIconType.Primary,
          modifier = Modifier.size(32.dp),
          enabled = enabled,
          onClick = null,
        )
        Spacer(modifier = Modifier.width(spacing.sm))
      }
if(canShowRightCaret){
  AppIcon(
    id = AppIcons.Default.RightCaret,
    // Decorative caret: the whole card is already a single clickable element, so the
    // navigation affordance does not need a separate spoken label.
    contentDescription = null,
    type = AppIconType.Primary,
    modifier = Modifier.size(32.dp),
    enabled = enabled,
    onClick = { onClick(scale) },
  )
}
    }
  }
  HorizontalDivider(thickness = 0.5.dp, color = colorScheme.utility)
}

@PreviewTheme
@Composable
fun PreviewAppScaleCard() {
  MeAppTheme {
    Column {
      // DeviceModelInfo example 1
      AppDeviceCard(
        scale =
          DeviceModelInfo(
            productName = "AccuCheck Verve Smart Scale",
            sku = "0412",
            setupType = DeviceSetupType.BtWifiR4,
            bodyComp = true,
            isConnected = true,
            isWifiConfigured = true,
            scaleId = "scaleId1",
          ),
        isSavedScale = true,
        onClick = {},
      )

      // DeviceModelInfo example 2
      AppDeviceCard(
        scale =
          DeviceModelInfo(
            productName = "Bluetooth Smart Scale",
            sku = "0375",
            setupType = DeviceSetupType.Bluetooth,
            bodyComp = false,
            isConnected = false,
            isWifiConfigured = false,
            scaleId = "scaleId2",
          ),
        isSavedScale = true,
        onClick = {},
      )

      // DeviceModelInfo example 3
      AppDeviceCard(
        scale =
          DeviceModelInfo(
            productName = "Wi-Fi Smart Scale",
            sku = "0384",
            setupType = DeviceSetupType.Wifi,
            bodyComp = true,
            isConnected = false,
            isWifiConfigured = false,
            scaleId = null,
          ),
        isSavedScale = false,
        onClick = {},
      )

      // BPM saved device example (User A)
      AppDeviceCard(
        scale =
          DeviceModelInfo(
            productName = "Smart Blood Pressure Monitor",
            sku = "0634",
            setupType = DeviceSetupType.Bluetooth,
            bodyComp = false,
            isConnected = true,
            scaleId = "scaleId3",
            hasNumericUsers = false,
            userNumber = 1,
          ),
        isSavedScale = true,
        onClick = {},
      )
    }
  }
}

