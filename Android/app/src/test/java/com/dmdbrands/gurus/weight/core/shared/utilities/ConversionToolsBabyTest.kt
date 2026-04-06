package com.dmdbrands.gurus.weight.core.shared.utilities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for baby-specific conversion functions in [ConversionTools].
 *
 * Pure math functions — no Android context or mocks needed.
 * Test values derived from babyApp reference implementation.
 */
class ConversionToolsBabyTest {

    // -------------------------------------------------------------------------
    // Metric display conversions
    // -------------------------------------------------------------------------

    @Nested
    inner class DecigramsToKg {
        @Test
        fun `converts typical baby weight`() {
            // 40,000 decigrams = 4.0 kg
            assertThat(ConversionTools.convertDecigramsToKg(40_000)).isEqualTo(4.0)
        }

        @Test
        fun `converts small newborn weight`() {
            // 25,000 decigrams = 2.5 kg
            assertThat(ConversionTools.convertDecigramsToKg(25_000)).isEqualTo(2.5)
        }

        @Test
        fun `converts zero`() {
            assertThat(ConversionTools.convertDecigramsToKg(0)).isEqualTo(0.0)
        }
    }

    @Nested
    inner class DecigramsToGrams {
        @Test
        fun `converts typical value`() {
            // 40,000 decigrams = 4,000.0 grams
            assertThat(ConversionTools.convertDecigramsToGrams(40_000)).isEqualTo(4000.0)
        }

        @Test
        fun `converts small value`() {
            // 55 decigrams = 5.5 grams
            assertThat(ConversionTools.convertDecigramsToGrams(55)).isEqualTo(5.5)
        }
    }

    @Nested
    inner class MmToCm {
        @Test
        fun `converts typical baby length`() {
            // 500 mm = 50.0 cm
            assertThat(ConversionTools.convertMmToCm(500)).isEqualTo(50.0)
        }

        @Test
        fun `converts with fractional result`() {
            // 495 mm = 49.5 cm
            assertThat(ConversionTools.convertMmToCm(495)).isEqualTo(49.5)
        }
    }

    // -------------------------------------------------------------------------
    // Imperial display conversions (existing, now tested)
    // -------------------------------------------------------------------------

    @Nested
    inner class DecigramsToLbOz {
        @Test
        fun `converts 7 lb 4 oz baby`() {
            // 7 lbs 4 oz = 7.25 lbs = 3289 grams = 32,890 decigrams
            val dg = 32_890
            assertThat(ConversionTools.convertDecigramsToLb(dg)).isEqualTo(7)
            assertThat(ConversionTools.convertDecigramsToOz(dg)).isWithin(0.2).of(4.0)
        }

        @Test
        fun `converts zero decigrams`() {
            assertThat(ConversionTools.convertDecigramsToLb(0)).isEqualTo(0)
            assertThat(ConversionTools.convertDecigramsToOz(0)).isEqualTo(0.0)
        }
    }

    @Nested
    inner class MmToInches {
        @Test
        fun `converts 500mm to about 19_7 inches`() {
            assertThat(ConversionTools.convertMmToInches(500)).isWithin(0.1).of(19.7)
        }

        @Test
        fun `converts zero`() {
            assertThat(ConversionTools.convertMmToInches(0)).isEqualTo(0.0)
        }
    }

    // -------------------------------------------------------------------------
    // Reverse / save conversions
    // -------------------------------------------------------------------------

    @Nested
    inner class ReverseConversions {
        @Test
        fun `lbOz to decigrams round-trips`() {
            val dg = ConversionTools.convertLbOzToDecigrams(7, 4.0)
            assertThat(ConversionTools.convertDecigramsToLb(dg)).isEqualTo(7)
            assertThat(ConversionTools.convertDecigramsToOz(dg)).isWithin(0.2).of(4.0)
        }

        @Test
        fun `kg to decigrams round-trips`() {
            val dg = ConversionTools.convertKgToDecigrams(3.5)
            assertThat(ConversionTools.convertDecigramsToKg(dg)).isWithin(0.01).of(3.5)
        }

        @Test
        fun `inches to mm round-trips`() {
            val mm = ConversionTools.convertInchesToMm(19.5)
            assertThat(ConversionTools.convertMmToInches(mm)).isWithin(0.1).of(19.5)
        }

        @Test
        fun `cm to mm round-trips`() {
            val mm = ConversionTools.convertCmToMm(49.5)
            assertThat(ConversionTools.convertMmToCm(mm)).isWithin(0.1).of(49.5)
        }
    }

    // -------------------------------------------------------------------------
    // SKU 0220 graduation
    // -------------------------------------------------------------------------

    @Nested
    inner class Sku0220 {
        @Test
        fun `kg - light baby uses 5g graduation`() {
            // 5000g = 50,000 decigrams, below 8165g threshold
            val kg = ConversionTools.convert0220DecigramsToKg(50_000)
            // 5000g rounded to nearest 5g = 5000g = 5.0 kg
            assertThat(kg).isEqualTo(5.0)
        }

        @Test
        fun `kg - medium baby uses 10g graduation`() {
            // 9000g = 90,000 decigrams, between 8165g and 11340g
            val kg = ConversionTools.convert0220DecigramsToKg(90_000)
            // 9000g / 10 = 900 → 900 / 100 = 9.0 kg
            assertThat(kg).isEqualTo(9.0)
        }

        @Test
        fun `kg - heavy baby uses 50g graduation`() {
            // 12000g = 120,000 decigrams, above 11340g
            val kg = ConversionTools.convert0220DecigramsToKg(120_000)
            // 12000g rounded to nearest 50g = 12000g = 12.0 kg
            assertThat(kg).isEqualTo(12.0)
        }

        @Test
        fun `lbDecimal - light baby uses 0_01 lb graduation`() {
            // 3000g = 30,000 decigrams
            val lbs = ConversionTools.convert0220DecigramsToLbDecimal(30_000)
            // (3000 / 1000) * 2.2046 = 6.6138 → rounded to 0.01 = 6.61
            assertThat(lbs).isWithin(0.01).of(6.61)
        }

        @Test
        fun `lbDecimal - heavy baby uses 0_1 lb graduation`() {
            // 12000g = 120,000 decigrams
            val lbs = ConversionTools.convert0220DecigramsToLbDecimal(120_000)
            // (12000 / 1000) * 2.2046 = 26.4552 → rounded to 0.1 = 26.5
            assertThat(lbs).isWithin(0.1).of(26.5)
        }

        @Test
        fun `lbOz - light baby returns correct pair`() {
            // ~7.25 lbs baby = ~3289g = 32,890 decigrams
            val (lbs, oz) = ConversionTools.convert0220DecigramsToLbOz(32_890)
            assertThat(lbs).isEqualTo(7)
            assertThat(oz).isWithin(0.5).of(4.0)
        }

        @Test
        fun `lbOz - heavy baby uses 2 oz graduation`() {
            // 12000g = 120,000 decigrams (above 11340g)
            val (lbs, oz) = ConversionTools.convert0220DecigramsToLbOz(120_000)
            assertThat(lbs).isAtLeast(26)
            // oz should be rounded to nearest 2
            assertThat(oz % 2).isWithin(0.1).of(0.0)
        }
    }

    // -------------------------------------------------------------------------
    // SKU 0222 graduation
    // -------------------------------------------------------------------------

    @Nested
    inner class Sku0222 {
        @Test
        fun `lbDecimal - light baby uses precise factor`() {
            // 3000g = 30,000 decigrams
            val lbs = ConversionTools.convert0222DecigramsToLbDecimal(30_000)
            // (3000 / 1000) * 2.204623 = 6.613869 → rounded to 0.01 = 6.61
            assertThat(lbs).isWithin(0.01).of(6.61)
        }

        @Test
        fun `lbDecimal - heavy baby uses 0_1 graduation`() {
            // 12000g = 120,000 decigrams
            val lbs = ConversionTools.convert0222DecigramsToLbDecimal(120_000)
            assertThat(lbs).isWithin(0.1).of(26.5)
        }

        @Test
        fun `lbOz - uses calibration formula`() {
            // 3289g = 32,890 decigrams (~7 lb 4 oz)
            val (lbs, oz) = ConversionTools.convert0222DecigramsToLbOz(32_890)
            assertThat(lbs).isEqualTo(7)
            assertThat(oz).isWithin(0.5).of(4.0)
        }

        @Test
        fun `lbOz - heavy baby indexing is 50`() {
            // 12000g = 120,000 decigrams
            val (lbs, oz) = ConversionTools.convert0222DecigramsToLbOz(120_000)
            assertThat(lbs).isAtLeast(26)
            // With indexing 50, oz rounds to nearest 2.0
            assertThat(oz % 2).isWithin(0.1).of(0.0)
        }
    }

    // -------------------------------------------------------------------------
    // Source-aware router
    // -------------------------------------------------------------------------

    @Nested
    inner class SourceAwareRouter {
        @Test
        fun `imperial manual entry formats as lbs oz`() {
            val result = ConversionTools.convertBabyWeightToDisplay(32_890, "manual", false)
            assertThat(result).contains("lbs")
            assertThat(result).contains("oz")
        }

        @Test
        fun `metric manual entry formats as kg`() {
            val result = ConversionTools.convertBabyWeightToDisplay(40_000, "manual", true)
            assertThat(result).contains("kg")
        }

        @Test
        fun `0220 source uses graduation logic`() {
            val result = ConversionTools.convertBabyWeightToDisplay(32_890, "0220", false)
            assertThat(result).contains("lbs")
            assertThat(result).contains("oz")
        }

        @Test
        fun `0222 source uses calibration formula`() {
            val result = ConversionTools.convertBabyWeightToDisplay(32_890, "0222", false)
            assertThat(result).contains("lbs")
            assertThat(result).contains("oz")
        }

        @Test
        fun `0220 metric uses graduation kg`() {
            val result = ConversionTools.convertBabyWeightToDisplay(50_000, "0220", true)
            assertThat(result).contains("kg")
        }

        @Test
        fun `null source falls back to generic conversion`() {
            val result = ConversionTools.convertBabyWeightToDisplay(32_890, null, false)
            assertThat(result).contains("lbs")
        }

        @Test
        fun `length imperial formats as inches`() {
            val result = ConversionTools.convertBabyLengthToDisplay(500, false)
            assertThat(result).contains("in")
        }

        @Test
        fun `length metric formats as cm`() {
            val result = ConversionTools.convertBabyLengthToDisplay(500, true)
            assertThat(result).contains("cm")
        }
    }
}
