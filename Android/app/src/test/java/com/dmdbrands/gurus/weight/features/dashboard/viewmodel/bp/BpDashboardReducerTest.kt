package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BpDashboardReducer], covering the empty/first-run flag and the
 * CONNECT DEVICE action added in MOB-432.
 */
class BpDashboardReducerTest {

  private lateinit var reducer: BpDashboardReducer

  @BeforeEach
  fun setUp() {
    reducer = BpDashboardReducer()
  }

  @Test
  fun `default state is not empty`() {
    assertThat(BpDashboardState().isEmpty).isFalse()
  }

  @Test
  fun `SetIsEmpty true flags the state empty`() {
    val result = reducer.reduce(BpDashboardState(isEmpty = false), BpDashboardIntent.SetIsEmpty(true))

    assertThat(result?.isEmpty).isTrue()
  }

  @Test
  fun `SetIsEmpty false clears the empty flag`() {
    val result = reducer.reduce(BpDashboardState(isEmpty = true), BpDashboardIntent.SetIsEmpty(false))

    assertThat(result?.isEmpty).isFalse()
  }

  @Test
  fun `OnConnectDevice does not change state`() {
    val state = BpDashboardState(isEmpty = true)

    val result = reducer.reduce(state, BpDashboardIntent.OnConnectDevice)

    assertThat(result).isEqualTo(state)
  }
}
