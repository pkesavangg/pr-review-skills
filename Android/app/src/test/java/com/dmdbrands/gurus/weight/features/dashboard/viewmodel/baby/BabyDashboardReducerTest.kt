package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BabyDashboardReducer], covering the empty/first-run flag and the
 * CONNECT DEVICE action added in MOB-432.
 */
class BabyDashboardReducerTest {

  private lateinit var reducer: BabyDashboardReducer

  @BeforeEach
  fun setUp() {
    reducer = BabyDashboardReducer()
  }

  @Test
  fun `default state is not empty`() {
    assertThat(BabyDashboardState().isEmpty).isFalse()
  }

  @Test
  fun `SetIsEmpty true flags the state empty`() {
    val result = reducer.reduce(BabyDashboardState(isEmpty = false), BabyDashboardIntent.SetIsEmpty(true))

    assertThat(result?.isEmpty).isTrue()
  }

  @Test
  fun `SetIsEmpty false clears the empty flag`() {
    val result = reducer.reduce(BabyDashboardState(isEmpty = true), BabyDashboardIntent.SetIsEmpty(false))

    assertThat(result?.isEmpty).isFalse()
  }

  @Test
  fun `OnConnectDevice does not change state`() {
    val state = BabyDashboardState(isEmpty = true)

    val result = reducer.reduce(state, BabyDashboardIntent.OnConnectDevice)

    assertThat(result).isEqualTo(state)
  }
}
