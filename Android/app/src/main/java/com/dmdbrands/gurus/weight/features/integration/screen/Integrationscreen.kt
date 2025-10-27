package com.dmdbrands.gurus.weight.features.integration.screen

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
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationState
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.features.integration.viewmodel.IntegrationViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Integration screen composable. Displays available and connected integrations, handles user interactions.
 */
@Composable
fun IntegrationScreen() {
    val viewmodel: IntegrationViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()

  LaunchedEffect(Unit) {
    viewmodel.handleIntent(IntegrationIntent.LoadIntegrations)
  }
    BackHandler {
        viewmodel.handleIntent(IntegrationIntent.OnBack)
    }

    IntegrationContent(state, viewmodel::handleIntent, viewmodel::onHealthConnectIconClicked)
}

@Composable
private fun IntegrationContent(
    state: IntegrationState,
    handleIntent: (IntegrationIntent) -> Unit,
    onHealthConnectIconClick: () -> Unit = {},
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
                .padding(vertical = spacing.md, horizontal = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            IntegrationList(state, handleIntent, onHealthConnectIconClick)
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
