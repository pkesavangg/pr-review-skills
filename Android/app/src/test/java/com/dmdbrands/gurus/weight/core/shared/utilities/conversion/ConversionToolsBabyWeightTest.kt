package com.dmdbrands.gurus.weight.core.shared.utilities.conversion

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the lb/oz decomposition contract:
 *   convertDecigramsToLb  → whole pounds (integer part only)
 *   convertDecigramsToOz  → REMAINDER ounces after whole pounds (not total ounces)
 *
 * Together they form a complete decomposition:
 *   total_oz ≈ lbs * 16 + oz
 */
class ConversionToolsBabyWeightTest {

  @Test
  fun `64538 decigrams decomposes to 14 lbs and 3_7 oz`() {
    val decigrams = 64538
    val lbs = ConversionTools.convertDecigramsToLb(decigrams)
    val oz = ConversionTools.convertDecigramsToOz(decigrams)

    assertEquals("whole pounds", 14, lbs)
    assertEquals("remainder ounces", 3.7, oz, 0.01)
  }

  @Test
  fun `zero decigrams yields 0 lbs and 0 oz`() {
    assertEquals(0, ConversionTools.convertDecigramsToLb(0))
    assertEquals(0.0, ConversionTools.convertDecigramsToOz(0), 0.001)
  }

  @Test
  fun `exact 1-pound boundary (4535_92 decigrams) yields 1 lb and 0 oz`() {
    // 1 lb = 16 oz = 16 * 283.495 = 4535.92 decigrams
    val decigrams = 4536 // rounded up from 4535.92
    val lbs = ConversionTools.convertDecigramsToLb(decigrams)
    val oz = ConversionTools.convertDecigramsToOz(decigrams)

    assertEquals(1, lbs)
    assertEquals(0.0, oz, 0.1)
  }

  @Test
  fun `lb-to-decigrams round-trips with convertDecigramsToLbExact`() {
    val originalLbs = 10.5
    val decigrams = ConversionTools.convertLbToDecigrams(originalLbs)
    val roundTrip = ConversionTools.convertDecigramsToLbExact(decigrams)

    // Small integer-truncation error from convertLbToDecigrams returning Int
    assertEquals(originalLbs, roundTrip, 0.01)
  }
}
