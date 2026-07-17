package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.features.common.strings.DeviceStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the list/card/user components' TalkBack semantics
 * (MOB-850 — Phase 0 shared-component accessibility).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class ListsAndCardsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun account(active: Boolean) = Account(
        id = "1",
        firstName = "Kristin",
        lastName = "Jones",
        dob = "1990-01-01",
        email = "kristin@gmail.com",
        gender = "Female",
        isActiveAccount = active,
        isLoggedIn = true,
        lastActiveTime = "2024-01-15T10:30:00.000Z",
        zipcode = "12345",
        isSynced = true,
        isExpired = false,
        weightUnit = WeightUnit.LB,
        isWeightlessOn = false,
        height = 170,
        activityLevel = "Active",
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
        dashboardMetrics = listOf("weight", "bmi"),
    )

    @Test
    fun appDeviceImage_hasProductContentDescriptionFromStrings() {
        composeTestRule.setContent {
            MeAppTheme {
                AppDeviceImage(sku = "0412")
            }
        }

        composeTestRule
            .onNodeWithContentDescription("0412 ${DeviceStrings.accScaleImageSuffix}")
            .assertExists()
    }

    @Test
    fun appDeviceCard_decorativeIconsAreNotAnnounced() {
        composeTestRule.setContent {
            MeAppTheme {
                AppDeviceCard(
                    scale = DeviceModelInfo(
                        productName = "Bluetooth Smart Scale",
                        sku = "0375",
                        setupType = DeviceSetupType.Bluetooth,
                        bodyComp = false,
                        isConnected = true,
                        isWifiConfigured = false,
                        scaleId = "scaleId2",
                    ),
                    isSavedScale = true,
                    onClick = {},
                )
            }
        }

        // Old decorative literals must no longer be in the tree.
        composeTestRule.onNodeWithContentDescription("Connection type icon").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Navigate").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Scale type icon").assertDoesNotExist()
    }

    @Test
    fun appProfileAvatar_initialIsDecorativeByDefault() {
        composeTestRule.setContent {
            MeAppTheme {
                AppProfileAvatar(text = "Kevin")
            }
        }

        // The bare initial must not be announced.
        composeTestRule.onNodeWithText("K").assertDoesNotExist()
    }

    @Test
    fun appProfileAvatar_announcesProvidedLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                AppProfileAvatar(text = "Kevin", contentDescription = "Kevin avatar")
            }
        }

        composeTestRule.onNodeWithContentDescription("Kevin avatar").assertExists()
    }

    @Test
    fun appUser_selectAccountControlUsesAccLabel_andAvatarIsDecorative() {
        composeTestRule.setContent {
            MeAppTheme {
                AppUser(
                    account = account(active = true),
                    onAccountSelect = {},
                    onLoginRequest = {},
                    showAccountActivity = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(AppListStrings.accSelectAccountLabel).assertExists()
        // Name is announced from the row text, not from the decorative avatar initial.
        composeTestRule.onNodeWithText("K").assertDoesNotExist()
    }

    @Test
    fun baseListItem_checkboxExposesCheckedState() {
        composeTestRule.setContent {
            MeAppTheme {
                BaseListItem(
                    title = "Item",
                    enableCheckbox = true,
                    isChecked = true,
                    checkboxDescription = "Select item",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Select item").assertIsSelected()
    }

    @Test
    fun baseListItem_uncheckedCheckboxIsNotSelected() {
        composeTestRule.setContent {
            MeAppTheme {
                BaseListItem(
                    title = "Item",
                    enableCheckbox = true,
                    isChecked = false,
                    checkboxDescription = "Select item",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Select item").assertIsNotSelected()
    }
}
