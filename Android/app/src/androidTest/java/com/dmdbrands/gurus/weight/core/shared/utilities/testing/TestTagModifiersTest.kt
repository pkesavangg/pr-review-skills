package com.dmdbrands.gurus.weight.core.shared.utilities.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [exposeTestTagsAsResourceId] surfaces a Compose `testTag` as an Android `resource-id`
 * that UiAutomator — the engine Appium drives — can select. This is the exact capability MOB-1099
 * adds; without the helper the tag is invisible to UiAutomator and this assertion fails.
 *
 * Runs against the debug build, where [exposeTestTagsAsResourceId] is active (it is gated to
 * `BuildConfig.DEBUG`).
 */
@RunWith(AndroidJUnit4::class)
class TestTagModifiersTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun testTag_isExposedAsResourceId_whenHelperApplied() {
    composeRule.setContent {
      Box(Modifier.fillMaxSize().exposeTestTagsAsResourceId()) {
        Box(Modifier.size(48.dp).testTag(PROBE_TAG))
      }
    }
    composeRule.waitForIdle()

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val foundByResourceId = device.wait(Until.hasObject(By.res(PROBE_TAG)), TIMEOUT_MS)

    assertThat(foundByResourceId).isTrue()
  }

  private companion object {
    const val PROBE_TAG = "mob1099_probe_tag"
    const val TIMEOUT_MS = 5_000L
  }
}
