package com.dmdbrands.gurus.weight.core.shared.utilities.testing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins down the shared per-row tag derivation (MOB-1504). Automation selectors depend on the
 * exact `<base>_<stableId>` shape, so the join is verified explicitly rather than left to
 * hand-written string interpolation at each call site.
 */
class TestTagsTest {

  @Test
  fun `rowTag suffixes the base tag with the stable id`() {
    assertThat(TestTags.rowTag(TestTags.Landing.AccountCardRow, "42"))
      .isEqualTo("account_card_row_42")
  }

  @Test
  fun `rowTag renders non-string ids via toString`() {
    assertThat(TestTags.rowTag("integration_row", 7)).isEqualTo("integration_row_7")
  }

  @Test
  fun `rowTag matches the documented account-card-delete example`() {
    assertThat(TestTags.rowTag(TestTags.Landing.AccountCardDeleteButton, "abc"))
      .isEqualTo("${TestTags.Landing.AccountCardDeleteButton}_abc")
  }
}
