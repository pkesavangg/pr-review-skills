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
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * @param registeredDevices every device registered so far this session. The title names ALL
 *   of them (in the fixed Figma order via [DeviceReadyStrings.readyTitle]) — one device reads
 *   "Your <device> profile is ready!", two read "Your <a> & <b> profiles are ready!" — so a
 *   second completed device is no longer dropped from the header. (MOB-1453)
 * @param onFinish invoked when the user taps FINISH — signup is complete.
 * @param onConnectAnother invoked when the user taps CONNECT ANOTHER DEVICE
 *   to loop back to PICK_DEVICE.
 */
@Composable
fun DeviceReadyStep(
    registeredDevices: Set<ProductType>,
    onFinish: () -> Unit,
    onConnectAnother: () -> Unit,
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
                text = DeviceReadyStrings.readyTitle(registeredDevices),
                textType = TextType.Title,
                textAlign = TextAlign.Center,
                // TalkBack: the success title is a heading for by-heading navigation.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
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
                modifier = Modifier.testTag(TestTags.Signup.FinishButton),
                onClick = onFinish,
            )
            AppButton(
                label = DeviceReadyStrings.connectAnother,
                type = ButtonType.TextPrimary,
                size = ButtonSize.Large,
                modifier = Modifier.testTag(TestTags.Signup.ConnectAnotherDeviceButton),
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
            registeredDevices = setOf(ProductType.MY_WEIGHT),
            onFinish = {},
            onConnectAnother = {},
        )
    }
}

@PreviewTheme
@Composable
private fun DeviceReadyStepTwoDevicesPreview() {
    MeAppTheme {
        DeviceReadyStep(
            registeredDevices = setOf(ProductType.MY_WEIGHT, ProductType.BLOOD_PRESSURE),
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
            registeredDevices = setOf(ProductType.BABY),
            onFinish = {},
            onConnectAnother = {},
        )
    }
}
