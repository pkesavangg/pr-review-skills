package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.composition.LocalCardAlignment
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.PickDeviceStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private const val DISABLED_CARD_ALPHA = 0.2f

/**
 * Represents a selectable device option for the signup flow.
 */
data class DeviceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
)

/**
 * Step for selecting which device the user will use with meApp.
 * Shows Baby Scale, Blood Pressure Monitor, and Weight Scale options.
 *
 * Devices already registered in this signup session (passed via
 * [registeredDevices]) are rendered disabled with an "already added"
 * subtitle and a check indicator — see MA-3825.
 */
@Composable
fun PickDeviceStep(
    deviceControl: FormControl<String>,
    modifier: Modifier = Modifier,
    registeredDevices: Set<ProductType> = emptySet(),
    onDeviceSelected: (String) -> Unit = {},
) {
    val devices = listOf(
        DeviceOption(
            id = PickDeviceStrings.Devices.BABY_SCALE,
            title = PickDeviceStrings.babyScaleTitle,
            subtitle = PickDeviceStrings.babyScaleSubtitle,
            iconResId = AppIcons.Default.BabyScale,
        ),
        DeviceOption(
            id = PickDeviceStrings.Devices.BLOOD_PRESSURE,
            title = PickDeviceStrings.bloodPressureTitle,
            subtitle = PickDeviceStrings.bloodPressureSubtitle,
            iconResId = AppIcons.Default.BloodPressureMonitor,
        ),
        DeviceOption(
            id = PickDeviceStrings.Devices.WEIGHT_SCALE,
            title = PickDeviceStrings.weightScaleTitle,
            subtitle = PickDeviceStrings.weightScaleSubtitle,
            iconResId = AppIcons.Default.WeightScale,
        ),
    )

    AppStyledCard(
        cardAlignmentType = LocalCardAlignment.current,
        modifier = modifier,
    ) {
        AppText(PickDeviceStrings.title, TextType.Title, spacing = MeTheme.spacing.xs)
        AppText(
            text = PickDeviceStrings.addLaterNote,
            textType = TextType.SubHeading,
            color = MeTheme.colorScheme.textSubheading,
            spacing = MeTheme.spacing.lg,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            devices.forEach { device ->
                val isRegistered =
                    ProductType.fromId(device.id)?.let { it in registeredDevices } == true
                DeviceCard(
                    device = device,
                    isSelected = deviceControl.value == device.id,
                    isRegistered = isRegistered,
                    onClick = { if (!isRegistered) onDeviceSelected(device.id) },
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceOption,
    isSelected: Boolean,
    isRegistered: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isRegistered) DISABLED_CARD_ALPHA else 1f)
            .clip(RoundedCornerShape(MeTheme.borderRadius.sm))
            .background(MeTheme.colorScheme.primaryBackground)
            .clickable(enabled = !isRegistered, onClick = onClick)
            .padding(horizontal = MeTheme.spacing.md, vertical = MeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(device.iconResId),
            contentDescription = device.title,
            modifier = Modifier.size(56.dp),
        )

        Spacer(modifier = Modifier.width(MeTheme.spacing.md))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = device.title,
                style = MeTheme.typography.heading5,
                color = MeTheme.colorScheme.textBody,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
            Text(
                text = if (isRegistered) PickDeviceStrings.alreadyAdded else device.subtitle,
                style = MeTheme.typography.body3,
                color = MeTheme.colorScheme.textSubheading,
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            enabled = !isRegistered,
            colors = RadioButtonDefaults.colors(
                selectedColor = MeTheme.colorScheme.primaryAction,
                unselectedColor = MeTheme.colorScheme.utility,
                disabledSelectedColor = MeTheme.colorScheme.utility,
                disabledUnselectedColor = MeTheme.colorScheme.utility,
            ),
        )
    }
}

@PreviewTheme
@Composable
fun PickDeviceStepPreview() {
    MeAppTheme {
        PickDeviceStep(
            deviceControl = FormControl.create("baby_scale", listOf(FormValidations.required())),
        )
    }
}

@PreviewTheme
@Composable
fun PickDeviceStepEmptyPreview() {
    MeAppTheme {
        PickDeviceStep(
            deviceControl = FormControl.create("", listOf(FormValidations.required())),
        )
    }
}

@PreviewTheme
@Composable
fun PickDeviceStepWithRegisteredPreview() {
    MeAppTheme {
        PickDeviceStep(
            deviceControl = FormControl.create("", listOf(FormValidations.required())),
            registeredDevices = setOf(ProductType.MY_WEIGHT, ProductType.BABY),
        )
    }
}
