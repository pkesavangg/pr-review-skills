package com.dmdbrands.gurus.weight.features.integration

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.BaseListItem
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Integration list item component that displays integration information with enable/disable toggle.
 *
 * @param integration The integration item to display
 * @param onToggle Callback when enable/disable toggle is clicked
 * @param onIconClick Callback when the icon is clicked (optional)
 * @param modifier Modifier for styling
 */
@Composable
fun IntegrationListItem(
  modifier: Modifier = Modifier,
  integration: IntegrationItem,
  onToggle: () -> Unit = {},
  onIconClick: (() -> Unit)? = null,
) {
  // Toggle button
  BaseListItem(
    title = integration.name,
    enableCheckbox = true,
    isChecked = integration.isConnected,
    checkboxDescription = "Toggle ${integration.name} integration",
    onClick = onToggle,
    modifier = modifier,
    leadingContent = {
      Image(
        painterResource( integration.iconRes),
        contentDescription = "${integration.name} logo",
        modifier = Modifier
          .size(42.dp)
          .then(if (onIconClick != null) Modifier.clickable { onIconClick() } else Modifier),
      )
    },
  )
}

@PreviewTheme
@Composable
fun IntegrationListItemPreview() {
  MeAppTheme {
    AppScaffold("") {
      Column(
        modifier = Modifier.padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
      ) {
        // Connected integration preview
        IntegrationListItem(
          integration =
            IntegrationItem(
              provider = IntegrationProvider.Fitbit,
              name = IntegrationStrings.FitbitProvider,
              isConnected = true,
              iconRes = AppIcons.Integrations.Fitbit ,
              platformRequirement = IntegrationProvider.Fitbit.getPlatformRequirement(),
              requiresOAuth = IntegrationProvider.Fitbit.requiresOAuth(),
            ),
          onToggle = {},
          onIconClick = {},
        )

        // Available integration preview
        IntegrationListItem(
          integration =
            IntegrationItem(
              provider = IntegrationProvider.MyFitnessPal,
              name = IntegrationStrings.MyFitnessPalProvider,
              isConnected = false,
              iconRes = AppIcons.Integrations.My_Fitness_Pal ,
              platformRequirement = IntegrationProvider.MyFitnessPal.getPlatformRequirement(),
              requiresOAuth = IntegrationProvider.MyFitnessPal.requiresOAuth(),
            ),
          onToggle = {},
          onIconClick = {},
        )

        // Health Connect integration preview
        IntegrationListItem(
          integration =
            IntegrationItem(
              provider = IntegrationProvider.HealthConnect,
              name = IntegrationStrings.HealthConnectProvider,
              isConnected = true,
              iconRes = AppIcons.Integrations.Health_Connect_Logo ,
              platformRequirement = IntegrationProvider.HealthConnect.getPlatformRequirement(),
              requiresOAuth = IntegrationProvider.HealthConnect.requiresOAuth(),
            ),
          onToggle = {},
          onIconClick = {},
        )
      }
    }
  }
}

