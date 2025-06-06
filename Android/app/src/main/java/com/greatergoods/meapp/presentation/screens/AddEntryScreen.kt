package com.greatergoods.meapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.presentation.viewmodel.EntryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import android.util.Log

@Composable
fun AddEntryScreen(
    viewModel: EntryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    var selectedEntryType by remember { mutableStateOf(EntryType.SCALE) }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var muscleMass by remember { mutableStateOf("") }
    var water by remember { mutableStateOf("") }
    var bmi by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Entry type selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EntryTypeSelector(
                selectedType = selectedEntryType,
                onTypeSelected = { selectedEntryType = it },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Generate Random button
        Button(
            onClick = {
                when (selectedEntryType) {
                    EntryType.SCALE -> {
                        weight = generateRandomWeight().toString()
                        bodyFat = generateRandomBodyFat().toString()
                        muscleMass = generateRandomMuscleMass().toString()
                        water = generateRandomWater().toString()
                        bmi = generateRandomBMI().toString()
                    }
                    EntryType.BPM -> {
                        pulse = generateRandomPulse().toString()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Generate Random Values")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedEntryType) {
            EntryType.SCALE -> {
                // Scale entry form
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bodyFat,
                    onValueChange = { bodyFat = it },
                    label = { Text("Body Fat (%)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = muscleMass,
                    onValueChange = { muscleMass = it },
                    label = { Text("Muscle Mass (%)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = water,
                    onValueChange = { water = it },
                    label = { Text("Water (%)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bmi,
                    onValueChange = { bmi = it },
                    label = { Text("BMI") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EntryType.BPM -> {
                // BPM entry form
                OutlinedTextField(
                    value = pulse,
                    onValueChange = { pulse = it },
                    label = { Text("Pulse (bpm)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                try {
                    val entry = when (selectedEntryType) {
                        EntryType.SCALE -> createScaleEntry(
                            weight = weight.toDoubleOrNull() ?: 0.0,
                            bodyFat = bodyFat.toDoubleOrNull() ?: 0.0,
                            muscleMass = muscleMass.toDoubleOrNull() ?: 0.0,
                            water = water.toDoubleOrNull() ?: 0.0,
                            bmi = bmi.toDoubleOrNull() ?: 0.0,
                        )

                        EntryType.BPM -> createBpmEntry(
                            pulse = pulse.toIntOrNull() ?: 0,
                        )
                    }
                    Log.i("CHECKING", "Created entry: $entry")
                    viewModel.addEntry(entry)
                    onNavigateBack()
                } catch (e: Exception) {
                    Log.e("CHECKING", "Error creating entry", e)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Entry")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun EntryTypeSelector(
    selectedType: EntryType,
    onTypeSelected: (EntryType) -> Unit
) {
    Row {
        FilterChip(
            selected = selectedType == EntryType.SCALE,
            onClick = { onTypeSelected(EntryType.SCALE) },
            label = { Text("Scale") },
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = selectedType == EntryType.BPM,
            onClick = { onTypeSelected(EntryType.BPM) },
            label = { Text("BPM") },
        )
    }
}

enum class EntryType {
    SCALE, BPM
}

private fun createScaleEntry(
    weight: Double,
    bodyFat: Double,
    muscleMass: Double,
    water: Double,
    bmi: Double
): Entry {
    val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        .format(Date())

    return Entry(
        entry = EntryEntity(
            id = 0,
            accountId = "default", // Set a default account ID
            entryTimestamp = timestamp,
            serverTimestamp = null,
            opTimestamp = null,
            operationType = "CREATE",
            deviceType = "scale",
            deviceId = "manual",
            isSynced = false,
        ),
        scaleEntry = BodyScaleEntryEntity(
            id = 0,
            weight = weight.toInt(),
            bodyFat = bodyFat.toInt(),
            muscleMass = muscleMass.toInt(),
            water = water.toInt(),
            bmi = bmi.toInt(),
            source = "manual",
        ),
        bpmEntry = null,
        scaleEntryMetric = null,
    )
}

private fun createBpmEntry(pulse: Int): Entry {
    val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        .format(Date())

    return Entry(
        entry = EntryEntity(
            id = 0,
            accountId = "default", // Set a default account ID
            entryTimestamp = timestamp,
            serverTimestamp = null,
            opTimestamp = null,
            operationType = "CREATE",
            deviceType = "bpm",
            deviceId = "manual",
            isSynced = false,
        ),
        bpmEntry = BpmEntryEntity(
            id = 0,
            pulse = pulse,
            meanArterial = "",
            systolic = 12,
            diastolic = 1212,
            note = "TODO()",
        ),
        scaleEntry = null,
        scaleEntryMetric = null,
    )
}

// Random value generation functions
private fun generateRandomWeight(): Double = Random.nextDouble(45.0, 120.0)
private fun generateRandomBodyFat(): Double = Random.nextDouble(10.0, 35.0)
private fun generateRandomMuscleMass(): Double = Random.nextDouble(30.0, 50.0)
private fun generateRandomWater(): Double = Random.nextDouble(45.0, 65.0)
private fun generateRandomBMI(): Double = Random.nextDouble(18.5, 35.0)
private fun generateRandomPulse(): Int = Random.nextInt(60, 100)
