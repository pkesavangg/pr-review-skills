package com.dmdbrands.gurus.weight.features.common.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [ReadingToastStrings], including the MOB-426 no-baby-profile copy
 * shown when a baby reading arrives with no baby to save it to.
 */
class ReadingToastStringsTest {

  @Test
  fun `title is the generic New Reading Received for every type`() {
    // Figma 30295-24793 / 30295-25144: the arrival-card title is the generic "New Reading Received"
    // for every product — the value and its product colour convey the type.
    assertThat(ReadingToastStrings.title(ProductType.MY_WEIGHT)).isEqualTo("New Reading Received")
    assertThat(ReadingToastStrings.title(ProductType.BLOOD_PRESSURE)).isEqualTo("New Reading Received")
    assertThat(ReadingToastStrings.title(ProductType.BABY)).isEqualTo("New Reading Received")
  }

  @Test
  fun `single-baby title embeds the upper-cased baby name`() {
    assertThat(ReadingToastStrings.titleForBaby("Princy")).isEqualTo("New Reading Received for PRINCY")
  }

  @Test
  fun `moreReadings pill copy includes the count`() {
    assertThat(ReadingToastStrings.moreReadings(2)).isEqualTo("2 more readings received for this session")
    assertThat(ReadingToastStrings.View).isEqualTo("VIEW")
  }

  @Test
  fun `assign-to-new-baby row copy is exposed for the picker`() {
    assertThat(ReadingToastStrings.AssignModal.AssignNewBaby).isEqualTo("Assign to new baby")
    assertThat(ReadingToastStrings.AssignModal.AssignNewBabySubtitle).isEqualTo("create new baby profile")
  }

  @Test
  fun `baby uses ASSIGN as the primary action, others use SAVE`() {
    assertThat(ReadingToastStrings.primaryAction(ProductType.BABY)).isEqualTo("ASSIGN")
    assertThat(ReadingToastStrings.primaryAction(ProductType.MY_WEIGHT)).isEqualTo("SAVE")
    assertThat(ReadingToastStrings.primaryAction(ProductType.BLOOD_PRESSURE)).isEqualTo("SAVE")
  }

  @Test
  fun `baby uses DON'T ASSIGN as the secondary action, others use DISCARD`() {
    assertThat(ReadingToastStrings.secondaryAction(ProductType.BABY)).isEqualTo("DON'T ASSIGN")
    assertThat(ReadingToastStrings.secondaryAction(ProductType.MY_WEIGHT)).isEqualTo("DISCARD")
    assertThat(ReadingToastStrings.secondaryAction(ProductType.BLOOD_PRESSURE)).isEqualTo("DISCARD")
  }

  @Test
  fun `no-baby copy matches the MOB-426 design`() {
    assertThat(ReadingToastStrings.NoBabyTitle).isEqualTo("New Reading Received")
    assertThat(ReadingToastStrings.NoBabySubtitle).isEqualTo("Add a baby to save this reading.")
    assertThat(ReadingToastStrings.AddBaby).isEqualTo("ADD A BABY")
    assertThat(ReadingToastStrings.Discard).isEqualTo("DISCARD")
  }

  @Test
  fun `assignedTo uppercases the baby name`() {
    assertThat(ReadingToastStrings.assignedTo("Emma")).isEqualTo("Reading assigned to EMMA")
  }

  @Test
  fun `save-failed copy is the user-facing retry message`() {
    assertThat(ReadingToastStrings.SaveFailed).isEqualTo("Couldn't save the reading. Please try again.")
  }

  @Test
  fun `no-baby strings are non-blank`() {
    listOf(
      ReadingToastStrings.NoBabyTitle,
      ReadingToastStrings.NoBabySubtitle,
      ReadingToastStrings.AddBaby,
      ReadingToastStrings.Discard,
    ).forEach { assertThat(it).isNotEmpty() }
  }
}
