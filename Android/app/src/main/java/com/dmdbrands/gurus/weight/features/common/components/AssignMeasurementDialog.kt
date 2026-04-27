package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.features.common.helper.MeasurementType
import com.dmdbrands.gurus.weight.features.common.helper.rememberMeasurementText
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * Center dialog for assigning a baby scale reading to a baby profile.
 *
 * @param reading The formatted reading string, e.g. "14 lbs 6 oz".
 * @param timestamp The relative timestamp, e.g. "Just now".
 * @param babies List of baby profiles to choose from.
 * @param preSelectedBabyId Optional pre-selected baby ID (for reassign flow).
 * @param onAssign Called with the selected baby ID when ASSIGN is tapped.
 * @param onDismiss Called when the dialog is dismissed (X, DON'T ASSIGN, or outside tap).
 */
@Composable
fun AssignMeasurementDialog(
    reading: String,
    timestamp: String,
    babies: List<BabyProfile>,
    preSelectedBabyId: String? = null,
    onAssign: (babyId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedBabyId by remember { mutableStateOf(preSelectedBabyId ?: babies.firstOrNull()?.id) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .width(316.dp)
                .padding(horizontal = MeTheme.spacing.md),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.primaryBackground),
        ) {
            Box {
                // Close button (top-right)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(MeTheme.spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colorScheme.textSubheading,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Baby avatar icon
                    AppProfileAvatar(
                        text = "",
                        size = 56.dp,
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

                    // Title
                    Text(
                        text = ReadingToastStrings.AssignModal.Title,
                        style = MeTheme.typography.heading4,
                        color = colorScheme.textHeading,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

                    // Subtitle
                    Text(
                        text = ReadingToastStrings.AssignModal.Subtitle,
                        style = MeTheme.typography.body2,
                        color = colorScheme.textBody,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

                    // Reading value with measurement text styling
                    Text(
                        text = rememberMeasurementText(
                            text = "$reading · $timestamp",
                            type = MeasurementType.BABY,
                            valueStyle = MeTheme.typography.heading3,
                        ),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                    // Baby list with radio selection
                    babies.forEach { baby ->
                        BabyRadioRow(
                            baby = baby,
                            selected = selectedBabyId == baby.id,
                            onClick = { selectedBabyId = baby.id },
                        )
                    }

                    Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                    // Primary action — ASSIGN
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.textBody),
                        onClick = {
                            selectedBabyId?.let { onAssign(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = ReadingToastStrings.AssignModal.Assign,
                            style = MeTheme.typography.button1,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primaryBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

                    // Secondary action — DON'T ASSIGN
                    Text(
                        text = ReadingToastStrings.AssignModal.DontAssign,
                        style = MeTheme.typography.button2,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.textSubheading,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BabyRadioRow(
    baby: BabyProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppProfileAvatar(
            text = baby.name,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = baby.name,
                style = MeTheme.typography.body1,
                color = colorScheme.textBody,
            )
            val ageText = baby.birthdate?.let { calculateAge(it) }
            if (ageText != null) {
                Text(
                    text = ageText,
                    style = MeTheme.typography.body2,
                    color = colorScheme.textSubheading,
                )
            }
        }
        AppRadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}

private fun calculateAge(birthdate: String): String? {
    return try {
        val birth = LocalDate.parse(birthdate, DateTimeFormatter.ISO_LOCAL_DATE)
        val months = Period.between(birth, LocalDate.now()).toTotalMonths().toInt()
        ReadingToastStrings.AssignModal.age(months)
    } catch (_: Exception) {
        null
    }
}

@PreviewTheme
@Composable
private fun AssignMeasurementDialogPreview() {
    MeAppTheme {
        AssignMeasurementDialog(
            reading = "14 lbs 6 oz",
            timestamp = "Just now",
            babies = listOf(
                BabyProfile(id = "1", accountId = "acc", name = "Emma", birthdate = "2026-01-15"),
                BabyProfile(id = "2", accountId = "acc", name = "Princy", birthdate = "2026-01-20"),
            ),
            onAssign = {},
            onDismiss = {},
        )
    }
}
