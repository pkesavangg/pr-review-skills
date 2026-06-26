package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the gesture-only list actions exposed to TalkBack as
 * custom accessibility actions (MOB-850 — Phase 0, Batch E):
 *  - AppUserList swipe-to-delete -> "Delete item" custom action,
 *  - AppDraggableList drag-to-reorder -> "Move up"/"Move down" custom actions.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class ListActionsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action '$label'") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
        }

    private fun doesNotHaveCustomAction(label: String) =
        SemanticsMatcher("no custom action '$label'") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.none { it.label == label } ?: true
        }

    private fun account(id: String, firstName: String) = Account(
        id = id,
        firstName = firstName,
        lastName = "User",
        dob = "1990-01-01",
        email = "$firstName@example.com",
        gender = "Female",
        isActiveAccount = false,
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
    fun appUserList_exposesDeleteAsCustomAction_andInvokesCallback() {
        var deleted: Account? = null
        composeTestRule.setContent {
            MeAppTheme {
                AppUserList(
                    accounts = listOf(account("1", "Kristin")),
                    canRemoveAccount = true,
                    onDeleteRequest = { deleted = it },
                    onAccountSelect = {},
                    onLoginRequest = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Kristin").assert(hasCustomAction(AppListStrings.accDeleteItemLabel))

        // Invoking the custom action runs the same delete callback as the swipe gesture.
        val node = composeTestRule.onNodeWithText("Kristin").fetchSemanticsNode()
        val deleteAction = node.config.getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
            .first { it.label == AppListStrings.accDeleteItemLabel }
        composeTestRule.runOnUiThread { deleteAction.action() }
        composeTestRule.waitForIdle()
        assertThat(deleted?.id).isEqualTo("1")
    }

    @Test
    fun appUserList_noDeleteActionWhenNotRemovable() {
        composeTestRule.setContent {
            MeAppTheme {
                AppUserList(
                    accounts = listOf(account("1", "Kristin")),
                    canRemoveAccount = false,
                    onDeleteRequest = {},
                    onAccountSelect = {},
                    onLoginRequest = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Kristin")
            .assert(doesNotHaveCustomAction(AppListStrings.accDeleteItemLabel))
    }

    @Test
    fun appDraggableList_exposesMoveActionsPerPosition() {
        composeTestRule.setContent {
            MeAppTheme {
                var items by remember { mutableStateOf(listOf("First", "Middle", "Last")) }
                AppDraggableList(
                    items = items,
                    onMove = { from, to ->
                        val next = items.toMutableList()
                        next.add(to, next.removeAt(from))
                        items = next
                    },
                    keySelector = { it },
                    itemContent = { item ->
                        DraggableItem { _, _ ->
                            Text(text = item, modifier = Modifier.padding(16.dp))
                        }
                    },
                )
            }
        }

        // First item: can move down, but not up.
        composeTestRule.onNodeWithText("First")
            .assert(hasCustomAction(AppListStrings.accMoveDownLabel))
            .assert(doesNotHaveCustomAction(AppListStrings.accMoveUpLabel))

        // Middle item: both directions.
        composeTestRule.onNodeWithText("Middle")
            .assert(hasCustomAction(AppListStrings.accMoveUpLabel))
            .assert(hasCustomAction(AppListStrings.accMoveDownLabel))

        // Last item: can move up, but not down.
        composeTestRule.onNodeWithText("Last")
            .assert(hasCustomAction(AppListStrings.accMoveUpLabel))
            .assert(doesNotHaveCustomAction(AppListStrings.accMoveDownLabel))
    }
}
