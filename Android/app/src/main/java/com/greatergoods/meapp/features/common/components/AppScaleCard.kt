package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.ScaleInfo
import com.greatergoods.meapp.features.common.strings.AppListStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Card composable for displaying a scale's info as per Figma.
 *
 * @param scale The scale info to display.
 * @param isSavedScale Whether the scale is saved (shows connection status and right caret if true).
 * @param onClick Callback when the card is clicked, provides the selected scale.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppScaleCard(
    scale: ScaleInfo,
    isSavedScale: Boolean,
    onClick: (ScaleInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardSpacing = if (isSavedScale) MeTheme.spacing.md else MeTheme.spacing.sm
    val connectionIcon = when (scale.setupType) {
        ScaleSetupType.Wifi, ScaleSetupType.EspTouchWifi -> AppIcons.Connection.Wifi
        ScaleSetupType.Bluetooth, ScaleSetupType.Lcbt -> AppIcons.Connection.Bluetooth
        ScaleSetupType.BtWifiR4 -> AppIcons.Connection.BluetoothWifi
        ScaleSetupType.AppSync -> TODO()
    }
    val trailingIcon = if (isSavedScale) AppIcons.Default.RightCaret else connectionIcon

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(scale) },
        color = MeTheme.colorScheme.secondaryBackground,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = cardSpacing, vertical = cardSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Placeholder image
            Image(
                painter = painterResource(id = AppIcons.Default.ScalePlaceholder),
                contentDescription = null,
                modifier = Modifier
                    .size(75.dp)
            )
            Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(
                    text = scale.sku,
                    textType = if (isSavedScale) TextType.ListTitle1 else TextType.ListTitle2
                )
                AppText(
                    text = scale.productName,
                    textType = TextType.ListSubtitle,
                )
                if (isSavedScale) {
                    Spacer(modifier = Modifier.height(spacing.x3s))
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        val setupIndicationIcon = if (scale.isWifiConfigured == false) {
                            AppIcons.Default.Exclamation
                        } else {
                            connectionIcon
                        }
                        AppIcon(
                            id = setupIndicationIcon,
                            contentDescription = "Connection type icon",
                            type = AppIconType.Primary,
                            enabled = false,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(spacing.x3s))
                        AppText(
                            text = when {
                                !scale.isConnected!! -> AppListStrings.NotConnected
                                !scale.isWifiConfigured!! -> AppListStrings.SetupIncomplete
                                else -> AppListStrings.Connected
                            } ,
                            textType = TextType.Body,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(MeTheme.spacing.md))
            AppIcon(
                id = trailingIcon,
                contentDescription = if (isSavedScale) "Navigate" else "Scale type icon",
                type = AppIconType.Primary,
                modifier = Modifier.size(32.dp),
                enabled = false,
                onClick = null,
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
            AppScaleCard(
                scale = ScaleInfo(
                    productName = "accucheck verve smart scale",
                    sku = "0412",
                    imgPath = null,
                    setupType = ScaleSetupType.BtWifiR4,
                    bodyComp = true,
                    isConnected = true,
                ),
                isSavedScale = true,
                onClick = {},
            )
            AppScaleCard(
                scale = ScaleInfo(
                    productName = "AppSync Body Fat Scale",
                    sku = "0375",
                    imgPath = null,
                    setupType = ScaleSetupType.Wifi,
                    bodyComp = true,
                    isWifiConfigured = false
                ),
                isSavedScale = true,
                onClick = {},
            )
            AppScaleCard(
                scale = ScaleInfo(
                    productName = "AppSync Body Fat Scale",
                    sku = "0341",
                    imgPath = null,
                    setupType = ScaleSetupType.Bluetooth,
                    bodyComp = true,
                ),
                isSavedScale = false,
                onClick = {},
            )
        }
    }
}
