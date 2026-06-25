package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.helper.MeasurementType
import com.dmdbrands.gurus.weight.features.common.helper.rememberMeasurementText
import com.dmdbrands.gurus.weight.features.common.model.ReadingToast
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

private fun ProductType.toMeasurementType(): MeasurementType = when (this) {
    ProductType.MY_WEIGHT -> MeasurementType.WEIGHT
    ProductType.BLOOD_PRESSURE -> MeasurementType.BLOOD_PRESSURE
    ProductType.BABY -> MeasurementType.BABY
}

@Composable
fun ReadingArrivalCard(
    modifier: Modifier = Modifier,
    readingToast: ReadingToast,
    clearToast: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .testTag("reading_toast_card")
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
            // Multiple readings buffered this session — surface a count + "VIEW" pill (MOB-598).
            if (readingToast.additionalCount > 0) {
                ReadingCountPill(
                    count = readingToast.additionalCount,
                    onView = {
                        readingToast.onView()
                        clearToast()
                    },
                )
            }
            when {
                // Manual entry: already saved — show "saved to your log" + a single VIEW action.
                readingToast.savedToLog -> SavedToLogContent(readingToast, clearToast)

                readingToast.type == ProductType.BABY && readingToast.noBabyProfile ->
                    NoBabyContent(readingToast, clearToast)

                readingToast.type == ProductType.BABY && readingToast.assignedTo != null ->
                    BabyAssignedContent(readingToast, clearToast)

                else -> ReadingContent(readingToast, clearToast)
            }
        }
    }
}

/** Main card content: title, reading value, primary/secondary action buttons. */
@Composable
private fun ReadingContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    val measurementType = readingToast.type.toMeasurementType()
    // A single baby exists — show its name and SAVE/DISCARD instead of the assign picker (MOB-598).
    val singleBabyName = readingToast.assignTargetName?.takeIf { readingToast.type == ProductType.BABY }
    val title = if (singleBabyName != null) {
        ReadingToastStrings.titleForBaby(singleBabyName)
    } else {
        ReadingToastStrings.title(readingToast.type)
    }
    val primaryLabel = if (singleBabyName != null) {
        ReadingToastStrings.Save
    } else {
        ReadingToastStrings.primaryAction(readingToast.type)
    }
    val secondaryLabel = if (singleBabyName != null) {
        ReadingToastStrings.Discard
    } else {
        ReadingToastStrings.secondaryAction(readingToast.type)
    }

    Text(
        text = title,
        style = MeTheme.typography.heading5,
        color = colorScheme.textBody,
    )
    Text(
        text = rememberMeasurementText(
            text = "${readingToast.reading} · ${readingToast.timestamp}",
            type = measurementType,
            valueStyle = MeTheme.typography.heading4,
        ),
    )
    ReadingActionRow(
        secondaryLabel = secondaryLabel,
        primaryLabel = primaryLabel,
        onSecondary = {
            readingToast.secondaryAction()
            clearToast()
        },
        onPrimary = {
            readingToast.primaryAction()
            clearToast()
        },
    )
}

/**
 * Manual-entry confirmation: "New Reading saved to your log", the reading value, and a single
 * VIEW pill that opens the entry's History detail (Figma 30456-24170).
 */
@Composable
private fun SavedToLogContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    val measurementType = readingToast.type.toMeasurementType()
    Text(
        text = ReadingToastStrings.SavedToLog,
        style = MeTheme.typography.heading5,
        color = colorScheme.textBody,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rememberMeasurementText(
                text = "${readingToast.reading} · ${readingToast.timestamp}",
                type = measurementType,
                valueStyle = MeTheme.typography.heading4,
            ),
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.textBody),
            onClick = {
                readingToast.onView()
                clearToast()
            },
        ) {
            Text(
                text = ReadingToastStrings.View,
                style = MeTheme.typography.button2,
                fontWeight = FontWeight.Bold,
                color = colorScheme.toastBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Shared bottom action row: a secondary text button and a filled primary pill button. */
@Composable
private fun ReadingActionRow(
    secondaryLabel: String,
    primaryLabel: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = secondaryLabel,
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textSubheading,
            modifier = Modifier
                .clickable(role = Role.Button) { onSecondary() }
                .padding(vertical = 6.dp),
        )
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.textBody),
            onClick = onPrimary,
        ) {
            Text(
                text = primaryLabel,
                style = MeTheme.typography.button2,
                fontWeight = FontWeight.Bold,
                color = colorScheme.toastBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Top-of-card pill: "<N> more readings received for this session   VIEW" (MOB-598). */
@Composable
private fun ReadingCountPill(
    count: Int,
    onView: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ReadingToastStrings.moreReadings(count),
                style = MeTheme.typography.body3,
                color = colorScheme.textBody,
            )
            Text(
                text = ReadingToastStrings.View,
                style = MeTheme.typography.button2,
                fontWeight = FontWeight.Bold,
                color = colorScheme.textBody,
                modifier = Modifier.clickable(role = Role.Button) { onView() },
            )
        }
    }
}

/**
 * Baby reading arrived but no baby profile exists to save it to.
 * Offers a DISCARD action and an "ADD A BABY" CTA that deep-links to the add-a-baby flow.
 */
@Composable
private fun NoBabyContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    val measurementType = readingToast.type.toMeasurementType()

    Text(
        text = ReadingToastStrings.NoBabyTitle,
        style = MeTheme.typography.heading5,
        color = colorScheme.textBody,
    )
    Text(
        text = ReadingToastStrings.NoBabySubtitle,
        style = MeTheme.typography.body2,
        color = colorScheme.textBody,
    )
    Text(
        text = rememberMeasurementText(
            text = "${readingToast.reading} · ${readingToast.timestamp}",
            type = measurementType,
            valueStyle = MeTheme.typography.heading4,
        ),
    )
    ReadingActionRow(
        secondaryLabel = ReadingToastStrings.Discard,
        primaryLabel = ReadingToastStrings.AddBaby,
        onSecondary = {
            readingToast.secondaryAction()
            clearToast()
        },
        onPrimary = {
            readingToast.primaryAction()
            clearToast()
        },
    )
}

/** Baby-only post-assignment state: value, "Reading assigned to X", Reassign link. */
@Composable
private fun BabyAssignedContent(
    readingToast: ReadingToast,
    clearToast: () -> Unit,
) {
    val measurementType = readingToast.type.toMeasurementType()

    Text(
        text = rememberMeasurementText(
            text = readingToast.reading,
            type = measurementType,
            valueStyle = MeTheme.typography.heading4,
        ),
    )
    Text(
        text = ReadingToastStrings.assignedTo(readingToast.assignedTo ?: ""),
        style = MeTheme.typography.body2,
        color = colorScheme.textBody,
    )
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // No other baby to reassign to → offer "Assign to new baby" → Add-a-Baby flow (MOB-598).
        if (!readingToast.assignToNewBaby) {
            Text(
                text = ReadingToastStrings.WrongBaby,
                style = MeTheme.typography.body2,
                color = colorScheme.textSubheading,
            )
        }
        Text(
            text = if (readingToast.assignToNewBaby) {
                ReadingToastStrings.AssignModal.AssignNewBaby
            } else {
                ReadingToastStrings.Reassign
            },
            style = MeTheme.typography.button2,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textBody,
            modifier = Modifier.clickable(role = Role.Button) {
                readingToast.primaryAction()
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
                reading = "14 lbs 6 oz",
                type = ProductType.BABY,
                timestamp = "Just now",
            ),
        )
    }
}

@PreviewTheme
@Composable
private fun ReadingArrivalCardNoBabyPreview() {
    MeAppTheme {
        ReadingArrivalCard(
            readingToast = ReadingToast(
                reading = "14 lbs 6 oz",
                type = ProductType.BABY,
                timestamp = "Just now",
                noBabyProfile = true,
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
                reading = "14 lbs 6 oz",
                type = ProductType.BABY,
                timestamp = "Just now",
                assignedTo = "Emma",
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
                reading = "149.2 lbs",
                type = ProductType.MY_WEIGHT,
                timestamp = "Just now",
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
                reading = "120/80 mmhg pulse 65",
                type = ProductType.BLOOD_PRESSURE,
                timestamp = "Just now",
            ),
        )
    }
}
