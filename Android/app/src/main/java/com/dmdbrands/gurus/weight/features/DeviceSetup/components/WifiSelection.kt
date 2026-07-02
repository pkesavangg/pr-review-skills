package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.listKey
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.greatergoods.ggbluetoothsdk.external.models.GGWifiInfo

/**
 * Data class representing a WiFi network.
 */
data class WifiNetwork(
  val macAddress: String,
  val password: String,
  val rssi: Int,
  val ssid: String
)

/**
 * Composable that displays a list of WiFi networks for selection.
 *
 * @param wifiList List of WiFi networks to display
 * @param title Title text for the selection screen
 * @param subtitle Subtitle text for the selection screen
 * @param configuredSSID Currently configured WiFi SSID (optional)
 * @param permissionGroups List of permission groups
 * @param onSelect Callback when a WiFi network is selected
 * @param onRefresh Callback when refresh button is pressed
 */
@Composable
fun WifiSelection(
  wifiList: List<GGWifiInfo> = emptyList(),
  title: String,
  subtitle: String,
  configuredSSID: String? = null,
  onSelect: (String) -> Unit,
  onRefresh: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      // .padding(vertical = spacing.md, horizontal = spacing.sm)
      .fillMaxSize(),
    contentPadding = PaddingValues(vertical = spacing.md, horizontal = spacing.sm)
  ) {
    item {
      AppText(
        text = title,
        textType = TextType.ListTitle2,
        modifier = Modifier.padding(bottom = spacing.xs),
      )
    }

    item {
      AppText(
        text = subtitle,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.lg),
      )
    }

    if (wifiList.isEmpty()) {
      item {
        AppText(
          text = DeviceSetupStrings.WifiList.NoNetworks,
          textType = TextType.Body,
          modifier = Modifier.padding(bottom = spacing.lg),
        )
      }
    } else {
      // Connected SSID
      configuredSSID?.let { configuredSsid ->
        val connectedWifi = wifiList.find { it.ssid == configuredSsid }
        connectedWifi?.let { wifi ->
          item {
            AppText(
              text = DeviceSetupStrings.WifiList.ConnectedNetwork,
              textType = TextType.ListTitle1,
              modifier = Modifier.padding(bottom = spacing.xs),
            )
            WifiItem(
              ssid = wifi.ssid,
              isConfigured = true,
              index = 0,
              total = 1,
            )
            Spacer(Modifier.padding(bottom = spacing.sm))
          }
        }
      }

      val availableNetworks = wifiList.filter { it.ssid != configuredSSID }
      if (availableNetworks.isNotEmpty()) {
        item {
          AppText(
            text = DeviceSetupStrings.WifiList.AvailableNetworks,
            textType = TextType.ListTitle1,
            modifier = Modifier.padding(bottom = spacing.xs),
          )
        }

        itemsIndexed(
          items = availableNetworks,
          key = { _, wifi -> wifi.listKey() },
        ) { index, wifi ->
          WifiItem(
            ssid = wifi.ssid,
            isConfigured = false,
            index = index,
            total = availableNetworks.size,
            onClick = { onSelect(wifi.ssid) },
          )
        }
      }
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.height(spacing.md))
        AppButton(
          label = DeviceSetupStrings.SetupButtons.Refresh,
          type = ButtonType.InlineTextPrimary,
          onClick = { onRefresh() },
        )
      }
    }
  }
}

/**
 * Composable that displays a single WiFi network item.
 *
 * @param wifi WiFi network data
 * @param isConfigured Whether this network is currently configured
 * @param onClick Callback when the item is clicked
 */
@Composable
fun WifiItem(
  ssid: String,
  isConfigured: Boolean,
  index: Int,
  total: Int,
  borderRadius: Dp = 0.dp,
  isWifiIcon : Boolean = true,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val shape = when {
    total == 1 -> RoundedCornerShape(MeTheme.borderRadius.sm)
    index == 0 -> RoundedCornerShape(topStart = MeTheme.borderRadius.sm, topEnd = MeTheme.borderRadius.sm)
    index == total - 1 -> RoundedCornerShape(bottomStart = MeTheme.borderRadius.sm, bottomEnd = MeTheme.borderRadius.sm)
    else -> RoundedCornerShape(0.dp)
  }

  Column(
    modifier = Modifier
      .clip(shape)
      .background(colorScheme.primaryBackground),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(spacing.sm)
        .clickable(
          enabled = !isConfigured,
          onClick = { onClick?.invoke() },
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
      if(isWifiIcon){
        AppIcon(
          id = AppIcons.Connection.Wifi,
          contentDescription = DeviceSetupStrings.accWifiNetworkIcon,
          modifier = Modifier.size(32.dp),
          // Decorative within the row: the parent Row.clickable is the single actionable target,
          // so the icon must not be its own focusable/clickable TalkBack stop.
          onClick = null,
        )
      }


      AppText(
        text = ssid,
        textType = TextType.Body,
      )

      Spacer(modifier = Modifier.weight(1f))

      if (!isConfigured) {
        AppIcon(
          // Decorative chevron: the whole row is the actionable target named by the SSID, so the
          // chevron must not be its own clickable node (an unlabeled "double-tap to activate" stop).
          id = AppIcons.Default.RightCaret,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          onClick = null,
        )
      }
    }

    if (index < total - 1) {
      HorizontalDivider(
        color = colorScheme.utility,
        thickness = 0.5.dp,
      )
    }
  }
}

@PreviewTheme()
@Composable
private fun WifiSelectionPreview() {
  MeAppTheme {
    val sampleNetworks = listOf(
      "greatergoods1" to "AA:BB:CC:00:00:01",
      "Home-2.4G" to "AA:BB:CC:00:00:02",
      "Home-5G" to "AA:BB:CC:00:00:03",
      "Cafe Wi-Fi" to "AA:BB:CC:00:00:04",
      "Guest" to "AA:BB:CC:00:00:05",
    ).map { (networkSsid, mac) ->
      GGWifiInfo().apply {
        ssid = networkSsid
        macAddress = mac
      }
    }

    WifiSelection(
      wifiList = sampleNetworks,
      title = "Select WiFi Network",
      subtitle = "Choose a WiFi network to configure your scale",
      configuredSSID = "greatergoods1",
      onSelect = { },
      onRefresh = { },
    )
  }
}
