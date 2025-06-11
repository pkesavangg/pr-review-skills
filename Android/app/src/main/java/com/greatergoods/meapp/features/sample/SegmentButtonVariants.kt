package com.greatergoods.meapp.features.sample


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.proto.ThemeMode // Assuming you have this
import com.greatergoods.meapp.theme.MeAppTheme // Assuming you have this
import com.greatergoods.meapp.features.common.components.SegmentButton // Import your SegmentButton
import com.greatergoods.meapp.features.common.components.SegmentButtonSize // Import your SegmentButtonSize enum
import com.greatergoods.meapp.features.common.components.SegmentOption

@Composable
fun MySegmentedButtonScreen() {
    Scaffold { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeAppTheme.colorScheme.primary)
                    .padding(16.dp)
            ) {
                // --- Example 1: Basic Segment Button ---
                Text(
                    text = "Select Timeframe:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val timeSegments = listOf("Day", "Week", "Month","Day1", "Week1", "Month1","Day2", "Week2", "Month2","Day3", "Week3", "Month3").mapIndexed { index, label ->
                    SegmentOption(
                        id = index,
                        label = label
                    )
                }
                var selectedTimeIndex by remember { mutableIntStateOf(1) } // Default to "Week"

                SegmentButton(
                    segments = timeSegments,
                    selectedIndex = selectedTimeIndex,
                    onSegmentSelected = { newIndex -> selectedTimeIndex = newIndex },
                    modifier = Modifier.fillMaxWidth(), // Make it fill the width
                    size = SegmentButtonSize.Medium,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Example 2: Disabled Segment Button ---
                Text(
                    text = "Select Category (Disabled):",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val categorySegments = listOf("Books", "Movies", "Music", "Games").mapIndexed { index, label -> SegmentOption(id = index, label = label) }
                var selectedCategoryIndex by remember { mutableIntStateOf(0) } // Default to "Books"

                SegmentButton(
                    segments = categorySegments,
                    selectedIndex = selectedCategoryIndex,
                    onSegmentSelected = { newIndex -> selectedCategoryIndex = newIndex },
                    modifier = Modifier.fillMaxWidth(),
                    size = SegmentButtonSize.Small,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Example 3: Large Size Segment Button ---
                Text(
                    text = "Select Priority:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val prioritySegments = listOf("Low", "Medium", "High").mapIndexed { index, label -> SegmentOption(id = index, label = label) }
                var selectedPriorityIndex by remember { mutableIntStateOf(2) } // Default to "High"

                SegmentButton(
                    segments = prioritySegments,
                    selectedIndex = selectedPriorityIndex,
                    onSegmentSelected = { newIndex -> selectedPriorityIndex = newIndex

                                        },
                    modifier = Modifier.fillMaxWidth(),
                    size = SegmentButtonSize.Large,
                )
            }
        }
    }
}


@Preview(name = "Segmented Button Screen Preview - Light", showBackground = true)
@Composable
fun MySegmentedButtonScreenPreviewLight() {
    MeAppTheme(themeMode = ThemeMode.LIGHT) {
        MySegmentedButtonScreen()
    }
}

@Preview(name = "Segmented Button Screen Preview - Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MySegmentedButtonScreenPreviewDark() {
    MeAppTheme(themeMode = ThemeMode.DARK) {
        MySegmentedButtonScreen()
    }
}
