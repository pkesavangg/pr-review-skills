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

        @Test
        fun `converts single decigram`() {
            // 1 decigram = 0.1 grams
            assertThat(ConversionTools.convertDecigramsToGrams(1)).isEqualTo(0.1)
        }

        @Test
        fun `converts value with exact tenths`() {
            // 123 decigrams = 12.3 grams (Int input always divides cleanly by 10)
            assertThat(ConversionTools.convertDecigramsToGrams(123)).isEqualTo(12.3)
        }

        @Test
        fun `converts zero`() {
            assertThat(ConversionTools.convertDecigramsToGrams(0)).isEqualTo(0.0)
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
            assertThat(ConversionTools.convertDecigramsToOz(dg)).isWithin(0.05).of(4.0)
        }

        @Test
        fun `converts zero decigrams`() {
            assertThat(ConversionTools.convertDecigramsToLb(0)).isEqualTo(0)
            assertThat(ConversionTools.convertDecigramsToOz(0)).isEqualTo(0.0)
        }

        @Test
        fun `handles oz carry-over to lbs at boundary`() {
            // 4522 decigrams ≈ 15.95 oz, which rounds to 16.0 oz — must carry to 1 lb 0.0 oz
            val dg = 4522
            assertThat(ConversionTools.convertDecigramsToLb(dg)).isEqualTo(1)
            assertThat(ConversionTools.convertDecigramsToOz(dg)).isEqualTo(0.0)
        }

        @Test
        fun `lbOz pair is always normalized with oz less than 16`() {
            // Values at or near the 15.95 oz rounding boundary that would otherwise
            // produce "0 lbs 16.0 oz" without the carry-over guard.
            listOf(4522, 4525, 4530).forEach { dg ->
                val (_, oz) = ConversionTools.convertDecigramsToLbOz(dg)
                assertThat(oz).isLessThan(16.0)
            }
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
            assertThat(ConversionTools.convertDecigramsToOz(dg)).isWithin(0.05).of(4.0)
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
            assertThat(oz).isWithin(0.05).of(4.0)
        }

        @Test
        fun `lbOz - heavy baby uses 2 oz graduation`() {
            // 12000g = 120,000 decigrams (above 11340g)
            val (lbs, oz) = ConversionTools.convert0220DecigramsToLbOz(120_000)
            assertThat(lbs).isAtLeast(26)
            // oz should be rounded to nearest 2
            assertThat(oz % 2).isWithin(0.1).of(0.0)
        }

        @Test
        fun `lbOz - oz is always less than 16 across full range`() {
            var dg = 0
            while (dg <= 200_000) {
                val (_, oz) = ConversionTools.convert0220DecigramsToLbOz(dg)
                assertThat(oz).isLessThan(16.0)
                dg += 10
            }
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
            assertThat(oz).isWithin(0.05).of(4.0)
        }

        @Test
        fun `lbOz - heavy baby indexing is 50`() {
            // 12000g = 120,000 decigrams
            val (lbs, oz) = ConversionTools.convert0222DecigramsToLbOz(120_000)
            assertThat(lbs).isAtLeast(26)
            // With indexing 50, oz rounds to nearest 2.0
            assertThat(oz % 2).isWithin(0.1).of(0.0)
        }

        @Test
        fun `lbOz - oz is always less than 16 across full range (no carry bug)`() {
            // Sweep the input range in 1g steps and verify invariant: oz < 16.0
            var dg = 0
            while (dg <= 200_000) {
                val (_, oz) = ConversionTools.convert0222DecigramsToLbOz(dg)
                assertThat(oz).isLessThan(16.0)
                dg += 10
            }
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
        fun `0220 source applies graduation (differs from generic)`() {
            // At 1460 dg, generic produces "5.2 oz" but 0220 graduation gives "5.1 oz"
            // due to different conversion constants (283.495 vs 28.35*10).
            val graduated = ConversionTools.convertBabyWeightToDisplay(1_460, "0220", false)
            val generic = ConversionTools.convertBabyWeightToDisplay(1_460, "manual", false)
            assertThat(graduated).isNotEqualTo(generic)
            assertThat(graduated).contains("5.1 oz")
        }

        @Test
        fun `0222 source applies calibration (differs from generic)`() {
            // 0222 uses Transtek calibration formula; at 1460 dg the result should
            // differ from the generic path.
            val calibrated = ConversionTools.convertBabyWeightToDisplay(1_460, "0222", false)
            val generic = ConversionTools.convertBabyWeightToDisplay(1_460, "manual", false)
            assertThat(calibrated).isNotEqualTo(generic)
        }

        @Test
        fun `0220 metric uses graduation kg`() {
            val result = ConversionTools.convertBabyWeightToDisplay(50_000, "0220", true)
            assertThat(result).contains("kg")
        }

        @Test
        fun `0222 metric uses same graduation as 0220`() {
            val result0220 = ConversionTools.convertBabyWeightToDisplay(50_000, "0220", true)
            val result0222 = ConversionTools.convertBabyWeightToDisplay(50_000, "0222", true)
            assertThat(result0222).contains("kg")
            assertThat(result0222).isEqualTo(result0220)
        }

        @Test
        fun `null source falls back to generic conversion`() {
            val result = ConversionTools.convertBabyWeightToDisplay(32_890, null, false)
            assertThat(result).contains("lbs")
        }

        @Test
        fun `source containing 0222 substring but not exact routes to 0220 graduation (babyApp parity)`() {
            // babyApp uses `source === '0222'` for the 0222 calibration branch and
            // falls through to 0220 graduation for any other 0220/0222-substring match.
            // See unit-conversion.service.ts line 146.
            val substring0222 = ConversionTools.convertBabyWeightToDisplay(1_460, "BS 0222", false)
            val exact0220 = ConversionTools.convertBabyWeightToDisplay(1_460, "0220", false)
            val exact0222 = ConversionTools.convertBabyWeightToDisplay(1_460, "0222", false)
            assertThat(substring0222).isEqualTo(exact0220)
            assertThat(substring0222).isNotEqualTo(exact0222)
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

        @Test
        fun `length imperial uses 0 decimal places`() {
            // 500 mm = 19.685 in → rounded to "20 in"
            assertThat(ConversionTools.convertBabyLengthToDisplay(500, false)).isEqualTo("20 in")
        }

        @Test
        fun `length metric uses 1 decimal place`() {
            // 500 mm = 50.0 cm
            assertThat(ConversionTools.convertBabyLengthToDisplay(500, true)).isEqualTo("50.0 cm")
        }
    }

    // -------------------------------------------------------------------------
    // Graduation threshold boundaries
    // -------------------------------------------------------------------------

    @Nested
    inner class GraduationThresholds {
        // 0220/0222 graduation thresholds in babyApp:
        //   < 8165 g  → light branch  (5g for 0220, 0.1 oz indexing for 0222)
        //   < 11340 g → medium branch (10g, 0.2 oz)
        //   ≥ 11340 g → heavy branch  (50g, 2 oz)
        // Boundary values in decigrams: 81_650 (18 lb) and 113_400 (25 lb).

        @Test
        fun `0220 kg is continuous across the 18 lb boundary`() {
            val below = ConversionTools.convert0220DecigramsToKg(81_649)
            val atBoundary = ConversionTools.convert0220DecigramsToKg(81_650)
            // Light branch rounds to 5 g (0.005 kg), medium rounds to 10 g (0.01 kg).
            // The two values must not jump by more than the medium step.
            assertThat(kotlin.math.abs(atBoundary - below)).isLessThan(0.011)
        }

        @Test
        fun `0220 kg is continuous across the 25 lb boundary`() {
            val below = ConversionTools.convert0220DecigramsToKg(113_399)
            val atBoundary = ConversionTools.convert0220DecigramsToKg(113_400)
            // Medium branch rounds to 10 g, heavy rounds to 50 g.
            assertThat(kotlin.math.abs(atBoundary - below)).isLessThan(0.051)
        }

        @Test
        fun `0220 lbOz is continuous across the 18 lb boundary`() {
            val (lbsBelow, ozBelow) = ConversionTools.convert0220DecigramsToLbOz(81_649)
            val (lbsAt, ozAt) = ConversionTools.convert0220DecigramsToLbOz(81_650)
            // Both values should be ~18 lb 0 oz, drift below ~0.3 oz.
            val totalBelow = lbsBelow * 16 + ozBelow
            val totalAt = lbsAt * 16 + ozAt
            assertThat(kotlin.math.abs(totalAt - totalBelow)).isLessThan(0.3)
        }

        @Test
        fun `0220 lbOz is continuous across the 25 lb boundary`() {
            val (lbsBelow, ozBelow) = ConversionTools.convert0220DecigramsToLbOz(113_399)
            val (lbsAt, ozAt) = ConversionTools.convert0220DecigramsToLbOz(113_400)
            val totalBelow = lbsBelow * 16 + ozBelow
            val totalAt = lbsAt * 16 + ozAt
            assertThat(kotlin.math.abs(totalAt - totalBelow)).isLessThan(2.1)
        }

        @Test
        fun `0222 lbOz is continuous across the 18 lb boundary`() {
            val (lbsBelow, ozBelow) = ConversionTools.convert0222DecigramsToLbOz(81_649)
            val (lbsAt, ozAt) = ConversionTools.convert0222DecigramsToLbOz(81_650)
            val totalBelow = lbsBelow * 16 + ozBelow
            val totalAt = lbsAt * 16 + ozAt
            assertThat(kotlin.math.abs(totalAt - totalBelow)).isLessThan(0.3)
        }

        @Test
        fun `0222 lbOz is continuous across the 25 lb boundary`() {
            val (lbsBelow, ozBelow) = ConversionTools.convert0222DecigramsToLbOz(113_399)
            val (lbsAt, ozAt) = ConversionTools.convert0222DecigramsToLbOz(113_400)
            val totalBelow = lbsBelow * 16 + ozBelow
            val totalAt = lbsAt * 16 + ozAt
            assertThat(kotlin.math.abs(totalAt - totalBelow)).isLessThan(2.1)
        }
    }

    // -------------------------------------------------------------------------
    // Negative / invalid input
    // -------------------------------------------------------------------------

    @Nested
    inner class NegativeInput {
        @Test
        fun `negative decigrams produces non-positive kg`() {
            assertThat(ConversionTools.convertDecigramsToKg(-100)).isAtMost(0.0)
        }

        @Test
        fun `negative millimeters produces non-positive cm`() {
            assertThat(ConversionTools.convertMmToCm(-50)).isAtMost(0.0)
        }
    }
}
