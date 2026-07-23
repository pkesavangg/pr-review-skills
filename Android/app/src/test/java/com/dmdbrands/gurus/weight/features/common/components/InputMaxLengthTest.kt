package com.dmdbrands.gurus.weight.features.common.components

import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Locks the AppInput/AppTextArea max-length cap that the Manual Entry notes field relies on
 * (MOB-403). The input handler processes a change only when [isWithinMaxLength] is true, so a
 * false result means the extra character is dropped and typing is blocked at the limit.
 */
class InputMaxLengthTest {

  @Test
  fun `null maxLength imposes no limit`() {
    assertThat(isWithinMaxLength("a".repeat(1000), null)).isTrue()
  }

  @Test
  fun `value below the limit is accepted`() {
    assertThat(isWithinMaxLength("a".repeat(279), 280)).isTrue()
  }

  @Test
  fun `value exactly at the limit is accepted`() {
    assertThat(isWithinMaxLength("a".repeat(280), 280)).isTrue()
  }

  @Test
  fun `value over the limit is rejected`() {
    assertThat(isWithinMaxLength("a".repeat(281), 280)).isFalse()
  }

  @Test
  fun `notes field caps input at 280 characters`() {
    val cap = EntryScreenStrings.NOTES_MAX_LENGTH
    assertThat(cap).isEqualTo(280)
    // The 280th character is allowed; the 281st is blocked.
    assertThat(isWithinMaxLength("n".repeat(280), cap)).isTrue()
    assertThat(isWithinMaxLength("n".repeat(281), cap)).isFalse()
  }
}
