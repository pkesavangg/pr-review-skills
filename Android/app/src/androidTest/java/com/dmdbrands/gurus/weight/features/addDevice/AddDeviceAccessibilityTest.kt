package com.dmdbrands.gurus.weight.features.addDevice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleFormControls
import com.dmdbrands.gurus.weight.features.addDevice.reducer.AddScaleState
import com.dmdbrands.gurus.weight.features.addDevice.screens.AddScaleScreenContent
import com.dmdbrands.gurus.weight.features.addDevice.strings.AddDeviceScreenStrings
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.example.nav3integration.TopLevelBackStack
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Add Device screen's TalkBack semantics
 * (MOB-855 — Phase 5).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class AddDeviceAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun emptyState() = AddScaleState(
        form = FormGroup(
            controls = AddScaleFormControls(modelNumber = FormControl.create("")),
        ),
        isSubmitting = false,
    )

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun createBackStack(): TopLevelBackStack<NavKey> =
        TopLevelBackStack(
            startKey = AppRoute.Home to AppRoute.Main.Dashboard,
            loginKey = AppRoute.Init.Loading,
        )

    private fun setContent() {
        val backStack = createBackStack()
        composeTestRule.setContent {
            MeAppTheme {
                CompositionLocalProvider(LocalNavBackStack provides backStack) {
                    AddScaleScreenContent(state = emptyState(), handleIntent = {})
                }
            }
        }
    }

    @Test
    fun addDevice_screenTitleIsAHeading() {
        setContent()

        composeTestRule.onNodeWithText(AddDeviceScreenStrings.Title).assert(isHeading())
    }

    @Test
    fun addDevice_closeButtonIsLabeled() {
        setContent()

        composeTestRule.onNodeWithContentDescription(AddDeviceScreenStrings.accCloseButton)
            .assertExists()
    }
}
