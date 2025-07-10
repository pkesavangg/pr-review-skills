package com.greatergoods.meapp.features.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.integration.model.IntegrationIntent
import com.greatergoods.meapp.features.integration.model.IntegrationItem
import com.greatergoods.meapp.features.integration.model.IntegrationState
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Integration list component that displays all integrations with enable/disable functionality.
 *
 * @param state The current integration state
 * @param handleIntent Callback for handling integration intents
 */
@Composable
fun IntegrationList(
  state: IntegrationState,
  handleIntent: (IntegrationIntent) -> Unit,
) {
  LazyColumn(
    modifier =
      Modifier
        .clip(RoundedCornerShape(spacing.sm)),
  ) {
    // All Integrations Section
    itemsIndexed(state.integrations) { index, integration ->
      IntegrationListItem(
        integration = integration,
        onToggle = {
          if (integration.isConnected) {
            // Disable integration
            handleIntent(IntegrationIntent.ShowDisconnectDialog(integration))
          } else {
            // Enable integration - you'll need to pass accountId here
            // For now, using a placeholder accountId
            handleIntent(
              IntegrationIntent.ConnectIntegration(
                provider = integration.provider,
              ),
            )
          }
        },
      )
      // Only show divider if not the last item
      if (index < state.integrations.size - 1) {
        HorizontalDivider(thickness = 0.5.dp, color = colorScheme.utility)
      }
    }
  }
}



@PreviewTheme
@Composable
fun IntegrationListPreview() {
  MeAppTheme {
    AppScaffold("Integration List Preview") {
      Column(
        modifier = Modifier.padding(spacing.md),
      ) {
        // Empty state
        IntegrationList(
          state =
            IntegrationState(
              integrations = emptyList(),
            ),
          handleIntent = {},
        )

        // Single integration (disabled)
        IntegrationList(
          state =
            IntegrationState(
              integrations =
                listOf(
                  IntegrationItem(
                    provider = IntegrationProvider.Fitbit,
                    name = "Fitbit",
                    isConnected = false,
                    iconRes = AppIcons.Integrations.Fitbit,
                  ),
                ),
            ),
          handleIntent = {},
        )
        Spacer(Modifier.padding(bottom = 50.dp))
        // Multiple integrations (mixed status)
        IntegrationList(
          state =
            IntegrationState(
              integrations =
                listOf(
                  IntegrationItem(
                    provider = IntegrationProvider.Fitbit,
                    name = "Fitbit",
                    isConnected = true,
                    iconRes = AppIcons.Integrations.Fitbit,
                  ),
                  IntegrationItem(
                    provider = IntegrationProvider.MyFitnessPal,
                    name = "MyFitnessPal",
                    isConnected = false,
                    iconRes = AppIcons.Integrations.My_Fitness_Pal,
                  ),
                  IntegrationItem(
                    provider = IntegrationProvider.HealthConnect,
                    name = "Health Connect",
                    isConnected = true,
                    iconRes = AppIcons.Integrations.Health_Connect_Logo,
                  ),
                ),
            ),
          handleIntent = {},
        )
      }
    }
  }
}
