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

  // Unit Type dialog labels — canonical set (MOB-667 / MOB-1250): "&" separates weight
  // from length, singular "lb", compound weight hyphenated "lb-oz", adult height "ft"
  // (My Weight / [unit]) vs baby length "in" (My Kids / [babyUnit]).
  @Test
  fun `LB unit display is lb ampersand ft`() {
    assertThat(WeightUnit.LB.unit).isEqualTo("lb & ft")
  }

  @Test
  fun `KG unit display is kg ampersand cm`() {
    assertThat(WeightUnit.KG.unit).isEqualTo("kg & cm")
  }

  @Test
  fun `LB_OZ unit display is lb-oz ampersand in`() {
    assertThat(WeightUnit.LB_OZ.unit).isEqualTo("lb-oz & in")
  }

  @Test
  fun `LB babyUnit display is lb ampersand in`() {
    assertThat(WeightUnit.LB.babyUnit).isEqualTo("lb & in")
  }

  @Test
  fun `KG babyUnit display is kg ampersand cm`() {
    assertThat(WeightUnit.KG.babyUnit).isEqualTo("kg & cm")
  }

  @Test
  fun `LB_OZ babyUnit display is lb-oz ampersand in`() {
    assertThat(WeightUnit.LB_OZ.babyUnit).isEqualTo("lb-oz & in")
  }

  @Test
  fun `LB_OZ value is lb_oz`() {
    assertThat(WeightUnit.LB_OZ.value).isEqualTo("lb_oz")
  }

  // [label] is the short value-suffix printed next to weights (history, notifications).
  // Adult weight is singular "lb" per the non-pluralised unit convention (MOB-655/657).
  @Test
  fun `LB label is singular lb`() {
    assertThat(WeightUnit.LB.label).isEqualTo("lb")
  }

  @Test
  fun `LB_OZ label is lbs ampersand oz`() {
    assertThat(WeightUnit.LB_OZ.label).isEqualTo("lbs & oz")
  }
}
