package com.dmdbrands.gurus.weight.features.integration

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.features.integration.baseComponent.HealthConnectScreen
import com.dmdbrands.gurus.weight.features.integration.baseComponent.HealthConnectScreenContent
import com.dmdbrands.gurus.weight.features.integration.components.OutOfSyncScreen
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Integrations & connection-state screens' TalkBack
 * semantics (MOB-857 — Phase 7).
 *
 * Verifies that each integration row announces its name + connected/disconnected state (not
 * colour-only), that status screens expose their title as a heading, and that enabled
 * data-type rows announce their state. Requires a device/emulator (real Compose test rule).
 */
class IntegrationAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun connectedItem() =
        IntegrationItem(
            provider = IntegrationProvider.Fitbit,
            name = IntegrationStrings.FitbitProvider,
            isConnected = true,
            iconRes = AppIcons.Integrations.Fitbit,
        )

    private fun disconnectedItem() =
        IntegrationItem(
            provider = IntegrationProvider.MyFitnessPal,
            name = IntegrationStrings.MyFitnessPalProvider,
            isConnected = false,
            iconRes = AppIcons.Integrations.My_Fitness_Pal,
        )

    @Test
    fun integrationRow_connected_announcesConnectedState() {
        val item = connectedItem()
        composeTestRule.setContent {
            MeAppTheme {
                IntegrationListItem(integration = item)
            }
        }

        // The row node carries the connected state alongside the provider name.
        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    IntegrationStrings.accConnectedState,
                ),
            )
            .assertExists()
    }

    @Test
    fun integrationRow_disconnected_announcesNotConnectedState() {
        val item = disconnectedItem()
        composeTestRule.setContent {
            MeAppTheme {
                IntegrationListItem(integration = item)
            }
        }

        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    IntegrationStrings.accNotConnectedState,
                ),
            )
            .assertExists()
    }

    @Test
    fun integrationRow_providerLogoIsLabelled() {
        val item = connectedItem()
        composeTestRule.setContent {
            MeAppTheme {
                IntegrationListItem(integration = item)
            }
        }

        composeTestRule
            .onNodeWithContentDescription(IntegrationStrings.accProviderLogo(item.name))
            .assertExists()
    }

    @Test
    fun healthConnectScreen_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                HealthConnectScreen(
                    content = HealthConnectScreenContent(
                        image = AppIcons.Integrations.User_Conflict,
                        title = HealthConnectStrings.UserConflictStrings.Title,
                        description = HealthConnectStrings.UserConflictStrings.Description,
                        primaryButtonLabel = HealthConnectStrings.ActionButtons.exit,
                    ),
                    onPrimaryAction = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(HealthConnectStrings.UserConflictStrings.Title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun healthConnectScreen_dataTypeRow_isPlainListItemWithoutStateDescription() {
        composeTestRule.setContent {
            MeAppTheme {
                HealthConnectScreen(
                    content = HealthConnectScreenContent(
                        image = AppIcons.Integrations.Full_Permission,
                        title = HealthConnectStrings.StartFullReconnectStrings.Title,
                        description = HealthConnectStrings.StartFullReconnectStrings.Description,
                        primaryButtonLabel = HealthConnectStrings.ActionButtons.finish,
                    ),
                    onPrimaryAction = {},
                    dataTypes = listOf(HealthConnectStrings.StartConnectStrings.DataTypeWeight),
                )
            }
        }

        // On the pre-connection consent screen these are plain list items — nothing is enabled
        // yet, so the row must NOT carry a "state" (it previously hardcoded "Enabled", which
        // TalkBack would wrongly announce). The data-type label is still present and readable.
        composeTestRule
            .onNodeWithText(HealthConnectStrings.StartConnectStrings.DataTypeWeight)
            .assertExists()
        composeTestRule
            .onNodeWithText(HealthConnectStrings.StartConnectStrings.DataTypeWeight)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun outOfSyncScreen_titleIsHeadingAndCloseIsLabelled() {
        composeTestRule.setContent {
            MeAppTheme {
                OutOfSyncScreen(
                    onClose = {},
                    onPrimaryAction = {},
                    onSecondaryAction = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(HealthConnectStrings.PopupStrings.outOfSyncTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeTestRule
            .onNodeWithContentDescription(HealthConnectStrings.Accessibility.closeButton)
            .assertExists()
    }
}
