package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.dmdbrands.gurus.weight.domain.enums.ProductType
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

/**
 * Per-device success step for the multi-device signup flow (MA-3825).
 *
 * Shown after the user completes one device's onboarding when at least one
 * device is still unregistered. Mirrors Figma node 29507:21935.
 *
 * @param deviceId the registered device's id (matches [ProductType.id]).
 *   Used to render the device-specific title.
 * @param onFinish invoked when the user taps FINISH — signup is complete.
 * @param onConnectAnother invoked when the user taps CONNECT ANOTHER DEVICE
 *   to loop back to PICK_DEVICE.
 */
@Composable
fun DeviceReadyStep(
    deviceId: String,
    onFinish: () -> Unit,
    onConnectAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName =
        ProductType.fromId(deviceId)?.displayName.orEmpty()

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
                text = DeviceReadyStrings.deviceTitle(displayName),
                textType = TextType.Title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
                onClick = onConnectAnother,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.md))
        }
    }
}

@PreviewTheme
@Composable
private fun DeviceReadyStepWeightScalePreview() {
    MeAppTheme {
        DeviceReadyStep(
            deviceId = ProductType.MY_WEIGHT.id,
            onFinish = {},
            onConnectAnother = {},
        )
    }
}

@PreviewTheme
@Composable
private fun DeviceReadyStepBpmPreview() {
    MeAppTheme {
        DeviceReadyStep(
            deviceId = ProductType.BLOOD_PRESSURE.id,
            onFinish = {},
            onConnectAnother = {},
        )
    }
}

@PreviewTheme
@Composable
private fun DeviceReadyStepBabyPreview() {
    MeAppTheme {
        DeviceReadyStep(
            deviceId = ProductType.BABY.id,
            onFinish = {},
            onConnectAnother = {},
        )
    }
}
