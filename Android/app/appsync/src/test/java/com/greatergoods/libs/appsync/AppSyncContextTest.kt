package com.greatergoods.libs.appsync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Tests for [findActivity], which resolves the hosting [Activity] from a (possibly wrapped) [Context].
 *
 * Regression coverage for MOB-198: a Compose `LocalContext.current` is usually a [ContextWrapper]
 * rather than the [Activity] itself, so a direct cast failed and the AppSync scanner never launched.
 */
class AppSyncContextTest {

  @Test
  fun `returns the activity when context is already an Activity`() {
    val activity = mockk<Activity>()

    assertThat(activity.findActivity()).isSameInstanceAs(activity)
  }

  @Test
  fun `unwraps a single ContextWrapper to find the Activity`() {
    val activity = mockk<Activity>()
    val wrapper = mockk<ContextWrapper>()
    every { wrapper.baseContext } returns activity

    assertThat(wrapper.findActivity()).isSameInstanceAs(activity)
  }

  @Test
  fun `unwraps a nested ContextWrapper chain to find the Activity`() {
    val activity = mockk<Activity>()
    val inner = mockk<ContextWrapper>()
    val outer = mockk<ContextWrapper>()
    every { inner.baseContext } returns activity
    every { outer.baseContext } returns inner

    assertThat(outer.findActivity()).isSameInstanceAs(activity)
  }

  @Test
  fun `returns null when the context is a plain Context with no Activity`() {
    val context = mockk<Context>()

    assertThat(context.findActivity()).isNull()
  }

  @Test
  fun `returns null when a ContextWrapper chain contains no Activity`() {
    val base = mockk<Context>()
    val wrapper = mockk<ContextWrapper>()
    every { wrapper.baseContext } returns base

    assertThat(wrapper.findActivity()).isNull()
  }
}
