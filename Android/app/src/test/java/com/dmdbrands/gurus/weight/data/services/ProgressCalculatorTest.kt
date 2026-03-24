package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Unit tests for [calculateProgressPure].
 *
 * This is a pure function — all inputs are pre-computed by the caller.
 * Tests verify arithmetic and branching logic without DB or coroutines.
 */
class ProgressCalculatorTest {

    companion object {
        private const val ACCOUNT_ID = "test-account"
        private const val LATEST_WEIGHT = 180.0
        private const val INIT_WEEK_WEIGHT = 185.0
        private const val INIT_MONTH_WEIGHT = 190.0
        private const val STARTING_WEIGHT = 200.0
        private const val FIRST_RECORDED_WEIGHT = 195.0
        private const val CURRENT_STREAK = 5
        private const val LONGEST_STREAK = 10
        private const val TOTAL_COUNT = 42
        private const val AVG_WEIGHT_YEAR = 192.0
    }

    private val fakeGoal: Goal = mockk(relaxed = true)

    private fun createScaleEntry(weight: Double, id: Long = 0L): ScaleEntry {
        val entryEntity = EntryEntity(
            id = id,
            accountId = ACCOUNT_ID,
            entryTimestamp = "2026-03-24T10:00:00Z",
            operationType = "create",
            deviceType = "scale",
            deviceId = "device-1",
            unit = WeightUnit.LB,
        )
        val bodyScaleEntry = BodyScaleEntryEntity(
            id = id,
            weight = weight,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = null,
            source = "scale",
        )
        val scaleEntryWithMetrics = ScaleEntryWithMetrics(
            scaleEntry = bodyScaleEntry,
            scaleEntryMetric = null,
        )
        return ScaleEntry(entry = entryEntity, scale = scaleEntryWithMetrics)
    }

    /**
     * Creates a month timestamp string N months ago from now in "MMM yyyy" format.
     */
    private fun monthsAgo(months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -months)
        return SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    @BeforeEach
    fun setUp() {
        // No shared mutable state to reset for a pure function
    }

    // -------------------------------------------------------------------------
    // Null / empty inputs — all deltas should be null
    // -------------------------------------------------------------------------

    @Test
    fun `null latestEntry produces null deltas`() {
        val result = calculateProgressPure(
            latestEntry = null,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = 0,
            longestStreak = 0,
            totalCount = 0,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.latest).isNull()
        assertThat(result.week).isNull()
        assertThat(result.month).isNull()
        assertThat(result.year).isNull()
        assertThat(result.total).isNull()
        assertThat(result.initWt).isEqualTo(0.0)
    }

    @Test
    fun `empty lists with valid latestEntry produce null week and month deltas`() {
        val latest = createScaleEntry(LATEST_WEIGHT)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.week).isNull()
        assertThat(result.month).isNull()
        assertThat(result.year).isNull()
        assertThat(result.total).isNull()
    }

    // -------------------------------------------------------------------------
    // Week delta
    // -------------------------------------------------------------------------

    @Test
    fun `week delta is calculated from latest minus oldest in last7Days`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val initWeek = createScaleEntry(INIT_WEEK_WEIGHT, id = 2L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = listOf(latest, initWeek), // last() is initWeek
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.week).isEqualTo(LATEST_WEIGHT - INIT_WEEK_WEIGHT)
        assertThat(result.initWeek).isEqualTo(initWeek)
    }

    // -------------------------------------------------------------------------
    // Month delta
    // -------------------------------------------------------------------------

    @Test
    fun `month delta is calculated from latest minus oldest in last30Days`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val initMonth = createScaleEntry(INIT_MONTH_WEIGHT, id = 3L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = listOf(latest, initMonth), // last() is initMonth
            months = emptyList(),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.month).isEqualTo(LATEST_WEIGHT - INIT_MONTH_WEIGHT)
        assertThat(result.initMonth).isEqualTo(initMonth)
    }

    // -------------------------------------------------------------------------
    // Total delta
    // -------------------------------------------------------------------------

    @Test
    fun `total uses firstRecordedWeightDisplay when available`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = STARTING_WEIGHT,
            firstRecordedWeightDisplay = FIRST_RECORDED_WEIGHT,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        // firstRecordedWeightDisplay takes priority over startingWeightDisplay
        assertThat(result.total).isEqualTo(LATEST_WEIGHT - FIRST_RECORDED_WEIGHT)
        assertThat(result.initWt).isEqualTo(FIRST_RECORDED_WEIGHT)
    }

    @Test
    fun `total falls back to startingWeightDisplay when firstRecordedWeight is null`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = STARTING_WEIGHT,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.total).isEqualTo(LATEST_WEIGHT - STARTING_WEIGHT)
        assertThat(result.initWt).isEqualTo(STARTING_WEIGHT)
    }

    @Test
    fun `total is null when both baseline weights are null`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.total).isNull()
        assertThat(result.initWt).isEqualTo(0.0)
    }

    // -------------------------------------------------------------------------
    // Year delta — initYear within 30 days (uses month delta logic)
    // -------------------------------------------------------------------------

    @Test
    fun `year uses month delta when initYear date is within 30 days`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val initMonth = createScaleEntry(INIT_MONTH_WEIGHT, id = 3L)
        // Use current month — within 30 days
        val recentMonth = HistoryMonth(
            entryTimestamp = monthsAgo(0),
            avgWeight = AVG_WEIGHT_YEAR,
            entryCount = 5,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = listOf(latest, initMonth), // last() is initMonth
            months = listOf(recentMonth),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        // Year falls back to month delta when initYear is within 30 days
        assertThat(result.year).isEqualTo(LATEST_WEIGHT - INIT_MONTH_WEIGHT)
    }

    // -------------------------------------------------------------------------
    // Year delta — initYear older than 30 days (uses avgWeight)
    // -------------------------------------------------------------------------

    @Test
    fun `year uses avgWeight when initYear date is older than 30 days`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        // Use a month well in the past — older than 30 days
        val oldMonth = HistoryMonth(
            entryTimestamp = monthsAgo(6),
            avgWeight = AVG_WEIGHT_YEAR,
            entryCount = 10,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = listOf(oldMonth),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.year).isEqualTo(LATEST_WEIGHT - AVG_WEIGHT_YEAR)
    }

    @Test
    fun `year is null when initYear avgWeight is null and date is older than 30 days`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val oldMonth = HistoryMonth(
            entryTimestamp = monthsAgo(6),
            avgWeight = null,
            entryCount = 10,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = listOf(oldMonth),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.year).isNull()
    }

    // -------------------------------------------------------------------------
    // Year delta — null entryTimestamp on initYear
    // -------------------------------------------------------------------------

    @Test
    fun `year is null when initYear entryTimestamp is null`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val monthWithNullTimestamp = HistoryMonth(
            entryTimestamp = null,
            avgWeight = AVG_WEIGHT_YEAR,
            entryCount = 5,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = listOf(monthWithNullTimestamp),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.year).isNull()
    }

    // -------------------------------------------------------------------------
    // Year delta — unparseable entryTimestamp (catch block uses avgWeight)
    // -------------------------------------------------------------------------

    @Test
    fun `year uses avgWeight fallback when entryTimestamp is unparseable`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val badMonth = HistoryMonth(
            entryTimestamp = "not-a-date",
            avgWeight = AVG_WEIGHT_YEAR,
            entryCount = 5,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = listOf(badMonth),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        // Falls through to catch block which uses avgWeight
        assertThat(result.year).isEqualTo(LATEST_WEIGHT - AVG_WEIGHT_YEAR)
    }

    @Test
    fun `year is null when entryTimestamp is unparseable and avgWeight is null`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val badMonth = HistoryMonth(
            entryTimestamp = "not-a-date",
            avgWeight = null,
            entryCount = 5,
        )

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = listOf(badMonth),
            startingWeightDisplay = null,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.year).isNull()
    }

    // -------------------------------------------------------------------------
    // Passthrough fields
    // -------------------------------------------------------------------------

    @Test
    fun `passthrough fields are set correctly in result`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = STARTING_WEIGHT,
            firstRecordedWeightDisplay = FIRST_RECORDED_WEIGHT,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.KG,
            goal = fakeGoal,
        )

        assertThat(result.latest).isEqualTo(latest)
        assertThat(result.currentStreak).isEqualTo(CURRENT_STREAK)
        assertThat(result.longestStreak).isEqualTo(LONGEST_STREAK)
        assertThat(result.count).isEqualTo(TOTAL_COUNT)
        assertThat(result.unit).isEqualTo(WeightUnit.KG)
        assertThat(result.goal).isEqualTo(fakeGoal)
        assertThat(result.initWt).isEqualTo(FIRST_RECORDED_WEIGHT)
    }

    // -------------------------------------------------------------------------
    // Combined week + month + total deltas
    // -------------------------------------------------------------------------

    @Test
    fun `week, month, and total deltas all calculated correctly together`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)
        val initWeek = createScaleEntry(INIT_WEEK_WEIGHT, id = 2L)
        val initMonth = createScaleEntry(INIT_MONTH_WEIGHT, id = 3L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = listOf(latest, initWeek),
            last30Days = listOf(latest, initMonth),
            months = emptyList(),
            startingWeightDisplay = STARTING_WEIGHT,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = fakeGoal,
        )

        assertThat(result.week).isEqualTo(LATEST_WEIGHT - INIT_WEEK_WEIGHT)
        assertThat(result.month).isEqualTo(LATEST_WEIGHT - INIT_MONTH_WEIGHT)
        assertThat(result.total).isEqualTo(LATEST_WEIGHT - STARTING_WEIGHT)
    }

    // -------------------------------------------------------------------------
    // Empty months list
    // -------------------------------------------------------------------------

    @Test
    fun `empty months list produces null year and initYear`() {
        val latest = createScaleEntry(LATEST_WEIGHT, id = 1L)

        val result = calculateProgressPure(
            latestEntry = latest,
            last7Days = emptyList(),
            last30Days = emptyList(),
            months = emptyList(),
            startingWeightDisplay = STARTING_WEIGHT,
            firstRecordedWeightDisplay = null,
            currentStreak = CURRENT_STREAK,
            longestStreak = LONGEST_STREAK,
            totalCount = TOTAL_COUNT,
            unit = WeightUnit.LB,
            goal = null,
        )

        assertThat(result.year).isNull()
        assertThat(result.initYear).isNull()
    }
}
