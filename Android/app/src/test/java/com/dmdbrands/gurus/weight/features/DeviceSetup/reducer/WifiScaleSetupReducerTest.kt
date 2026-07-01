package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.WifiScaleSetupStep
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WifiScaleSetupReducer].
 *
 * WifiScaleSetupReducer is a self-contained reducer (does not extend DeviceSetupReducer)
 * with complex navigation logic inside Next and Back. Every state-mutating intent,
 * and the most important navigation branches, are covered here.
 */
class WifiScaleSetupReducerTest {

    private lateinit var reducer: WifiScaleSetupReducer

    companion object {
        private const val TEST_SKU = "0385"
        private const val PERMISSION_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val MAC_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }

    private fun defaultState() = WifiScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = WifiScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default WifiScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
        assertThat(state.sku).isEqualTo("0384")
        assertThat(state.isLoading).isFalse()
        assertThat(state.isSetupFinished).isFalse()
        assertThat(state.isConnected).isFalse()
        assertThat(state.selectedUser).isNull()
        assertThat(state.selectedWifiMode).isNull()
        assertThat(state.canProceedToNext).isFalse()
        assertThat(state.isGetMACSetup).isFalse()
        assertThat(state.showApMode).isFalse()
        assertThat(state.showError).isFalse()
        assertThat(state.isNavigating).isFalse()
        assertThat(state.isFirstStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetScaleSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleSku updates sku`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SetScaleSku(TEST_SKU))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // SetCurrentStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetCurrentStep changes step and clears isNavigating`() {
        val state = defaultState().copy(isNavigating = true)

        val result = reducer.reduce(
            state, WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.WIFI_PASSWORD)
        )

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.WIFI_PASSWORD)
        assertThat(result?.isNavigating).isFalse()
    }

    // -------------------------------------------------------------------------
    // SelectUser
    // -------------------------------------------------------------------------

    @Test
    fun `SelectUser stores user number`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SelectUser(2))

        assertThat(result?.selectedUser).isEqualTo(2)
    }

    // -------------------------------------------------------------------------
    // SelectWifiMode
    // -------------------------------------------------------------------------

    @Test
    fun `SelectWifiMode stores wifi mode`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SelectWifiMode("apmode"))

        assertThat(result?.selectedWifiMode).isEqualTo("apmode")
    }

    // -------------------------------------------------------------------------
    // SelectErrorCode
    // -------------------------------------------------------------------------

    @Test
    fun `SelectErrorCode stores code and enables proceed when non-empty`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SelectErrorCode("E01")
        )

        assertThat(result?.selectedErrorCode).isEqualTo("E01")
        assertThat(result?.canProceedToNext).isTrue()
    }

    @Test
    fun `SelectErrorCode with empty string disables proceed`() {
        val state = defaultState().copy(canProceedToNext = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.SelectErrorCode(""))

        assertThat(result?.selectedErrorCode).isEqualTo("")
        assertThat(result?.canProceedToNext).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_LOCATION to "true")

        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_LOCATION, "true")
    }

    // -------------------------------------------------------------------------
    // SetCanProceedToNext
    // -------------------------------------------------------------------------

    @Test
    fun `SetCanProceedToNext true allows proceeding`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetCanProceedToNext(true)
        )

        assertThat(result?.canProceedToNext).isTrue()
    }

    @Test
    fun `SetCanProceedToNext false prevents proceeding`() {
        val state = defaultState().copy(canProceedToNext = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.SetCanProceedToNext(false))

        assertThat(result?.canProceedToNext).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetWifiStatus
    // -------------------------------------------------------------------------

    @Test
    fun `SetWifiStatus stores provided status`() {
        val status: WifiStatus = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SetWifiStatus(status))

        assertThat(result?.wifiStatus).isEqualTo(status)
    }

    // -------------------------------------------------------------------------
    // SetMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `SetMacAddress stores provided mac address`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetMacAddress(MAC_ADDRESS)
        )

        assertThat(result?.macAddress).isEqualTo(MAC_ADDRESS)
    }

    // -------------------------------------------------------------------------
    // SetShowApMode
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowApMode true shows AP mode`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SetShowApMode(true))

        assertThat(result?.showApMode).isTrue()
    }

    @Test
    fun `SetShowApMode false hides AP mode`() {
        val state = defaultState().copy(showApMode = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.SetShowApMode(false))

        assertThat(result?.showApMode).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetShowError
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowError true shows error`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.SetShowError(true))

        assertThat(result?.showError).isTrue()
    }

    @Test
    fun `SetShowError false hides error`() {
        val state = defaultState().copy(showError = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.SetShowError(false))

        assertThat(result?.showError).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetPermissionsSkipped
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissionsSkipped true marks permissions as skipped`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetPermissionsSkipped(true)
        )

        assertThat(result?.permissionsSkipped).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetNextButtonText
    // -------------------------------------------------------------------------

    @Test
    fun `SetNextButtonText updates button text`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetNextButtonText("Finish")
        )

        assertThat(result?.nextButtonText).isEqualTo("Finish")
    }

    // -------------------------------------------------------------------------
    // SetShouldGetMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `SetShouldGetMacAddress true sets flag`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetShouldGetMacAddress(true)
        )

        assertThat(result?.shouldGetMacAddress).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetConnectionSuccess
    // -------------------------------------------------------------------------

    @Test
    fun `SetConnectionSuccess true marks connected`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.SetConnectionSuccess(true)
        )

        assertThat(result?.isConnected).isTrue()
    }

    @Test
    fun `SetConnectionSuccess false marks not connected`() {
        val state = defaultState().copy(isConnected = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.SetConnectionSuccess(false))

        assertThat(result?.isConnected).isFalse()
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup true sets isSetupFinished and isConnected`() {
        val result = reducer.reduce(
            defaultState(),
            WifiScaleSetupIntent.ExitSetup(isSetupFinished = true, isConnected = true)
        )

        assertThat(result?.isSetupFinished).isTrue()
        assertThat(result?.isConnected).isTrue()
    }

    @Test
    fun `ExitSetup false clears isSetupFinished`() {
        val state = defaultState().copy(isSetupFinished = true)

        val result = reducer.reduce(
            state,
            WifiScaleSetupIntent.ExitSetup(isSetupFinished = false, isConnected = false)
        )

        assertThat(result?.isSetupFinished).isFalse()
    }

    // -------------------------------------------------------------------------
    // HandleUserConfirmSelected
    // -------------------------------------------------------------------------

    @Test
    fun `HandleUserConfirmSelected with AP_MODE sets showApMode to "true"`() {
        val result = reducer.reduce(
            defaultState(),
            WifiScaleSetupIntent.HandleUserConfirmSelected(SetupPath.AP_MODE)
        )

        assertThat(result?.showApMode).isTrue()
    }

    @Test
    fun `HandleUserConfirmSelected with COMPLETE sets showApMode to "false"`() {
        val state = defaultState().copy(showApMode = true)

        val result = reducer.reduce(
            state,
            WifiScaleSetupIntent.HandleUserConfirmSelected(SetupPath.COMPLETE)
        )

        assertThat(result?.showApMode).isFalse()
    }

    // -------------------------------------------------------------------------
    // HandleErrorCodeSelected
    // -------------------------------------------------------------------------

    @Test
    fun `HandleErrorCodeSelected stores selected error code`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.HandleErrorCodeSelected("E05")
        )

        assertThat(result?.selectedErrorCode).isEqualTo("E05")
    }

    // -------------------------------------------------------------------------
    // NavigateToErrorGuide
    // -------------------------------------------------------------------------

    @Test
    fun `NavigateToErrorGuide sets currentStep to ERROR_GUIDE and saves previous step`() {
        val state = defaultState().copy(currentStep = WifiScaleSetupStep.WIFI_MODE)

        val result = reducer.reduce(state, WifiScaleSetupIntent.NavigateToErrorGuide())

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_GUIDE)
        assertThat(result?.stepBeforeErrorGuide).isEqualTo(WifiScaleSetupStep.WIFI_MODE)
    }

    // -------------------------------------------------------------------------
    // NavigateToTroubleShooting
    // -------------------------------------------------------------------------

    @Test
    fun `NavigateToTroubleShooting sets currentStep to TROUBLE_SHOOTING and button to Finish`() {
        val result = reducer.reduce(
            defaultState(), WifiScaleSetupIntent.NavigateToTroubleShooting()
        )

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.TROUBLE_SHOOTING)
        assertThat(result?.nextButtonText).isEqualTo("Finish")
    }

    // -------------------------------------------------------------------------
    // OnGetScaleMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `OnGetScaleMacAddress sets isGetMACSetup and shouldGetMacAddress to "true"`() {
        val result = reducer.reduce(defaultState(), WifiScaleSetupIntent.OnGetScaleMacAddress())

        assertThat(result?.isGetMACSetup).isTrue()
        assertThat(result?.shouldGetMacAddress).isTrue()
    }

    // -------------------------------------------------------------------------
    // ClearNavigationState
    // -------------------------------------------------------------------------

    @Test
    fun `ClearNavigationState clears isNavigating flag`() {
        val state = defaultState().copy(isNavigating = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.ClearNavigationState)

        assertThat(result?.isNavigating).isFalse()
    }

    // -------------------------------------------------------------------------
    // Next — navigation logic
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO navigates to next step and sets isNavigating`() {
        val state = defaultState() // currentStep = SCALE_INFO

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        // Default next from SCALE_INFO is PERMISSIONS (index 1)
        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.PERMISSIONS)
        assertThat(result?.isNavigating).isTrue()
        assertThat(result?.canProceedToNext).isFalse()
    }

    @Test
    fun `Next when isNavigating returns null to prevent double-click`() {
        val state = defaultState().copy(isNavigating = true)

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result).isNull()
    }

    @Test
    fun `Next from PERMISSIONS in normal flow navigates to WIFI_PASSWORD`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.PERMISSIONS,
            isGetMACSetup = false,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.WIFI_PASSWORD)
    }

    @Test
    fun `Next from PERMISSIONS in MAC setup flow navigates to ACTIVATE_SCALE`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.PERMISSIONS,
            isGetMACSetup = true,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ACTIVATE_SCALE)
    }

    @Test
    fun `Next from WIFI_MODE with apmode navigates to SWITCH_WIFI`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.WIFI_MODE,
            selectedWifiMode = "apmode",
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.SWITCH_WIFI)
        assertThat(result?.showApMode).isTrue()
    }

    @Test
    fun `Next from WIFI_MODE with non-apmode skips SWITCH_WIFI and goes to STEP_ON`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.WIFI_MODE,
            selectedWifiMode = "normal",
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.STEP_ON)
    }

    @Test
    fun `Next from ERROR_CODE_SELECTED sets isLastStep true`() {
        val state = defaultState().copy(currentStep = WifiScaleSetupStep.ERROR_CODE_SELECTED)

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.isLastStep).isTrue()
    }

    @Test
    fun `Next from TROUBLE_SHOOTING sets isLastStep true`() {
        val state = defaultState().copy(currentStep = WifiScaleSetupStep.TROUBLE_SHOOTING)

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.isLastStep).isTrue()
    }

    @Test
    fun `Next from ERROR_GUIDE without errorCode selected stays on same step`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.ERROR_GUIDE,
            selectedErrorCode = null,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        // Returns state.copy() — same step
        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_GUIDE)
    }

    @Test
    fun `Next from ERROR_GUIDE with errorCode selected navigates to ERROR_CODE_SELECTED`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.ERROR_GUIDE,
            selectedErrorCode = "E03",
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_CODE_SELECTED)
    }

    // -------------------------------------------------------------------------
    // Back — navigation logic
    // -------------------------------------------------------------------------

    @Test
    fun `Back when isNavigating returns null to prevent double-click`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.PERMISSIONS,
            isNavigating = true,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result).isNull()
    }

    @Test
    fun `Back from PERMISSIONS in normal flow goes to SCALE_INFO`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.PERMISSIONS,
            isGetMACSetup = false,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `Back from STEP_ON when mode is not apmode goes to WIFI_MODE`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.STEP_ON,
            selectedWifiMode = "direct",
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.WIFI_MODE)
    }

    @Test
    fun `Back from ERROR_GUIDE returns to stepBeforeErrorGuide`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.ERROR_GUIDE,
            stepBeforeErrorGuide = WifiScaleSetupStep.SCALE_COUNTS,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_COUNTS)
        assertThat(result?.stepBeforeErrorGuide).isNull() // cleared on leaving ERROR_GUIDE
    }

    @Test
    fun `Back from ERROR_CODE_SELECTED returns to ERROR_GUIDE`() {
        val state = defaultState().copy(currentStep = WifiScaleSetupStep.ERROR_CODE_SELECTED)

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_GUIDE)
    }

    @Test
    fun `Back from TROUBLE_SHOOTING returns to ERROR_GUIDE`() {
        val state = defaultState().copy(currentStep = WifiScaleSetupStep.TROUBLE_SHOOTING)

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_GUIDE)
    }

    @Test
    fun `Back resets nextButtonText to Next and clears isLastStep`() {
        val state = defaultState().copy(
            currentStep = WifiScaleSetupStep.PERMISSIONS,
            nextButtonText = "Finish",
            isLastStep = true,
        )

        val result = reducer.reduce(state, WifiScaleSetupIntent.Back)

        assertThat(result?.nextButtonText).isEqualTo("Next")
        assertThat(result?.isLastStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `Skip returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, WifiScaleSetupIntent.Skip)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `GoToWifiSettings returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, WifiScaleSetupIntent.GoToWifiSettings)

        assertThat(result).isEqualTo(state)
    }
}
