package com.greatergoods.meapp.features.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppInput
import com.greatergoods.meapp.features.common.components.AppInputType
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.DateTimeInput
import com.greatergoods.meapp.features.common.components.DateTimeInputMode
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.entry.strings.EntryScreenStrings
import com.greatergoods.meapp.features.entry.viewmodel.EntryViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun EntryScreen(
) {
    val viewModel: EntryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val controls = state.form.controls
    AppScaffold(EntryScreenStrings.Title) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MeTheme.spacing.md)
                .padding(top = MeTheme.spacing.md),
            verticalArrangement = Arrangement.Top,
        ) {
            AppInput(
                formControl = controls.weight,
                label = EntryScreenStrings.WEIGHT_LABEL,
                type = AppInputType.NUMBER,
                modifier = Modifier
                    .fillMaxWidth(),
            )
            DateTimeInput(
                formControl = controls.dateTime,
                mode = DateTimeInputMode.DateTime,
                label = EntryScreenStrings.DATE_LABEL,
            )
            Button(
                onClick = { /* TODO: Implement save logic */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(EntryScreenStrings.SaveButton)
            }
        }
    }
}

@PreviewTheme
@Composable
fun EntryScreenPreview() {
    MeAppTheme {
        EntryScreen()
    }
}
