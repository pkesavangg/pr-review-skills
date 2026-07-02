package com.dmdbrands.gurus.weight.features.monitorSetup.reducer

import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MonitorSetupReducerTest {

  private lateinit var reducer: MonitorSetupReducer
  private lateinit var initialState: MonitorSetupState

  @BeforeEach
  fun setUp() {
    reducer = MonitorSetupReducer()
    initialState = MonitorSetupState(
      scaleSetupState = MonitorSetupState().scaleSetupState.copy(
        steps = MonitorSetupStepHelper.stepsForSku("0603").toImmutableList(),
      ),
    )
  }

  @Test
  fun `SetSelectedUser updates selectedUser`() {
    val result = reducer.reduce(initialState, MonitorSetupIntent.SetSelectedUser("A"))
    assertThat(result?.selectedUser).isEqualTo("A")
  }

  @Test
  fun `SetMonitorNickname updates monitorNickname`() {
    val result = reducer.reduce(initialState, MonitorSetupIntent.SetMonitorNickname("My BPM"))
    assertThat(result?.monitorNickname).isEqualTo("My BPM")
  }

  @Test
  fun `SetHasNumericUsers updates hasNumericUsers`() {
    val result = reducer.reduce(initialState, MonitorSetupIntent.SetHasNumericUsers(true))
    assertThat(result?.hasNumericUsers).isTrue()
  }

  @Test
  fun `SetNewStep changes step via base reducer`() {
    val result = reducer.reduce(initialState, DeviceSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
    assertThat(result?.step).isEqualTo(MonitorSetupStep.PERMISSIONS)
  }

  @Test
  fun `BackEnabled updates backEnabled flag`() {
    val result = reducer.reduce(initialState, DeviceSetupIntent.BackEnabled(true))
    assertThat(result?.backEnabled).isTrue()
  }

  @Test
  fun `NextEnabled updates nextEnabled flag`() {
    val result = reducer.reduce(initialState, DeviceSetupIntent.NextEnabled(false))
    assertThat(result?.nextEnabled).isFalse()
  }

  @Test
  fun `AlterConnectionState updates connection state`() {
    val result = reducer.reduce(initialState, DeviceSetupIntent.AlterConnectionState(ConnectionState.Success))
    assertThat(result?.scaleSetupState?.setupState?.connectionState).isEqualTo(ConnectionState.Success)
  }

  @Test
  fun `SetPermissions updates permissions map`() {
    val perms = mutableMapOf("bluetooth_switch" to "enabled")
    val result = reducer.reduce(initialState, DeviceSetupIntent.SetPermissions(perms))
    assertThat(result?.permissions).isEqualTo(perms)
  }

  @Test
  fun `SetSku updates sku in state`() {
    val result = reducer.reduce(initialState, DeviceSetupIntent.SetSku("0604"))
    assertThat(result?.scaleSetupState?.sku).isEqualTo("0604")
  }
}
