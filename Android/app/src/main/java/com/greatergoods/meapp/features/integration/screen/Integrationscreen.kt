package com.greatergoods.meapp.features.integration.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.integration.IntegrationList
import com.greatergoods.meapp.features.integration.model.IntegrationIntent
import com.greatergoods.meapp.features.integration.model.IntegrationState
import com.greatergoods.meapp.features.integration.strings.IntegrationStrings
import com.greatergoods.meapp.features.integration.viewmodel.IntegrationViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Integration screen composable. Displays available and connected integrations, handles user interactions.
 */
@Composable
fun IntegrationScreen() {
    val viewModel: IntegrationViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    BackHandler {
        viewModel.handleIntent(IntegrationIntent.OnBack)
    }

    // Load integrations on first launch
    LaunchedEffect(Unit) {
        viewModel.handleIntent(IntegrationIntent.LoadIntegrations)
    }

    IntegrationContent(state, viewModel::handleIntent)
}

@Composable
private fun IntegrationContent(
    state: IntegrationState,
    handleIntent: (IntegrationIntent) -> Unit,
) {
    AppScaffold(
        title = IntegrationStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                handleIntent(IntegrationIntent.OnBack)
            }
        },
    ) { scaffoldModifier ->
        Column(
            modifier = scaffoldModifier
                .fillMaxSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            IntegrationList(state, handleIntent)
        }
    }
}

@PreviewTheme
@Composable
fun IntegrationScreenPreview() {
    MeAppTheme {
        val dummyState = IntegrationState(
            integrations = emptyList(),
        )

        IntegrationContent(
            state = dummyState,
            handleIntent = {},
        )
    }
}
