package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

private fun valueColor(type: ProductType): Color = when (type) {
    ProductType.MY_WEIGHT -> SnapshotColors.Weight
    ProductType.BLOOD_PRESSURE -> SnapshotColors.BloodPressure
    ProductType.BABY -> SnapshotColors.Baby
}

@Composable
fun ReadingArrivalCard(
    modifier: Modifier = Modifier,
    readingToast: ReadingToast,
    clearToast: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .cssBoxShadow(
                color = colorScheme.glow,
                offsetX = 2.dp,
                offsetY = 2.dp,
                blurRadius = 8.dp,
                spread = 0.dp,
                cornerRadius = 10.dp,
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.toastBackground,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (readingToast.assignedTo != null) {
                AssignedContent(readingToast, clearToast)
            } else {
                UnassignedContent(readingToast, clearToast)
            }
        }
    }
}

/** Renders value + unit pairs with accent color for values and muted color for units. */
@Composable
private fun ValueDisplay(readingToast: ReadingToast) {
    val accent = valueColor(readingToast.type)
    val unitOffset = Modifier.offset(y = (-4).dp)

    Row(verticalAlignment = Alignment.Bottom) {
        // Primary value + unit
        Text(
            text = readingToast.value,
            style = MeTheme.typography.heading4,
            color = accent,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = readingToast.unit,
            style = MeTheme.typography.subHeading2,
            color = colorScheme.textSubheading,
            modifier = unitOffset,
        )

        // Secondary value + unit (baby: oz portion)
        if (readingToast.secondaryValue != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = readingToast.secondaryValue,
                style = MeTheme.typography.heading4,
                color = accent,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = readingToast.secondaryUnit ?: "",
                style = MeTheme.typography.subHeading2,
                color = colorScheme.textSubheading,
                modifier = unitOffset,
            )
        }

        // Pulse (BPM only)
        if (readingToast.pulse != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = ReadingToastStrings.Pulse,
                style = MeTheme.typography.subHeading2,
                color = colorScheme.textSubheading,
                modifier = unitOffset,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${readingToast.pulse}",
                style = MeTheme.typography.heading4,
                color = accent,
            )
        }

        // Timestamp
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "· ${readingToast.timestamp}",
            style = MeTheme.typography.body2,
            color = colorScheme.textSubheading,
            modifier = unitOffset,
        )
    }
}

@Composable
private fun UnassignedContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    Text(
        text = ReadingToastStrings.title(readingToast.type),
        style = MeTheme.typography.heading5,
        color = colorScheme.textBody,
    )
    ValueDisplay(readingToast)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ReadingToastStrings.secondaryAction(readingToast.type),
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textSubheading,
            modifier = Modifier
                .clickable {
                    readingToast.onDismiss()
                    clearToast()
                }
                .padding(vertical = 6.dp),
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.textBody,
            ),
            onClick = {
                readingToast.onAssign()
                clearToast()
            },
        ) {
            Text(
                text = ReadingToastStrings.primaryAction(readingToast.type),
                style = MeTheme.typography.button2,
                fontWeight = FontWeight.Bold,
                color = colorScheme.toastBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AssignedContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    ValueDisplay(readingToast)
    Text(
        text = ReadingToastStrings.assignedTo(readingToast.assignedName ?: ""),
        style = MeTheme.typography.body2,
        color = colorScheme.textBody,
    )
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = ReadingToastStrings.WrongBaby,
            style = MeTheme.typography.body2,
            color = colorScheme.textSubheading,
        )
        Text(
            text = ReadingToastStrings.Reassign,
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textBody,
            modifier = Modifier.clickable {
                readingToast.onReassign()
            },
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardBabyPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "14", unit = "lbs",
                secondaryValue = "6", secondaryUnit = "oz",
                timestamp = "Just now",
                type = ProductType.BABY,
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardAssignedPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "14", unit = "lbs",
                secondaryValue = "6", secondaryUnit = "oz",
                timestamp = "Just now",
                type = ProductType.BABY,
                assignedTo = "baby-123",
                assignedName = "Emma",
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardWeightPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "149.2", unit = "lbs",
                timestamp = "Just now",
                type = ProductType.MY_WEIGHT,
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardBpmPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                value = "120/80", unit = "mmhg",
                pulse = 65,
                timestamp = "Just now",
                type = ProductType.BLOOD_PRESSURE,
            ),
        )
    }
}
