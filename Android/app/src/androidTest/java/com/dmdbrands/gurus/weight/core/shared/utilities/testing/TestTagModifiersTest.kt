package com.dmdbrands.gurus.weight.core.shared.utilities.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
 * Contract test for the testTag → Android `resource-id` exposure that UiAutomator (the engine
 * Appium drives) depends on. It is the Android counterpart of the iOS
 * `AccessibilityIDContractUITests`: each applied tag must resolve to **exactly one** node, and
 * the per-window exposure rule (MOB-1099 / MOB-1503) is guarded so a redesign that drops
 * `exposeTestTagsAsResourceId()` from a dialog root goes red here instead of silently breaking
 * automation.
 *
 * Runs against the debug build, where [exposeTestTagsAsResourceId] is active (gated to
 * `BuildConfig.DEBUG`). In CI it runs on a headless Gradle Managed Device (MOB-1509):
 * `./gradlew :app:pixel6Api30AtdDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class TestTagModifiersTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val device: UiDevice
    get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  @Test
  fun testTag_isExposedAsResourceId_whenHelperApplied() {
    composeRule.setContent {
      Box(Modifier.fillMaxSize().exposeTestTagsAsResourceId()) {
        Box(Modifier.size(48.dp).testTag(PROBE_TAG))
      }
    }
    composeRule.waitForIdle()

    val foundByResourceId = device.wait(Until.hasObject(By.res(PROBE_TAG)), TIMEOUT_MS)

    assertThat(foundByResourceId).isTrue()
  }

  @Test
  fun eachExposedTag_resolvesToExactlyOneNode() {
    val tags = listOf(
      TestTags.Login.EmailField,
      TestTags.Login.PasswordField,
      TestTags.Login.SubmitButton,
    )
    composeRule.setContent {
      Column(Modifier.fillMaxSize().exposeTestTagsAsResourceId()) {
        tags.forEach { Box(Modifier.size(48.dp).testTag(it)) }
      }
    }
    composeRule.waitForIdle()

    // The core contract (mirrors iOS assertResolvesToOne): one applied tag == one queryable node.
    tags.forEach { tag ->
      assertThat(device.wait(Until.hasObject(By.res(tag)), TIMEOUT_MS)).isTrue()
      assertThat(device.findObjects(By.res(tag))).hasSize(1)
    }
  }

  @Test
  fun tagInsideDialog_isInvisibleToUiAutomator_untilDialogRootExposesTags() {
    // A Dialog renders in its own window; testTagsAsResourceId does not cross the boundary, so a
    // tag inside a dialog that does NOT re-apply the helper must be unreachable by resource-id.
    composeRule.setContent {
      Box(Modifier.fillMaxSize().exposeTestTagsAsResourceId()) {
        Dialog(onDismissRequest = {}) {
          Box(Modifier.size(48.dp).testTag(DIALOG_PROBE_TAG))
        }
      }
    }
    composeRule.waitForIdle()

    assertThat(device.wait(Until.hasObject(By.res(DIALOG_PROBE_TAG)), SHORT_TIMEOUT_MS)).isFalse()
  }

  @Test
  fun tagInsideDialog_isExposed_whenDialogRootAppliesHelper() {
    composeRule.setContent {
      Box(Modifier.fillMaxSize()) {
        Dialog(onDismissRequest = {}) {
          // Dialog window opts into exposure at its own root — the MOB-1099 / MOB-1503 pattern.
          Box(Modifier.size(48.dp).exposeTestTagsAsResourceId().testTag(DIALOG_PROBE_TAG))
        }
      }
    }
    composeRule.waitForIdle()

    assertThat(device.wait(Until.hasObject(By.res(DIALOG_PROBE_TAG)), TIMEOUT_MS)).isTrue()
  }

  private companion object {
    const val PROBE_TAG = "mob1099_probe_tag"
    const val DIALOG_PROBE_TAG = "mob1509_dialog_probe_tag"
    const val TIMEOUT_MS = 5_000L
    const val SHORT_TIMEOUT_MS = 1_500L
  }
}
