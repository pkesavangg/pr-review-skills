package com.dmdbrands.gurus.weight.features.addDevice.strings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for [AddDeviceScreenStrings].
 *
 * MOB-509: the My Devices screen (AddDeviceScreen, reached from Settings > My Devices)
 * must show "My Devices" as its app bar title — [AddDeviceScreenStrings.Header] — to
 * match iOS, not the legacy "Add & Edit Devices" copy.
 */
class AddScaleScreenStringsTest {
  @Test
  fun `app bar header shows My Devices`() {
    assertThat(AddDeviceScreenStrings.Header).isEqualTo("My Devices")
  }
}
