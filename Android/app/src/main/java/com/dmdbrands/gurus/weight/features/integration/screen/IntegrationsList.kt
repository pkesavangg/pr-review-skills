package com.dmdbrands.gurus.weight.features.integration.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationCategory
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.integration.IntegrationListItem
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationState
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import kotlinx.collections.immutable.persistentListOf

/**
 * Renders the Integrations screen body as device-driven sections.
 *
 * Section 1 — "Integrations for weight scales" → providers tagged
 * [IntegrationCategory.WeightScale] (Fitbit, MyFitnessPal).
 *
 * Section 2 — "Integrations for Weight Scales & BPM" → providers tagged
 * [IntegrationCategory.WeightScaleAndBpm] (Health Connect).
 *
 * Sections render only when they contain at least one provider, so when new
 * providers/devices are introduced their section appears automatically.
 */
@Composable
fun IntegrationList(
  state: IntegrationState,
  handleIntent: (IntegrationIntent) -> Unit,
  onHealthConnectIconClick: (() -> Unit)? = null,
) {
  val grouped = state.integrations.groupBy { it.provider.getCategory() }
  Column(
    verticalArrangement = Arrangement.spacedBy(spacing.md),
    modifier = Modifier.fillMaxWidth(),
  ) {
    IntegrationSectionOrNull(
      title = IntegrationStrings.SectionWeightScale,
      items = grouped[IntegrationCategory.WeightScale].orEmpty(),
      handleIntent = handleIntent,
      onHealthConnectIconClick = onHealthConnectIconClick,
    )
    IntegrationSectionOrNull(
      title = IntegrationStrings.SectionWeightScaleAndBpm,
      items = grouped[IntegrationCategory.WeightScaleAndBpm].orEmpty(),
      handleIntent = handleIntent,
      onHealthConnectIconClick = onHealthConnectIconClick,
    )
  }
}

@Composable
private fun IntegrationSectionOrNull(
  title: String,
  items: List<IntegrationItem>,
  handleIntent: (IntegrationIntent) -> Unit,
  onHealthConnectIconClick: (() -> Unit)?,
) {
  if (items.isEmpty()) return
  Column(
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = title,
      style = typography.heading6,
      color = colorScheme.textHeading,
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(spacing.sm)),
    ) {
      items.forEachIndexed { index, integration ->
        IntegrationListItem(
          integration = integration,
          onToggle = {
            if (integration.provider == IntegrationProvider.HealthConnect) {
              handleIntent(IntegrationIntent.ToggleHealthConnectIntegration(integration))
            } else {
              handleIntent(IntegrationIntent.OpenIntegration(integration))
            }
          },
          onIconClick = if (integration.provider == IntegrationProvider.HealthConnect) onHealthConnectIconClick else null,
        )
        if (index < items.lastIndex) {
          HorizontalDivider(thickness = 0.5.dp, color = colorScheme.utility)
        }
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
        IntegrationList(
          state = IntegrationState(integrations = persistentListOf()),
          handleIntent = {},
        )
        Spacer(Modifier.padding(bottom = 24.dp))
        IntegrationList(
          state = IntegrationState(
            integrations = persistentListOf(
              IntegrationItem(
                provider = IntegrationProvider.Fitbit,
                name = "Fitbit",
                isConnected = true,
                iconRes = AppIcons.Integrations.Fitbit,
              ),
              IntegrationItem(
                provider = IntegrationProvider.MyFitnessPal,
                name = "My Fitness Pal",
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
