package com.dmdbrands.gurus.weight.features.addScale.strings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for [AddScaleScreenStrings].
 *
 * MOB-509: the My Devices screen (AddScaleScreen, reached from Settings > My Devices)
 * must show "My Devices" as its app bar title — [AddScaleScreenStrings.Header] — to
 * match iOS, not the legacy "Add & Edit Devices" copy.
 */
class AddScaleScreenStringsTest {
  @Test
  fun `app bar header shows My Devices`() {
    assertThat(AddScaleScreenStrings.Header).isEqualTo("My Devices")
  }
}
