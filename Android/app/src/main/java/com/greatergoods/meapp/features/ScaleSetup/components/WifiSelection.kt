package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.ButtonType
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.borderRadius
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

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
  wifiList: List<WifiNetwork> = emptyList(),
  title: String,
  subtitle: String,
  configuredSSID: String? = null,
  onSelect: (String) -> Unit,
  onRefresh: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = spacing.md, horizontal = spacing.sm),
  ) {
    AppText(
      text = title,
      textType = TextType.ListTitle2,
      modifier = Modifier.padding(bottom = spacing.xs),
    )
    AppText(
      text = subtitle,
      textType = TextType.Body,
      modifier = Modifier.padding(bottom = spacing.lg),
    )

    if (wifiList.isEmpty()) {
      AppText(
        text = ScaleSetupStrings.WifiList.NoNetworks,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.lg),
      )
    } else {
      LazyColumn {
        // Show connected network section if there's a configured SSID
        configuredSSID?.let { configuredSsid ->
          val connectedWifi = wifiList.find { it.ssid == configuredSsid }
          connectedWifi?.let { wifi ->
            item {

              AppText(
                text = "Connected Network",
                textType = TextType.ListTitle2,
                modifier = Modifier.padding(bottom = spacing.xs),
              )
              Column(
                modifier = Modifier
                  .clip(RoundedCornerShape(borderRadius.sm))
                  .padding(bottom = spacing.sm),
              ) {
                WifiItem(
                  ssid = wifi.ssid,
                  isConfigured = true,
                )
              }
            }
          }
        }

        // Show available networks section
        val availableNetworks = wifiList.filter { it.ssid != configuredSSID }
        if (availableNetworks.isNotEmpty()) {
          item {
            AppText(
              text = "Available Networks",
              textType = TextType.ListTitle2,
              modifier = Modifier.padding(bottom = spacing.xs),
            )
          }

          items(availableNetworks) { wifi ->
            Column(
              modifier = Modifier,
            ) {
              WifiItem(
                ssid = wifi.ssid,
                isConfigured = false,
                onClick = { onSelect(wifi.ssid) },
              )
              if (availableNetworks.size > 1 && availableNetworks.indexOf(wifi) < availableNetworks.size - 1) {
                HorizontalDivider(
                  color = MeTheme.colorScheme.utility,
                  thickness = .5.dp,
                )
              }
            }
          }
        }
      }
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.height(spacing.md))
        AppButton(
          label = ScaleSetupStrings.SetupButtons.Refresh,
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
private fun WifiItem(
  ssid: String,
  isConfigured: Boolean,
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.primaryBackground)
      .padding(spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    AppIcon(
      id = AppIcons.Connection.Wifi,
      contentDescription = "Wifi",
      modifier = Modifier.size(24.dp),
    )

    AppText(
      text = ssid,
      textType = TextType.Body,
    )
    Spacer(modifier = Modifier.weight(1f))
    if (isConfigured || onClick != null) {
      AppIcon(
        id = AppIcons.Default.RightCaret,
        contentDescription = "Right caret",
        modifier = Modifier.size(16.dp),
        onClick = { onClick },
      )
    }
  }
}

@PreviewTheme()
@Composable
private fun WifiSelectionPreview() {
  MeAppTheme {
    WifiSelection(
      wifiList = listOf(
        WifiNetwork(
          macAddress = "a8:63:7d:cd:fb:b0",
          password = "password123",
          rssi = 41,
          ssid = "greatergoods1",
        ),
        WifiNetwork(
          macAddress = "b2:45:8e:12:34:56",
          password = "",
          rssi = 35,
          ssid = "3diq2",
        ),
        WifiNetwork(
          macAddress = "c1:67:9a:78:90:12",
          password = "wifi_pass",
          rssi = 28,
          ssid = "vivo 1904",
        ),
        WifiNetwork(
          macAddress = "d3:89:bc:34:56:78",
          password = "network_key",
          rssi = 22,
          ssid = "MAS-TEL_e6e0",
        ),
        WifiNetwork(
          macAddress = "e4:12:cd:90:12:34",
          password = "admin123",
          rssi = 18,
          ssid = "3DIQAdmin",
        ),
      ),
      title = "Select WiFi Network",
      subtitle = "Choose a WiFi network to configure your scale",
      configuredSSID = "greatergoods1",
      onSelect = { },
      onRefresh = { },
    )
  }
}

@PreviewTheme()
@Composable
private fun WifiSelectionDarkPreview() {
  MeAppTheme {
    WifiSelection(
      wifiList = listOf(
        WifiNetwork(
          macAddress = "a8:63:7d:cd:fb:b0",
          password = "password123",
          rssi = 41,
          ssid = "greatergoods1",
        ),
        WifiNetwork(
          macAddress = "b2:45:8e:12:34:56",
          password = "",
          rssi = 35,
          ssid = "3diq2",
        ),
        WifiNetwork(
          macAddress = "c1:67:9a:78:90:12",
          password = "wifi_pass",
          rssi = 28,
          ssid = "vivo 1904",
        ),
      ),
      title = "Select WiFi Network",
      subtitle = "Choose a WiFi network to configure your scale",
      configuredSSID = null,
      onSelect = { },
      onRefresh = { },
    )
  }
}
