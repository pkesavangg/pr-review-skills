package com.dmdbrands.gurus.weight.theme

import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import android.content.res.Configuration

/**
 * Pure-function coverage for the night-mode bit-masking used by MainActivity.attachBaseContext
 * and MeAppTheme. A regression here is exactly what reintroduces MA-3996, so the ThemeMode -> night
 * flag mapping and the uiMode bit preservation are pinned down explicitly.
 */
class ThemeUiModeTest {

  @Test
  fun `resolveNightFlag maps LIGHT to night-no regardless of current uiMode`() {
    val current = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
    assertThat(resolveNightFlag(ThemeMode.LIGHT, current))
      .isEqualTo(Configuration.UI_MODE_NIGHT_NO)
  }

  @Test
  fun `resolveNightFlag maps DARK to night-yes regardless of current uiMode`() {
    val current = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
    assertThat(resolveNightFlag(ThemeMode.DARK, current))
      .isEqualTo(Configuration.UI_MODE_NIGHT_YES)
  }

  @Test
  fun `resolveNightFlag follows current night bits for SYSTEM`() {
    val systemDark = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
    val systemLight = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO

    assertThat(resolveNightFlag(ThemeMode.SYSTEM, systemDark))
      .isEqualTo(Configuration.UI_MODE_NIGHT_YES)
    assertThat(resolveNightFlag(ThemeMode.SYSTEM, systemLight))
      .isEqualTo(Configuration.UI_MODE_NIGHT_NO)
  }

  @Test
  fun `resolveNightFlag follows current night bits for UNRECOGNIZED`() {
    val current = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
    assertThat(resolveNightFlag(ThemeMode.UNRECOGNIZED, current))
      .isEqualTo(Configuration.UI_MODE_NIGHT_YES)
  }

  @Test
  fun `applyNightFlag preserves non-night uiMode bits`() {
    val current = Configuration.UI_MODE_TYPE_CAR or Configuration.UI_MODE_NIGHT_NO

    val result = applyNightFlag(current, Configuration.UI_MODE_NIGHT_YES)

    // Night bits flipped to YES...
    assertThat(result and Configuration.UI_MODE_NIGHT_MASK)
      .isEqualTo(Configuration.UI_MODE_NIGHT_YES)
    // ...while the device-type bits (CAR) survive untouched.
    assertThat(result and Configuration.UI_MODE_TYPE_MASK)
      .isEqualTo(Configuration.UI_MODE_TYPE_CAR)
  }

  @Test
  fun `applyNightFlag replaces existing night bits rather than ORing them`() {
    val alreadyDark = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES

    val result = applyNightFlag(alreadyDark, Configuration.UI_MODE_NIGHT_NO)

    assertThat(result and Configuration.UI_MODE_NIGHT_MASK)
      .isEqualTo(Configuration.UI_MODE_NIGHT_NO)
  }
}
