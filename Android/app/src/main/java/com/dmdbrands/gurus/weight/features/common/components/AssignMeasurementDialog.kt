package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
                .padding(horizontal = MeTheme.spacing.lg)
                .fillMaxWidth(),
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
                        .padding(horizontal = MeTheme.spacing.lg, vertical = MeTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

                    // Person icon in circle (matching Figma)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, colorScheme.textSubheading, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colorScheme.textSubheading,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                    // Title
                    Text(
                        text = ReadingToastStrings.AssignModal.Title,
                        style = MeTheme.typography.heading4,
                        fontWeight = FontWeight.Bold,
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

                    // Reading value
                    Text(
                        text = rememberMeasurementText(
                            text = "$reading · $timestamp",
                            type = MeasurementType.BABY,
                            valueStyle = MeTheme.typography.heading3,
                        ),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                    // Baby list
                    babies.forEach { baby ->
                        BabyRadioRow(
                            baby = baby,
                            selected = selectedBabyId == baby.id,
                            onClick = { selectedBabyId = baby.id },
                        )
                    }

                    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))

                    // ASSIGN button
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.textBody),
                        onClick = { selectedBabyId?.let { onAssign(it) } },
                        modifier = Modifier.fillMaxWidth(0.6f),
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

                    // DON'T ASSIGN
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

                    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
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
        AppProfileAvatar(text = baby.name, size = 40.dp)
        Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = baby.name,
                style = MeTheme.typography.body1,
                fontWeight = FontWeight.SemiBold,
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
            modifier = Modifier.width(48.dp),
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
            reading = "179.2 lbs",
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
