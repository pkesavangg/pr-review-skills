package com.greatergoods.meapp.features.scaleSettings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.helper.ScaleUtility
import com.greatergoods.meapp.features.scaleSettings.strings.ScaleSettingsStrings
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsState
import com.greatergoods.meapp.features.scaleSettings.viewmodel.ScaleSettingsViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import com.greatergoods.meapp.features.common.components.AppScaleCard
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.ScaleInfo

@Composable
fun ScaleSettingsScreen(viewModel: ScaleSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    ScaleSettingsScreenContent(state)
}

@Composable
fun ScaleSettingsScreenContent(state: ScaleSettingsState) {
    val scale = state.scaleInfo
    MeAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.secondaryBackground)
                .padding(horizontal = spacing.md),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md, bottom = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = AppIcons.Default.Close),
                    contentDescription = ScaleSettingsStrings.Close,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { /* TODO: handle close */ },
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                androidx.compose.material3.Text(
                    text = scale.productName,
                    style = MeTheme.typography.body2,
                    modifier = Modifier.weight(1f),
                )
            }
            // Scale Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        id = ScaleUtility.scaleImageResource(scale.sku) ?: AppIcons.Default.ScalePlaceholder,
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            // Settings Section
            SectionHeader(ScaleSettingsStrings.Settings)
            SettingsItem(ScaleSettingsStrings.Mode, ScaleSettingsStrings.AllBodyMetrics)
            SettingsItem(ScaleSettingsStrings.DisplayMetrics, "")
            SettingsItem(ScaleSettingsStrings.Users, "Kristin")
            SettingsItem(ScaleSettingsStrings.ScaleName, scale.productName)
            Spacer(modifier = Modifier.height(spacing.md))
            // Connection Section
            SectionHeader(ScaleSettingsStrings.Connection)
            SettingsItem(ScaleSettingsStrings.Bluetooth, ScaleSettingsStrings.Connected, AppIcons.Connection.Bluetooth)
            SettingsItem(ScaleSettingsStrings.WiFi, "greatergoods1", AppIcons.Connection.Wifi)
            SettingsItem(ScaleSettingsStrings.WiFiMacAddress, "00:11:22:33:44:55")
            Spacer(modifier = Modifier.height(spacing.md))
            // Support Section
            SectionHeader(ScaleSettingsStrings.Support)
            SettingsItem(
                ScaleSettingsStrings.ScaleType,
                ScaleSettingsStrings.BluetoothWiFi,
                AppIcons.Connection.BluetoothWifi,
            )
            SettingsItem(ScaleSettingsStrings.Sku, scale.sku)
            SettingsItem(ScaleSettingsStrings.DatePaired, "June 27, 2023")
            SettingsItem(ScaleSettingsStrings.ProductGuide, "", trailingIcon = AppIcons.Default.RightCaret)
            Spacer(modifier = Modifier.height(spacing.md))
            // Delete
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: handle delete */ }
                        .padding(vertical = spacing.md, horizontal = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = AppIcons.Default.Delete),
                        contentDescription = ScaleSettingsStrings.DeleteScale,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    androidx.compose.material3.Text(
                        text = ScaleSettingsStrings.DeleteScale,
                        color = colorScheme.textError,
                        style = MeTheme.typography.body4,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(spacing.md))
    androidx.compose.material3.Text(
        text = title,
        style = MeTheme.typography.heading6,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SettingsItem(
    label: String,
    value: String,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Image(
                painter = painterResource(id = leadingIcon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(spacing.sm))
        }
        androidx.compose.material3.Text(
            text = label,
            style = MeTheme.typography.body2,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            androidx.compose.material3.Text(
                text = value,
                style = MeTheme.typography.body2,
            )
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(spacing.sm))
            Image(
                painter = painterResource(id = trailingIcon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@PreviewTheme
@Composable
fun ScaleSettingsScreenPreview() {
    val dummyState = ScaleSettingsState(
        scaleInfo = ScaleInfo(
                productName = "AppSync Body Fat Scale",
                sku = "0341",
                imgPath = null,
                setupType = ScaleSetupType.Bluetooth,
                bodyComp = true,
            )
    )
    ScaleSettingsScreenContent(state = dummyState)
}
