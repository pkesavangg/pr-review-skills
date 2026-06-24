package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers the [ReadingToast] model, focusing on the MOB-426 no-baby-profile state
 * (the "ADD A BABY" CTA) added to the reading-arrival toast.
 */
class ReadingToastTest {

  private fun toast(
    type: ProductType = ProductType.BABY,
    assignedTo: String? = null,
    noBabyProfile: Boolean = false,
    primaryAction: () -> Unit = {},
    secondaryAction: () -> Unit = {},
  ) = ReadingToast(
    reading = "14 lbs 6 oz",
    type = type,
    timestamp = "Just now",
    assignedTo = assignedTo,
    noBabyProfile = noBabyProfile,
    primaryAction = primaryAction,
    secondaryAction = secondaryAction,
  )

  @Test
  fun `noBabyProfile defaults to false`() {
    assertThat(toast().noBabyProfile).isFalse()
  }

  @Test
  fun `noBabyProfile is true when a baby reading has no profile`() {
    assertThat(toast(noBabyProfile = true).noBabyProfile).isTrue()
  }

  @Test
  fun `message combines the type title and the reading value`() {
    val readingToast = toast(type = ProductType.BABY)
    assertThat(readingToast.message)
      .isEqualTo("${ReadingToastStrings.title(ProductType.BABY)} · 14 lbs 6 oz")
  }

  @Test
  fun `primaryAction is invoked when triggered`() {
    var invoked = false
    toast(primaryAction = { invoked = true }).primaryAction()
    assertThat(invoked).isTrue()
  }

  @Test
  fun `secondaryAction is invoked when triggered`() {
    var invoked = false
    toast(secondaryAction = { invoked = true }).secondaryAction()
    assertThat(invoked).isTrue()
  }

  @Test
  fun `no-baby toast carries the discard and add-a-baby actions independently`() {
    var discarded = false
    var added = false
    val readingToast = toast(
      noBabyProfile = true,
      primaryAction = { added = true },
      secondaryAction = { discarded = true },
    )

    readingToast.secondaryAction()
    assertThat(discarded).isTrue()
    assertThat(added).isFalse()

    readingToast.primaryAction()
    assertThat(added).isTrue()
  }
}
