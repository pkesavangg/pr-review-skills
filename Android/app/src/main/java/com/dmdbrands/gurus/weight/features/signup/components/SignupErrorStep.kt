package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
import com.dmdbrands.gurus.weight.features.signup.strings.SignupErrorStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private val ErrorIconSize = 155.dp
private val ErrorIconGlyphSize = 90.dp
private val ErrorCircleStroke = 6.dp
private val DeviceIconSize = 56.dp

/**
 * Terminal error step for the multi-device signup flow (MOB-420).
 *
 * Shown only when a device's product creation fails (network / data-retrieval /
 * backend). Account-creation failures stay as a toast on the password step and
 * never reach this screen. Mirrors Figma node 30358:24196.
 *
 * The status of each device is derived from existing state:
 *  - in [registeredDevices] → "Added to your profile"
 *  - the current [failedDeviceId] → "Profile couldn't be saved — tap Try Again"
 *  - otherwise → "Not yet started"
 *
 * @param failedDeviceId the device whose product creation failed (matches [ProductType.id]).
 * @param registeredDevices devices already saved successfully this session.
 * @param onFinish invoked on FINISH — completes signup; account + saved devices persist.
 * @param onTryAgain invoked on TRY AGAIN — retries the failed device's product creation.
 */
@Composable
fun SignupErrorStep(
    failedDeviceId: String,
    registeredDevices: Set<ProductType>,
    onFinish: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedDevice = ProductType.fromId(failedDeviceId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MeTheme.spacing.md, vertical = MeTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Content scrolls so the badge + status list never clip on smaller
        // screens; the action buttons stay pinned below.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            ErrorBadge()
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            AppText(
                text = SignupErrorStrings.title,
                textType = TextType.Title,
                textAlign = TextAlign.Center,
                // TalkBack: the error title is a heading for by-heading navigation.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
            AppText(
                text = SignupErrorStrings.subtitle,
                textType = TextType.SubHeading,
                textAlign = TextAlign.Center,
                color = MeTheme.colorScheme.textSubheading,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
            DeviceStatusList(failedDevice = failedDevice, registeredDevices = registeredDevices)
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                label = SignupErrorStrings.finish,
                type = ButtonType.TextPrimary,
                size = ButtonSize.Small,
                onClick = onFinish,
            )
            AppButton(
                label = SignupErrorStrings.tryAgain,
                type = ButtonType.SecondaryFilled,
                size = ButtonSize.Small,
                onClick = onTryAgain,
            )
        }
    }
}

@Composable
private fun ErrorBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ErrorIconSize)
            .border(width = ErrorCircleStroke, color = MeTheme.colorScheme.errorAction, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = AppIcons.Default.Exclamation),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MeTheme.colorScheme.errorAction),
            modifier = Modifier.size(ErrorIconGlyphSize),
        )
    }
}

@Composable
private fun DeviceStatusList(
    failedDevice: ProductType?,
    registeredDevices: Set<ProductType>,
    modifier: Modifier = Modifier,
) {
    // Stable display order mirrors Figma: BPM, Baby Scale, Weight Scale.
    val order = listOf(ProductType.BLOOD_PRESSURE, ProductType.BABY, ProductType.MY_WEIGHT)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        order.forEach { device ->
            val status = when {
                device == failedDevice -> DeviceStatus.FAILED
                device in registeredDevices -> DeviceStatus.ADDED
                else -> DeviceStatus.PENDING
            }
            DeviceStatusCard(device = device, status = status)
        }
    }
}

private enum class DeviceStatus { ADDED, FAILED, PENDING }

@Composable
private fun DeviceStatusCard(device: ProductType, status: DeviceStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MeTheme.borderRadius.sm))
            .background(MeTheme.colorScheme.primaryBackground)
            .padding(horizontal = MeTheme.spacing.md, vertical = MeTheme.spacing.md)
            // TalkBack: read the device name + its status as one node (the icon is
            // decorative — the name is already shown as the title text).
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconFor(device)),
            // Decorative: the device name is already spoken as the title text below.
            contentDescription = null,
            modifier = Modifier.size(DeviceIconSize),
        )
        Spacer(modifier = Modifier.width(MeTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            AppText(text = device.displayName, textType = TextType.ListTitle1)
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
            AppText(
                text = statusLabel(status),
                textType = TextType.ListSubtitle,
                color = if (status == DeviceStatus.FAILED) {
                    MeTheme.colorScheme.errorAction
                } else {
                    MeTheme.colorScheme.textSubheading
                },
            )
        }
    }
}

private fun statusLabel(status: DeviceStatus): String = when (status) {
    DeviceStatus.ADDED -> SignupErrorStrings.deviceSuccess
    DeviceStatus.FAILED -> SignupErrorStrings.deviceFailure
    DeviceStatus.PENDING -> SignupErrorStrings.devicePending
}

private fun iconFor(device: ProductType): Int = when (device) {
    ProductType.BLOOD_PRESSURE -> AppIcons.Default.BloodPressureMonitor
    ProductType.BABY -> AppIcons.Default.BabyScale
    ProductType.MY_WEIGHT -> AppIcons.Default.WeightScale
}

@PreviewTheme
@Composable
private fun SignupErrorStepPreview() {
    MeAppTheme {
        SignupErrorStep(
            failedDeviceId = ProductType.BABY.id,
            registeredDevices = setOf(ProductType.BLOOD_PRESSURE),
            onFinish = {},
            onTryAgain = {},
        )
    }
}
