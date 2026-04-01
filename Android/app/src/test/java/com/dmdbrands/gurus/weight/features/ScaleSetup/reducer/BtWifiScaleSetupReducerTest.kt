package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BtWifiScaleSetupReducer].
 *
 * BtWifiScaleSetupReducer manages its own independent state (does NOT extend ScaleSetupReducer)
 * with a rich set of intents for the BT+WiFi scale setup flow.
 */
class BtWifiScaleSetupReducerTest {

    private lateinit var reducer: BtWifiScaleSetupReducer

    companion object {
        private const val TEST_SKU = "0412"
        private const val TEST_SCALE_ID = "scale-abc-123"
        private const val TEST_SSID = "HomeNetwork"
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH_SCAN"
    }

    private fun defaultState() = BtWifiScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = BtWifiScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default BtWifiScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.currentStep).isEqualTo(BtWifiSetupStep.SCALE_INFO)
        assertThat(state.sku).isEqualTo("0412")
        assertThat(state.isLoading).isFalse()
        assertThat(state.isSetupFinished).isFalse()
        assertThat(state.canProceedToNext).isTrue()
        assertThat(state.isAllBodyMetrics).isTrue()
        assertThat(state.isHeartRateOn).isFalse()
        assertThat(state.hasSavedSettings).isFalse()
        assertThat(state.visitedCustomizeSteps).isEmpty()
        assertThat(state.isFirstStep).isTrue()
        assertThat(state.isLastStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScaleSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleSku updates sku`() {
        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetScaleSku(TEST_SKU))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // SetScaleId
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleId updates scaleId`() {
        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetScaleId(TEST_SCALE_ID))

        assertThat(result?.scaleId).isEqualTo(TEST_SCALE_ID)
    }

    // -------------------------------------------------------------------------
    // SetCurrentStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetCurrentStep changes currentStep and resets nextButtonText to Next`() {
        val state = defaultState().copy(nextButtonText = "Finish")

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.WAKEUP))

        assertThat(result?.currentStep).isEqualTo(BtWifiSetupStep.WAKEUP)
        assertThat(result?.nextButtonText).isEqualTo(ScaleSetupStrings.SetupButtons.Next)
    }

    // -------------------------------------------------------------------------
    // SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading true enables loading`() {
        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetLoading(true))

        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `SetLoading false disables loading`() {
        val state = defaultState().copy(isLoading = true)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetLoading(false))

        assertThat(result?.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetErrorCode
    // -------------------------------------------------------------------------

    @Test
    fun `SetErrorCode stores error code`() {
        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetErrorCode("ERR_42"))

        assertThat(result?.errorCode).isEqualTo("ERR_42")
    }

    @Test
    fun `SetErrorCode with null clears error code`() {
        val state = defaultState().copy(errorCode = "OLD_ERR")

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetErrorCode(null))

        assertThat(result?.errorCode).isNull()
    }

    // -------------------------------------------------------------------------
    // SetConnectedSSID
    // -------------------------------------------------------------------------

    @Test
    fun `SetConnectedSSID stores SSID`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetConnectedSSID(TEST_SSID)
        )

        assertThat(result?.connectedSSID).isEqualTo(TEST_SSID)
    }

    // -------------------------------------------------------------------------
    // SetUserList
    // -------------------------------------------------------------------------

    @Test
    fun `SetUserList stores user list as immutable list`() {
        val userA: GGBTUser = mockk(relaxed = true)
        val userB: GGBTUser = mockk(relaxed = true)

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetUserList(listOf(userA, userB))
        )

        assertThat(result?.userList).containsExactly(userA, userB).inOrder()
    }

    @Test
    fun `SetUserList with empty list clears previous list`() {
        val userA: GGBTUser = mockk(relaxed = true)
        val state = defaultState().copy(userList = persistentListOf(userA))

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetUserList(emptyList()))

        assertThat(result?.userList).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetDuplicateUser
    // -------------------------------------------------------------------------

    @Test
    fun `SetDuplicateUser stores duplicate user`() {
        val dup: GGBTUser = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetDuplicateUser(dup))

        assertThat(result?.duplicateUser).isEqualTo(dup)
    }

    @Test
    fun `SetDuplicateUser with null clears duplicate user`() {
        val dup: GGBTUser = mockk(relaxed = true)
        val state = defaultState().copy(duplicateUser = dup)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetDuplicateUser(null))

        assertThat(result?.duplicateUser).isNull()
    }

    // -------------------------------------------------------------------------
    // SetDuplicateUserList
    // -------------------------------------------------------------------------

    @Test
    fun `SetDuplicateUserList stores list as immutable list`() {
        val dup: GGBTUser = mockk(relaxed = true)

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetDuplicateUserList(listOf(dup))
        )

        assertThat(result?.duplicateUserList).containsExactly(dup)
    }

    // -------------------------------------------------------------------------
    // SetDashboardKeys
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardKeys stores keys as immutable list`() {
        val key: DashboardKey = mockk(relaxed = true)

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetDashboardKeys(listOf(key))
        )

        assertThat(result?.dashboardKeys).containsExactly(key)
    }

    // -------------------------------------------------------------------------
    // SetGoalProgress
    // -------------------------------------------------------------------------

    @Test
    fun `SetGoalProgress updates goalProgress`() {
        val progress = Progress(currentStreak = 5, longestStreak = 10, count = 20)

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetGoalProgress(progress)
        )

        assertThat(result?.goalProgress).isEqualTo(progress)
    }

    // -------------------------------------------------------------------------
    // SetCanProceedToNext
    // -------------------------------------------------------------------------

    @Test
    fun `SetCanProceedToNext false prevents proceeding`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetCanProceedToNext(false)
        )

        assertThat(result?.canProceedToNext).isFalse()
    }

    @Test
    fun `SetCanProceedToNext true allows proceeding`() {
        val state = defaultState().copy(canProceedToNext = false)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetCanProceedToNext(true))

        assertThat(result?.canProceedToNext).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")

        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "true")
    }

    // -------------------------------------------------------------------------
    // SetStepConnectionState
    // -------------------------------------------------------------------------

    @Test
    fun `SetStepConnectionState updates connection state for given step`() {
        val result = reducer.reduce(
            defaultState(),
            BtWifiScaleSetupIntent.SetStepConnectionState(
                step = BtWifiSetupStep.CONNECTING_BLUETOOTH,
                connectionState = ConnectionState.Success,
            )
        )

        assertThat(result?.stepConnectionStates?.get(BtWifiSetupStep.CONNECTING_BLUETOOTH))
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `SetStepConnectionState preserves existing step states`() {
        val initial = defaultState().copy(
            stepConnectionStates = mapOf(BtWifiSetupStep.WAKEUP to ConnectionState.Success)
        )

        val result = reducer.reduce(
            initial,
            BtWifiScaleSetupIntent.SetStepConnectionState(
                step = BtWifiSetupStep.CONNECTING_BLUETOOTH,
                connectionState = ConnectionState.Failed.Error,
            )
        )

        assertThat(result?.stepConnectionStates).containsEntry(
            BtWifiSetupStep.WAKEUP, ConnectionState.Success
        )
        assertThat(result?.stepConnectionStates).containsEntry(
            BtWifiSetupStep.CONNECTING_BLUETOOTH, ConnectionState.Failed.Error
        )
    }

    // -------------------------------------------------------------------------
    // Next
    // -------------------------------------------------------------------------

    @Test
    fun `Next clears errorCode`() {
        val state = defaultState().copy(errorCode = "OLD_ERR")

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.Next)

        assertThat(result?.errorCode).isNull()
    }

    @Test
    fun `Next sets isSetupFinished when on last step`() {
        val lastStepState = defaultState().copy(
            currentStep = defaultState().steps.last()
        )

        val result = reducer.reduce(lastStepState, BtWifiScaleSetupIntent.Next)

        assertThat(result?.isSetupFinished).isTrue()
    }

    @Test
    fun `Next does not set isSetupFinished when not on last step`() {
        val state = defaultState() // first step, not last

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.Next)

        assertThat(result?.isSetupFinished).isFalse()
    }

    // -------------------------------------------------------------------------
    // Back
    // -------------------------------------------------------------------------

    @Test
    fun `Back resets nextButtonText to default Next`() {
        val state = defaultState().copy(nextButtonText = "Finish")

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.Back)

        assertThat(result?.nextButtonText).isEqualTo(ScaleSetupStrings.SetupButtons.Next)
    }

    // -------------------------------------------------------------------------
    // TryAgain
    // -------------------------------------------------------------------------

    @Test
    fun `TryAgain clears errorCode and prevents manual progression`() {
        val state = defaultState().copy(errorCode = "ERR_01", canProceedToNext = true)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.TryAgain)

        assertThat(result?.errorCode).isNull()
        assertThat(result?.canProceedToNext).isFalse()
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup true sets isSetupFinished`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.ExitSetup(isSetupFinished = true)
        )

        assertThat(result?.isSetupFinished).isTrue()
    }

    @Test
    fun `ExitSetup false clears isSetupFinished`() {
        val state = defaultState().copy(isSetupFinished = true)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.ExitSetup(isSetupFinished = false))

        assertThat(result?.isSetupFinished).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateNextButtonText
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateNextButtonText changes button text`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.UpdateNextButtonText("Finish")
        )

        assertThat(result?.nextButtonText).isEqualTo("Finish")
    }

    // -------------------------------------------------------------------------
    // RefreshNetworks
    // -------------------------------------------------------------------------

    @Test
    fun `RefreshNetworks navigates to GATHERING_NETWORK step`() {
        val result = reducer.reduce(defaultState(), BtWifiScaleSetupIntent.RefreshNetworks)

        assertThat(result?.currentStep).isEqualTo(BtWifiSetupStep.GATHERING_NETWORK)
    }

    // -------------------------------------------------------------------------
    // SetScaleModePreference
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleModePreference updates isAllBodyMetrics and isHeartRateOn`() {
        val result = reducer.reduce(
            defaultState(),
            BtWifiScaleSetupIntent.SetScaleModePreference(
                isAllBodyMetrics = false,
                isHeartRateOn = true,
            )
        )

        assertThat(result?.isAllBodyMetrics).isFalse()
        assertThat(result?.isHeartRateOn).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetHasSavedSettings
    // -------------------------------------------------------------------------

    @Test
    fun `SetHasSavedSettings true marks settings as saved`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetHasSavedSettings(true)
        )

        assertThat(result?.hasSavedSettings).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetVisitedCustomizeSteps
    // -------------------------------------------------------------------------

    @Test
    fun `SetVisitedCustomizeSteps stores provided step set`() {
        val steps = setOf(CustomizeSettings.SCALE_MODE, CustomizeSettings.DASHBOARD_METRICS)

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetVisitedCustomizeSteps(steps)
        )

        assertThat(result?.visitedCustomizeSteps).containsExactlyElementsIn(steps)
    }

    // -------------------------------------------------------------------------
    // SetScaleMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleMetrics stores provided metrics as immutable list`() {
        val metrics = listOf("weight", "bmi", "bodyFat")

        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetScaleMetrics(metrics)
        )

        assertThat(result?.scaleMetrics).containsExactlyElementsIn(metrics).inOrder()
    }

    // -------------------------------------------------------------------------
    // SetInitialStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetInitialStep stores provided step as initialStep`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetInitialStep(BtWifiSetupStep.PERMISSIONS)
        )

        assertThat(result?.initialStep).isEqualTo(BtWifiSetupStep.PERMISSIONS)
    }

    // -------------------------------------------------------------------------
    // SetLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `SetLatestWeight stores weight`() {
        val result = reducer.reduce(
            defaultState(), BtWifiScaleSetupIntent.SetLatestWeight(75.5)
        )

        assertThat(result?.latestWeight).isEqualTo(75.5)
    }

    @Test
    fun `SetLatestWeight with null clears weight`() {
        val state = defaultState().copy(latestWeight = 80.0)

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.SetLatestWeight(null))

        assertThat(result?.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents return state unchanged (else -> state)
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.OpenHelp)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ShowRestoreAccountAlert returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.ShowRestoreAccountAlert)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `HandlePasswordNetworkStatus returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, BtWifiScaleSetupIntent.HandlePasswordNetworkStatus)

        assertThat(result).isEqualTo(state)
    }
}
