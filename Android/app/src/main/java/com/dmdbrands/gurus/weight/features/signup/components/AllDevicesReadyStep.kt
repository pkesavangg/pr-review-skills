package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.signup.strings.DeviceReadyStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private val SuccessIconSize = 155.dp
private val DeviceIconSize = 75.dp

/**
 * Aggregate success step for the multi-device signup flow (MA-3825).
 *
 * Shown after the user has registered all three devices in this session.
 * Mirrors Figma node 29507:22141 — green check, single shared title, and
 * a horizontal row of all three device icons. CONNECT ANOTHER DEVICE is
 * rendered disabled because nothing is left to connect.
 */
@Composable
fun AllDevicesReadyStep(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeTheme.spacing.md, vertical = MeTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            Image(
                painter = painterResource(id = AppIcons.Setup.BabyScalePairedCheck),
                contentDescription = null,
                modifier = Modifier.size(SuccessIconSize),
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            AppText(
                text = DeviceReadyStrings.allDevicesTitle,
                textType = TextType.Title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            DeviceIconRow()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
        ) {
            AppButton(
                label = DeviceReadyStrings.finish,
                type = ButtonType.PrimaryFilled,
                size = ButtonSize.Large,
                onClick = onFinish,
            )
            AppButton(
                label = DeviceReadyStrings.connectAnother,
                type = ButtonType.TextPrimary,
                size = ButtonSize.Large,
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun DeviceIconRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = AppIcons.Default.BloodPressureMonitor),
            contentDescription = null,
            modifier = Modifier.size(DeviceIconSize),
        )
        Image(
            painter = painterResource(id = AppIcons.Default.BabyScale),
            contentDescription = null,
            modifier = Modifier.size(DeviceIconSize),
        )
        Image(
            painter = painterResource(id = AppIcons.Default.WeightScale),
            contentDescription = null,
            modifier = Modifier.size(DeviceIconSize),
        )
    }
}

@PreviewTheme
@Composable
private fun AllDevicesReadyStepPreview() {
    MeAppTheme {
        AllDevicesReadyStep(onFinish = {})
    }
}
