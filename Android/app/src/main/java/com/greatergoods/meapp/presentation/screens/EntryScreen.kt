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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.presentation.viewmodel.EntryUiState
import com.greatergoods.meapp.presentation.viewmodel.EntryViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EntryScreen(
    viewModel: EntryViewModel = hiltViewModel(),
    onNavigateToAddEntry: () -> Unit
) {
    val navBackStack = LocalNavBackStack.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthEntries by viewModel.monthEntries.collectAsState()
    val selectedEntry by viewModel.selectedEntry.collectAsState()

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Button(
            onClick = {
                navBackStack.add(
                    AppRoute.Main.AddEntry,
                )
            },
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 16.dp, end = 16.dp, top = 80.dp),
        ) {
            Text("Add Entry")
        }
        when (uiState) {
            is EntryUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            is EntryUiState.Success -> {
                MonthList(
                    months = (uiState as EntryUiState.Success).months,
                    selectedMonth = selectedMonth,
                    onMonthSelected = { viewModel.selectMonth(it) },
                )
            }

            is EntryUiState.Error -> {
                Text(
                    text = (uiState as EntryUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedMonth != null) {
            EntryList(
                entries = monthEntries,
                selectedEntry = selectedEntry,
                onEntrySelected = { viewModel.selectEntry(it) },
                onDeleteEntry = { viewModel.deleteEntry(it) },
            )
        }
    }
}

@Composable
fun MonthList(
    months: List<HistoryMonth>,
    selectedMonth: HistoryMonth?,
    onMonthSelected: (HistoryMonth) -> Unit
) {
    LazyColumn {
        items(months) { month ->
            MonthItem(
                month = month,
                onClick = { onMonthSelected(month) },
            )
        }
    }
}

@Composable
fun MonthItem(
    month: HistoryMonth,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = month.entryTimestamp ?: "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Weight: ${month.weight} kg",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Entries: ${month.count}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun EntryList(
    entries: List<Entry>,
    selectedEntry: Entry?,
    onEntrySelected: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit
) {
    LazyColumn {
        items(entries) { entry ->
            EntryItem(
                entry = entry,
                isSelected = entry == selectedEntry,
                onClick = { onEntrySelected(entry) },
                onDelete = { onDeleteEntry(entry) },
            )
        }
    }
}

@Composable
fun EntryItem(
    entry: Entry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = formatDate(entry.entry.entryTimestamp),
                    style = MaterialTheme.typography.titleMedium,
                )

            }

            if (isSelected) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry",
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(timestamp)
        outputFormat.format(date!!)
    } catch (e: Exception) {
        timestamp
    }
}
