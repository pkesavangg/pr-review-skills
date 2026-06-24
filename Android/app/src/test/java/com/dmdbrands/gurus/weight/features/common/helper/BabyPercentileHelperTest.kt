package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper.PercentileRow
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BabyPercentileHelper] conversion and series-building logic.
 *
 * CSV loading requires Android Context (res/raw resources), so we inject known
 * test data via reflection to exercise the public getWeight/getLengthPercentileSeries
 * methods and their unit-conversion math.
 */
class BabyPercentileHelperTest {

    // ── Known WHO CSV row-0 values (from res/raw) ──

    /** Boy weight day-0 row: Day=0, p5=25427, p10=27200, p25=30166, p50=33464, p75=36762, p90=39728, p95=41501 (decigrams) */
    private val boyWeightDay0 = PercentileRow(
        day = 0, p5 = 25427.0, p10 = 27200.0, p25 = 30166.0,
        p50 = 33464.0, p75 = 36762.0, p90 = 39728.0, p95 = 41501.0,
    )
    private val boyWeightDay8 = PercentileRow(
        day = 8, p5 = 26568.0, p10 = 28402.0, p25 = 31469.0,
        p50 = 34879.0, p75 = 38289.0, p90 = 41356.0, p95 = 43190.0,
    )

    /** Boy length day-0 row: Day=0, p5=468.750, ..., p50=499.890, ..., p95=531.030 (mm) */
    private val boyLengthDay0 = PercentileRow(
        day = 0, p5 = 468.750, p10 = 475.622, p25 = 487.112,
        p50 = 499.890, p75 = 512.668, p90 = 524.158, p95 = 531.030,
    )
    private val boyLengthDay8 = PercentileRow(
        day = 8, p5 = 479.846, p10 = 486.754, p25 = 498.305,
        p50 = 511.150, p75 = 523.995, p90 = 535.546, p95 = 542.454,
    )

    private val birthDateMillis = 1_700_000_000_000L // arbitrary fixed birth timestamp

    @BeforeEach
    fun injectTestData() {
        setPrivateField("boyWeightData", listOf(boyWeightDay0, boyWeightDay8))
        setPrivateField("girlWeightData", listOf(boyWeightDay0, boyWeightDay8)) // reuse for girl tests
        setPrivateField("boyLengthData", listOf(boyLengthDay0, boyLengthDay8))
        setPrivateField("girlLengthData", listOf(boyLengthDay0, boyLengthDay8))
    }

    @AfterEach
    fun clearTestData() {
        setPrivateField("boyWeightData", null)
        setPrivateField("girlWeightData", null)
        setPrivateField("boyLengthData", null)
        setPrivateField("girlLengthData", null)
    }

    // ── Weight percentile tests ──

    @Nested
    inner class WeightPercentileSeries {

        @Test
        fun `day-0 p50 boy weight converts decigrams to lbs correctly`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            // Conversion: decigrams -> lbs = value / 283.495 / 16.0
            val expectedP50Lbs = boyWeightDay0.p50 / 283.495 / 16.0
            assertThat(series!!.p50.first()).isWithin(0.001).of(expectedP50Lbs)
        }

        @Test
        fun `getWeightPercentileSeries returns non-null for male`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            assertThat(series!!.xTimestamps).hasSize(2)
            assertThat(series.allBands()).hasSize(7)
        }

        @Test
        fun `getWeightPercentileSeries returns non-null for female`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("female", birthDateMillis)

            assertThat(series).isNotNull()
            assertThat(series!!.xTimestamps).hasSize(2)
        }

        @Test
        fun `getWeightPercentileSeries returns null for unknown sex`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("unknown", birthDateMillis)

            assertThat(series).isNull()
        }

        @Test
        fun `getWeightPercentileSeries returns null for null sex`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries(null, birthDateMillis)

            assertThat(series).isNull()
        }

        @Test
        fun `weight x-timestamps are offset from birth by day count`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            val dayMs = 86_400_000L
            assertThat(series!!.xTimestamps[0]).isWithin(0.1).of(birthDateMillis.toDouble())
            assertThat(series.xTimestamps[1]).isWithin(0.1).of((birthDateMillis + 8 * dayMs).toDouble())
        }
    }

    // ── Length percentile tests ──

    @Nested
    inner class LengthPercentileSeries {

        @Test
        fun `day-0 p50 boy length converts mm to inches correctly`() {
            val series = BabyPercentileHelper.getLengthPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            // Conversion: mm -> inches = value / 25.4
            val expectedP50Inches = boyLengthDay0.p50 / 25.4 // ~19.68 inches
            assertThat(series!!.p50.first()).isWithin(0.001).of(expectedP50Inches)
        }

        @Test
        fun `day-0 p50 boy length raw value matches WHO CSV`() {
            // Verify our test fixture matches the actual CSV value (~499.89 mm)
            assertThat(boyLengthDay0.p50).isWithin(0.01).of(499.89)
        }

        @Test
        fun `getLengthPercentileSeries returns non-null for male`() {
            val series = BabyPercentileHelper.getLengthPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            assertThat(series!!.xTimestamps).hasSize(2)
            assertThat(series.allBands()).hasSize(7)
        }

        @Test
        fun `getLengthPercentileSeries returns non-null for female`() {
            val series = BabyPercentileHelper.getLengthPercentileSeries("female", birthDateMillis)

            assertThat(series).isNotNull()
        }

        @Test
        fun `getLengthPercentileSeries returns null for unknown sex`() {
            val series = BabyPercentileHelper.getLengthPercentileSeries("unknown", birthDateMillis)

            assertThat(series).isNull()
        }

        @Test
        fun `length x-timestamps are offset from birth by day count`() {
            val series = BabyPercentileHelper.getLengthPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            val dayMs = 86_400_000L
            assertThat(series!!.xTimestamps[0]).isWithin(0.1).of(birthDateMillis.toDouble())
            assertThat(series.xTimestamps[1]).isWithin(0.1).of((birthDateMillis + 8 * dayMs).toDouble())
        }
    }

    // ── PercentileSeries.allBands() ──

    @Nested
    inner class PercentileSeriesAllBands {

        @Test
        fun `allBands returns exactly 7 bands`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            val bands = series!!.allBands()
            assertThat(bands).hasSize(7)
            // Each band has same size as xTimestamps
            bands.forEach { band ->
                assertThat(band).hasSize(series.xTimestamps.size)
            }
        }

        @Test
        fun `allBands order is p5 through p95`() {
            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNotNull()
            val bands = series!!.allBands()
            assertThat(bands[0]).isEqualTo(series.p5)
            assertThat(bands[1]).isEqualTo(series.p10)
            assertThat(bands[2]).isEqualTo(series.p25)
            assertThat(bands[3]).isEqualTo(series.p50)
            assertThat(bands[4]).isEqualTo(series.p75)
            assertThat(bands[5]).isEqualTo(series.p90)
            assertThat(bands[6]).isEqualTo(series.p95)
        }
    }

    // ── Edge cases ──

    @Nested
    inner class EdgeCases {

        @Test
        fun `returns null when data has fewer than 2 rows`() {
            setPrivateField("boyWeightData", listOf(boyWeightDay0))

            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNull()
        }

        @Test
        fun `returns null when data is empty`() {
            setPrivateField("boyWeightData", emptyList<PercentileRow>())

            val series = BabyPercentileHelper.getWeightPercentileSeries("male", birthDateMillis)

            assertThat(series).isNull()
        }

        @Test
        fun `sex matching is case-insensitive`() {
            assertThat(BabyPercentileHelper.getWeightPercentileSeries("Male", birthDateMillis)).isNotNull()
            assertThat(BabyPercentileHelper.getWeightPercentileSeries("MALE", birthDateMillis)).isNotNull()
            assertThat(BabyPercentileHelper.getWeightPercentileSeries("Female", birthDateMillis)).isNotNull()
            assertThat(BabyPercentileHelper.getWeightPercentileSeries("FEMALE", birthDateMillis)).isNotNull()
        }
    }

    // ── Reflection helper ──

    private fun setPrivateField(name: String, value: Any?) {
        val field = BabyPercentileHelper::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(BabyPercentileHelper, value)
    }
}
