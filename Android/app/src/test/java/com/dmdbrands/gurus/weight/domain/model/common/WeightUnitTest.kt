package com.dmdbrands.gurus.weight.domain.model.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WeightUnitTest {

  @Test
  fun `from parses kg`() {
    assertThat(WeightUnit.from("kg")).isEqualTo(WeightUnit.KG)
  }

  @Test
  fun `from parses lb`() {
    assertThat(WeightUnit.from("lb")).isEqualTo(WeightUnit.LB)
  }

  @Test
  fun `from parses lbs as LB`() {
    assertThat(WeightUnit.from("lbs")).isEqualTo(WeightUnit.LB)
  }

  @Test
  fun `from parses lb_oz`() {
    assertThat(WeightUnit.from("lb_oz")).isEqualTo(WeightUnit.LB_OZ)
  }

  @Test
  fun `from is case insensitive`() {
    assertThat(WeightUnit.from("KG")).isEqualTo(WeightUnit.KG)
    assertThat(WeightUnit.from("LB")).isEqualTo(WeightUnit.LB)
    assertThat(WeightUnit.from("LB_OZ")).isEqualTo(WeightUnit.LB_OZ)
  }

  @Test
  fun `from trims whitespace`() {
    assertThat(WeightUnit.from(" kg ")).isEqualTo(WeightUnit.KG)
    assertThat(WeightUnit.from(" lb_oz ")).isEqualTo(WeightUnit.LB_OZ)
  }

  @Test
  fun `from defaults to LB for unknown value`() {
    assertThat(WeightUnit.from("unknown")).isEqualTo(WeightUnit.LB)
  }

  @Test
  fun `from defaults to LB for null`() {
    assertThat(WeightUnit.from(null)).isEqualTo(WeightUnit.LB)
  }

  @Test
  fun `LB unit display is lbs forward slash ft`() {
    assertThat(WeightUnit.LB.unit).isEqualTo("lbs / ft")
  }

  @Test
  fun `KG unit display is kg forward slash cm`() {
    assertThat(WeightUnit.KG.unit).isEqualTo("kg / cm")
  }

  @Test
  fun `LB_OZ unit display is lbs ampersand oz forward slash in`() {
    assertThat(WeightUnit.LB_OZ.unit).isEqualTo("lbs & oz / in")
  }

  @Test
  fun `LB_OZ value is lb_oz`() {
    assertThat(WeightUnit.LB_OZ.value).isEqualTo("lb_oz")
  }

  @Test
  fun `LB_OZ label is lbs ampersand oz`() {
    assertThat(WeightUnit.LB_OZ.label).isEqualTo("lbs & oz")
  }
}
