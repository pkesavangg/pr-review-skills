package com.dmdbrands.gurus.weight.features.scaleDetails.reducer

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.scaleDetails.Enums.ScaleSettingSteps
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ScaleDetailsReducer].
 *
 * ScaleDetailsReducer is a pure function — no mocking or coroutines needed.
 * [ScaleDetailsState] requires a [FormGroup], so we use [ScaleNameDialogFormControls.create]
 * wrapped in a [FormGroup] to build valid initial state.
 */
class ScaleDetailsReducerTest {

    private lateinit var reducer: ScaleDetailsReducer

    companion object {
        private const val TEST_SSID = "HomeNet"
        private const val TEST_MAC = "AA:BB:CC:DD:EE:FF"
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH_SCAN"
        private const val SCALE_NAME = "My Scale"
    }

    private fun createForm() = FormGroup(ScaleNameDialogFormControls.create())

    private fun defaultState() = ScaleDetailsState(
        scaleNameForm = createForm(),
    )

    @BeforeEach
    fun setUp() {
        reducer = ScaleDetailsReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default ScaleDetailsState has expected initial values`() {
        val state = defaultState()

        assertThat(state.scale).isNull()
        assertThat(state.permissions).isEmpty()
        assertThat(state.settingsScreenStep).isEqualTo(ScaleSettingSteps.NONE)
        assertThat(state.connectedSSID).isNull()
        assertThat(state.wifiMacAddress).isNull()
        assertThat(state.deviceInfo).isNull()
        assertThat(state.isSessionImpedanceEnabled).isFalse()
        assertThat(state.currentTimeFormat).isEqualTo("12H")
        assertThat(state.currentClearDataSelection).isNull()
        assertThat(state.isStartAnimationEnabled).isFalse()
        assertThat(state.isEndAnimationEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScaleInfo
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleInfo stores provided device`() {
        val device: Device = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.SetScaleInfo(device))

        assertThat(result?.scale).isEqualTo(device)
    }

    @Test
    fun `SetScaleInfo replaces previous device`() {
        val deviceA: Device = mockk(relaxed = true)
        val deviceB: Device = mockk(relaxed = true)
        val state = defaultState().copy(scale = deviceA)

        val result = reducer.reduce(state, ScaleDetailsIntent.SetScaleInfo(deviceB))

        assertThat(result?.scale).isEqualTo(deviceB)
    }

    // -------------------------------------------------------------------------
    // SetConnectedSSID
    // -------------------------------------------------------------------------

    @Test
    fun `SetConnectedSSID stores SSID`() {
        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.SetConnectedSSID(TEST_SSID))

        assertThat(result?.connectedSSID).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetConnectedSSID with null clears SSID`() {
        val state = defaultState().copy(connectedSSID = TEST_SSID)

        val result = reducer.reduce(state, ScaleDetailsIntent.SetConnectedSSID(null))

        assertThat(result?.connectedSSID).isNull()
    }

    // -------------------------------------------------------------------------
    // SetWifiMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `SetWifiMacAddress stores mac address`() {
        val result = reducer.reduce(
            defaultState(), ScaleDetailsIntent.SetWifiMacAddress(TEST_MAC)
        )

        assertThat(result?.wifiMacAddress).isEqualTo(TEST_MAC)
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")

        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "true")
    }

    @Test
    fun `SetPermissions with empty map clears permissions`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")
        val state = defaultState().copy(permissions = perms)

        val result = reducer.reduce(state, ScaleDetailsIntent.SetPermissions(mutableMapOf()))

        assertThat(result?.permissions).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetSettingsScreenStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetSettingsScreenStep updates settingsScreenStep`() {
        val result = reducer.reduce(
            defaultState(),
            ScaleDetailsIntent.SetSettingsScreenStep(ScaleSettingSteps.BLUETOOTH_SETTINGS),
        )

        assertThat(result?.settingsScreenStep).isEqualTo(ScaleSettingSteps.BLUETOOTH_SETTINGS)
    }

    @Test
    fun `SetSettingsScreenStep to NONE resets step`() {
        val state = defaultState().copy(settingsScreenStep = ScaleSettingSteps.ADDITIONAL_SETTINGS)

        val result = reducer.reduce(
            state, ScaleDetailsIntent.SetSettingsScreenStep(ScaleSettingSteps.NONE)
        )

        assertThat(result?.settingsScreenStep).isEqualTo(ScaleSettingSteps.NONE)
    }

    // -------------------------------------------------------------------------
    // SetDeviceDetail
    // -------------------------------------------------------------------------

    @Test
    fun `SetDeviceDetail stores device info`() {
        val detail: GGDeviceDetail = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.SetDeviceDetail(detail))

        assertThat(result?.deviceInfo).isEqualTo(detail)
    }

    // -------------------------------------------------------------------------
    // ToggleSessionImpedance
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleSessionImpedance true enables session impedance`() {
        val result = reducer.reduce(
            defaultState(), ScaleDetailsIntent.ToggleSessionImpedance(true)
        )

        assertThat(result?.isSessionImpedanceEnabled).isTrue()
    }

    @Test
    fun `ToggleSessionImpedance false disables session impedance`() {
        val state = defaultState().copy(isSessionImpedanceEnabled = true)

        val result = reducer.reduce(state, ScaleDetailsIntent.ToggleSessionImpedance(false))

        assertThat(result?.isSessionImpedanceEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // ClearScaleData
    // -------------------------------------------------------------------------

    @Test
    fun `ClearScaleData stores data type selection`() {
        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.ClearScaleData("ALL"))

        assertThat(result?.currentClearDataSelection).isEqualTo("ALL")
    }

    @Test
    fun `ClearScaleData with WIFI stores wifi selection`() {
        val result = reducer.reduce(defaultState(), ScaleDetailsIntent.ClearScaleData("WIFI"))

        assertThat(result?.currentClearDataSelection).isEqualTo("WIFI")
    }

    // -------------------------------------------------------------------------
    // ChangeTimeFormat
    // -------------------------------------------------------------------------

    @Test
    fun `ChangeTimeFormat with is12Hour true sets 12H format`() {
        val state = defaultState().copy(currentTimeFormat = "24H")

        val result = reducer.reduce(state, ScaleDetailsIntent.ChangeTimeFormat(is12Hour = true))

        assertThat(result?.currentTimeFormat).isEqualTo("12H")
    }

    @Test
    fun `ChangeTimeFormat with is12Hour false sets 24H format`() {
        val state = defaultState().copy(currentTimeFormat = "12H")

        val result = reducer.reduce(state, ScaleDetailsIntent.ChangeTimeFormat(is12Hour = false))

        assertThat(result?.currentTimeFormat).isEqualTo("24H")
    }

    // -------------------------------------------------------------------------
    // ToggleScaleAnimation
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleScaleAnimation with isStartAnimation true enables start animation`() {
        val result = reducer.reduce(
            defaultState(),
            ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = true),
        )

        assertThat(result?.isStartAnimationEnabled).isTrue()
        assertThat(result?.isEndAnimationEnabled).isFalse() // unchanged
    }

    @Test
    fun `ToggleScaleAnimation with isStartAnimation false enables end animation`() {
        val result = reducer.reduce(
            defaultState(),
            ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = false, enabled = true),
        )

        assertThat(result?.isStartAnimationEnabled).isFalse() // unchanged
        assertThat(result?.isEndAnimationEnabled).isTrue()
    }

    @Test
    fun `ToggleScaleAnimation with isStartAnimation true and enabled false disables start animation`() {
        val state = defaultState().copy(isStartAnimationEnabled = true)

        val result = reducer.reduce(
            state,
            ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = false),
        )

        assertThat(result?.isStartAnimationEnabled).isFalse()
    }

    @Test
    fun `ToggleScaleAnimation with isStartAnimation false and enabled false disables end animation`() {
        val state = defaultState().copy(isEndAnimationEnabled = true)

        val result = reducer.reduce(
            state,
            ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = false, enabled = false),
        )

        assertThat(result?.isEndAnimationEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScaleName
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleName updates the name form control value`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.SetScaleName(SCALE_NAME))

        assertThat(result?.scaleNameForm?.controls?.name?.value).isEqualTo(SCALE_NAME)
    }

    @Test
    fun `SetScaleName with empty string clears name`() {
        val state = defaultState()
        val withName = reducer.reduce(state, ScaleDetailsIntent.SetScaleName(SCALE_NAME))!!

        val result = reducer.reduce(withName, ScaleDetailsIntent.SetScaleName(""))

        assertThat(result?.scaleNameForm?.controls?.name?.value).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — all return state.copy() (state unchanged)
    // -------------------------------------------------------------------------

    @Test
    fun `EditName returns state unchanged`() {
        val state = defaultState().copy(connectedSSID = TEST_SSID)

        val result = reducer.reduce(state, ScaleDetailsIntent.EditName)

        assertThat(result?.connectedSSID).isEqualTo(TEST_SSID)
    }

    @Test
    fun `DeleteScale returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.DeleteScale)

        assertThat(result?.scale).isNull()
    }

    @Test
    fun `Back returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.Back)

        assertThat(result?.settingsScreenStep).isEqualTo(state.settingsScreenStep)
    }

    @Test
    fun `StartFirmwareUpdate returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.StartFirmwareUpdate)

        assertThat(result?.scale).isNull()
    }

    @Test
    fun `ShowTimeFormatDialog returns state unchanged`() {
        val state = defaultState().copy(currentTimeFormat = "24H")

        val result = reducer.reduce(state, ScaleDetailsIntent.ShowTimeFormatDialog)

        assertThat(result?.currentTimeFormat).isEqualTo("24H")
    }

    @Test
    fun `ShowClearDataDialog returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.ShowClearDataDialog)

        assertThat(result?.currentClearDataSelection).isNull()
    }

    @Test
    fun `EnableBodyMetrics returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.EnableBodyMetrics)

        assertThat(result?.isSessionImpedanceEnabled).isEqualTo(state.isSessionImpedanceEnabled)
    }

    @Test
    fun `DownloadLogs returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleDetailsIntent.DownloadLogs)

        assertThat(result?.scale).isNull()
    }
}
