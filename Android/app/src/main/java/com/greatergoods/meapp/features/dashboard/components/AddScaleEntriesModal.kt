package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.theme.MeAppTheme
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Modal dialog for adding scale entries with configurable count and period.
 *
 * @param onDismiss Called when the modal is dismissed.
 * @param onEntriesGenerated Called with the generated list of ScaleEntry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScaleEntriesModal(
    onDismiss: () -> Unit,
    onEntriesGenerated: (List<ScaleEntry>) -> Unit,
) {
    var entryCountText by remember { mutableStateOf("") }
    var entryCount by remember { mutableStateOf(1) }
    var period by remember { mutableStateOf(PeriodType.WEEK) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scale Entries", style = MeAppTheme.typography.heading2) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = entryCountText,
                    onValueChange = { value ->
                        entryCountText = value
                        value.toIntOrNull()?.let { entryCount = it.coerceAtLeast(1) }
                    },
                    label = { Text("Number of Entries") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                Button(
                    onClick = { isDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(period.displayName)
                }
                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                ) {
                    PeriodType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                period = type
                                isDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEntriesGenerated(generateScaleEntries(entryCount, period))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate Entries")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Enum representing the period type for entry generation.
 */
enum class PeriodType(val displayName: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ANY("Any/Total")
}

/**
 * Generates a list of ScaleEntry objects based on the count and period.
 *
 * @param count Number of entries to generate.
 * @param period PeriodType for distribution.
 * @return List of ScaleEntry.
 */
private fun generateScaleEntries(count: Int, period: PeriodType): List<ScaleEntry> {
    val now = LocalDateTime.now()
    val zone = ZoneId.of("America/Los_Angeles")
    val weightList = (50..70).toList()
    val daysRange = when (period) {
        PeriodType.WEEK -> 6
        PeriodType.MONTH -> 29
        PeriodType.YEAR -> 364
        PeriodType.ANY -> 365 * 10 - 1
    }
    val daysList = when (period) {
        PeriodType.ANY -> List(count) { Random.nextInt(0, daysRange) }
        else -> if (count > daysRange + 1) List(count) { it % (daysRange + 1) } else (0..daysRange).shuffled()
            .take(count)
    }
    return daysList.mapIndexed { index, daysAgo ->
        val date = now.minusDays(daysAgo.toLong()).withHour(8 + (index % 12)).withMinute(0).withSecond(0).withNano(0)
        val timestamp = ZonedDateTime.of(date, zone).toInstant().toEpochMilli()
        ScaleEntry(
            entry = com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity(
                id = (1000 + index).toLong(),
                accountId = "account_${1000 + index}",
                entryTimestamp = timestamp,
                serverTimestamp = null,
                opTimestamp = null,
                operationType = "CREATE",
                deviceType = "SCALE",
                deviceId = "device_${1000 + index}",
                attempts = 0,
                isSynced = false,
            ),
            scale = com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics(
                scaleEntry = com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity(
                    id = (1000 + index).toLong(),
                    weight = weightList.random(),
                    bodyFat = 200 + index,
                    muscleMass = 200 + index,
                    water = 400 + index,
                    bmi = 2200 + index,
                    source = "manual",
                ),
                scaleEntryMetric = null,
            ),
        )
    }
}
