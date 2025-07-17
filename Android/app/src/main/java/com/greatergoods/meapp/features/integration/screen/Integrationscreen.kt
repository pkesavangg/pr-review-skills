package com.greatergoods.meapp.features.integration.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
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
    val viewmodel: IntegrationViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewmodel.onResume(lifecycleOwner)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
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
                .padding(spacing.md),
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
