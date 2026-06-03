package com.dmdbrands.gurus.weight.features.integration.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun StartConnect(
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dataTypes = listOf(
        HealthConnectStrings.StartConnectStrings.DataTypeWeight,
        HealthConnectStrings.StartConnectStrings.DataTypeBmi,
        HealthConnectStrings.StartConnectStrings.DataTypeBodyFat,
        HealthConnectStrings.StartConnectStrings.DataTypeLeanBodyMass,
        HealthConnectStrings.StartConnectStrings.DataTypeBloodPressure,
        HealthConnectStrings.StartConnectStrings.DataTypeHeartRate,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = AppIcons.Integrations.No_Permission),
            contentDescription = "",
            modifier = Modifier
                .width(190.dp)
                .height(350.dp)
                .padding(top = MeTheme.spacing.md),
        )
        Spacer(Modifier.padding(top = MeTheme.spacing.lg))
        AppText(
            text = HealthConnectStrings.StartConnectStrings.Title,
            textType = TextType.Title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.padding(top = MeTheme.spacing.x2s))
        AppText(
            text = HealthConnectStrings.StartConnectStrings.Description,
            textType = TextType.Subtitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.padding(top = MeTheme.spacing.sm))
        dataTypes.forEach { DataTypeRow(it) }
        Spacer(Modifier.padding(bottom = MeTheme.spacing.x6l))
        AppButton(
            type = ButtonType.PrimaryFilled,
            label = HealthConnectStrings.ActionButtons.connect,
            size = ButtonSize.Large,
            onClick = onPrimaryAction,
        )
    }
}

@Composable
private fun DataTypeRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeTheme.spacing.x2s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            id = AppIcons.Outlined.CheckedCircle,
            contentDescription = "",
            type = AppIconType.Primary,
        )
        Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
        AppText(
            text = label,
            textType = TextType.Body,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StartConnectPreview() {
    MeAppTheme {
        Surface {
            StartConnect(
                onPrimaryAction = {},
                modifier = Modifier
            )
        }
    }
}
