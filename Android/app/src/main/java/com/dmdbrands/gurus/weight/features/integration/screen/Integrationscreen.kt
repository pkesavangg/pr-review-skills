package com.dmdbrands.gurus.weight.features.integration.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationState
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.features.integration.viewmodel.IntegrationViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.collections.immutable.persistentListOf

/**
 * Integration screen composable. Displays available and connected integrations, handles user interactions.
 */
@Composable
fun IntegrationScreen() {
    val viewmodel: IntegrationViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    //check it can removable or not
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntegrationList(state, handleIntent, onHealthConnectIconClick)
            AppButton(
                label = IntegrationStrings.RequestNewIntegration,
                type = ButtonType.TextPrimary,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                onClick = { handleIntent(IntegrationIntent.RequestNewIntegration) },
            )
        }
    }
}

@PreviewTheme
@Composable
fun IntegrationScreenPreview() {
    MeAppTheme {
        val dummyState = IntegrationState(
            integrations = persistentListOf(),
        )
        IntegrationContent(
            state = dummyState,
            handleIntent = {},
        )
    }
}
