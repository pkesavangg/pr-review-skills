package com.dmdbrands.gurus.weight.features.integration.screen

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
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.integration.IntegrationListItem
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationState
import com.dmdbrands.gurus.weight.resources.AppIcons
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

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
  onHealthConnectIconClick: (() -> Unit)? = null,
) {
  LazyColumn(
    modifier =
      Modifier
        .clip(RoundedCornerShape(spacing.sm)),
  ) {
    // All Integrations Section
    itemsIndexed(state.integrations, key = { _, integration -> integration.provider.apiValue }) { index, integration ->
      IntegrationListItem(
        integration = integration,
        onToggle = {
          if (integration.provider == IntegrationProvider.HealthConnect) {
            handleIntent.invoke(IntegrationIntent.ToggleHealthConnectIntegration(integration))
          } else {
            handleIntent.invoke(IntegrationIntent.OpenIntegration(integration))
          }
        },
        onIconClick = if (integration.provider == IntegrationProvider.HealthConnect) onHealthConnectIconClick else null,
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
              integrations = persistentListOf(),
            ),
          handleIntent = {},
        )

        // Single integration (disabled)
        IntegrationList(
          state =
            IntegrationState(
              integrations =
                persistentListOf(
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
                persistentListOf(
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
