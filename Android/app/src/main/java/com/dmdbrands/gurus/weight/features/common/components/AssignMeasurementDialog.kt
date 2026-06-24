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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** Sentinel selection id for the "Assign to new baby" row (routes into the Add-a-Baby flow). */
const val ASSIGN_NEW_BABY_ID = "__assign_new_baby__"

/** Max height of the scrollable baby list before it scrolls, keeping the actions on-screen. */
private val BABY_LIST_MAX_HEIGHT = 280.dp

@Composable
fun AssignMeasurementDialog(
    reading: String,
    timestamp: String,
    babies: List<BabyProfile>,
    preSelectedBabyId: String? = null,
    onAssign: (babyId: String) -> Unit,
    onAssignNewBaby: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var selectedBabyId by remember { mutableStateOf(preSelectedBabyId ?: babies.firstOrNull()?.id) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = MeTheme.spacing.md)
                .fillMaxWidth(),
            shape = RoundedCornerShape(MeTheme.borderRadius.lg),
            colors = CardDefaults.cardColors(containerColor = colorScheme.primaryBackground),
        ) {
            Box {
                // Close button (top-right) — 24dp icon
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
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
                ) {
                    AssignMeasurementHeader(reading = reading, timestamp = timestamp)

                    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

                    AssignMeasurementBabyList(
                        babies = babies,
                        selectedBabyId = selectedBabyId,
                        onSelect = { selectedBabyId = it },
                    )

                    Spacer(modifier = Modifier.height(MeTheme.spacing.md))

                    AssignMeasurementActions(
                        onAssign = {
                            if (selectedBabyId == ASSIGN_NEW_BABY_ID) {
                                onAssignNewBaby()
                            } else {
                                selectedBabyId?.let { onAssign(it) }
                            }
                        },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

/** Person icon, title, subtitle and the measurement value at the top of the dialog. */
@Composable
private fun AssignMeasurementHeader(reading: String, timestamp: String) {
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .border(2.dp, colorScheme.textSubheading, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = colorScheme.textSubheading,
            modifier = Modifier.size(40.dp),
        )
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    Text(
        text = ReadingToastStrings.AssignModal.Title,
        style = MeTheme.typography.heading4,
        fontWeight = FontWeight.Bold,
        color = colorScheme.textHeading,
        textAlign = TextAlign.Center,
    )
    Text(
        text = ReadingToastStrings.AssignModal.Subtitle,
        style = MeTheme.typography.body2,
        color = colorScheme.textBody,
        textAlign = TextAlign.Center,
    )
    Text(
        text = rememberMeasurementText(
            text = "$reading · $timestamp",
            type = MeasurementType.BABY,
            valueStyle = MeTheme.typography.heading3,
        ),
        textAlign = TextAlign.Center,
    )
}

/**
 * Scrollable list of baby rows plus the trailing "Assign to new baby" row. Scrolls past
 * [BABY_LIST_MAX_HEIGHT] so the actions stay on-screen with many babies (MOB-598).
 */
@Composable
private fun AssignMeasurementBabyList(
    babies: List<BabyProfile>,
    selectedBabyId: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = BABY_LIST_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    ) {
        babies.forEach { baby ->
            BabyRadioRow(
                baby = baby,
                selected = selectedBabyId == baby.id,
                onClick = { onSelect(baby.id) },
            )
        }
        AssignNewBabyRow(
            selected = selectedBabyId == ASSIGN_NEW_BABY_ID,
            onClick = { onSelect(ASSIGN_NEW_BABY_ID) },
        )
    }
}

/** ASSIGN (primary) and DON'T ASSIGN (dismiss) buttons. */
@Composable
private fun AssignMeasurementActions(
    onAssign: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.textBody),
        onClick = onAssign,
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 160.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = MeTheme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ReadingToastStrings.AssignModal.Assign,
                style = MeTheme.typography.button1,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primaryBackground,
            )
        }
    }
    Box(
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 160.dp)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ReadingToastStrings.AssignModal.DontAssign,
            style = MeTheme.typography.button1,
            fontWeight = FontWeight.Bold,
            color = colorScheme.textSubheading,
        )
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
            .width(310.dp)
            .clip(RoundedCornerShape(MeTheme.borderRadius.md))
            .background(colorScheme.secondaryBackground)
            .border(1.dp, colorScheme.secondaryBackground, RoundedCornerShape(MeTheme.borderRadius.md))
            .clickable { onClick() }
            .padding(MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            // Avatar — 32dp with border
            AppProfileAvatar(text = baby.name, size = 32.dp)
            // Name + age
            Column {
                Text(
                    text = baby.name,
                    style = MeTheme.typography.body2,
                    color = colorScheme.textBody,
                )
                val ageText = baby.birthdate?.let { calculateAge(it) }
                if (ageText != null) {
                    Text(
                        text = ageText,
                        style = MeTheme.typography.body3,
                        color = colorScheme.textSubheading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Radio — 24dp
        AppRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Picker row that, when selected + ASSIGN, opens the Add-a-Baby flow instead of assigning. */
@Composable
private fun AssignNewBabyRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(310.dp)
            .clip(RoundedCornerShape(MeTheme.borderRadius.md))
            .background(colorScheme.secondaryBackground)
            .border(1.dp, colorScheme.secondaryBackground, RoundedCornerShape(MeTheme.borderRadius.md))
            .clickable { onClick() }
            .padding(MeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, colorScheme.textSubheading, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colorScheme.textSubheading,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = ReadingToastStrings.AssignModal.AssignNewBaby,
                    style = MeTheme.typography.body2,
                    color = colorScheme.textBody,
                )
                Text(
                    text = ReadingToastStrings.AssignModal.AssignNewBabySubtitle,
                    style = MeTheme.typography.body3,
                    color = colorScheme.textSubheading,
                )
            }
        }
        AppRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(24.dp),
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
