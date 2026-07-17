package com.dmdbrands.gurus.weight.data.repository

import android.content.Context
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryReadDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.BabyDailySummaryResult
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToDisplay
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.repository.IEntryReadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Single implementation of [IEntryReadRepository].
 * Delegates all queries to [EntryReadDao] and converts Room types to domain types.
 */
class EntryReadRepository @Inject constructor(
    private val entryReadDao: EntryReadDao,
    @ApplicationContext private val context: Context,
) : IEntryReadRepository {

    companion object {
        /** Flip to true to return sample data instead of querying the database. */
        var USE_SAMPLE_DATA = false
        private const val SAMPLE_BABY_ID = "sample-1"
    }

    // ---------------------------------------------------------------------------
    // Weight
    // ---------------------------------------------------------------------------

    override fun getWeightMonthlyHistory(accountId: String): Flow<List<HistoryMonth>> =
        entryReadDao.getWeightMonthlyHistory(accountId)
            .map { months -> months.map { it.convertToDisplay() } }

    override fun getWeightMonthDetail(accountId: String, month: String): Flow<List<ScaleEntry>> =
        entryReadDao.getWeightMonthDetail(accountId, month).map { entries ->
            entries.mapNotNull { it.toEntry() }.filterIsInstance<ScaleEntry>()
        }

    // ---------------------------------------------------------------------------
    // BPM
    // ---------------------------------------------------------------------------

    override fun getBpmMonthlyHistory(accountId: String): Flow<List<BpHistoryMonth>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpMonthlyHistory())
        return entryReadDao.getBpmMonthlyHistory(accountId)
    }

    override fun getBpmMonthDetail(accountId: String, month: String): Flow<List<BpmEntry>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmEntries())
        return entryReadDao.getBpmMonthDetail(accountId, month).map { entries ->
            entries.mapNotNull { it.toEntry() }.filterIsInstance<BpmEntry>()
        }
    }

    // ---------------------------------------------------------------------------
    // Baby
    // ---------------------------------------------------------------------------

    override fun getBabyWeeklyHistory(
        accountId: String,
        babyId: String,
        sex: String?,
        birthDateMillis: Long,
    ): Flow<List<BabyWeekGroup>> {
        if (USE_SAMPLE_DATA) return flowOf(groupByWeek(sampleBabyDailySummaries(), sex, birthDateMillis))
        return entryReadDao.getBabyWeeklyHistory(accountId, babyId)
            .map { groupByWeek(it, sex, birthDateMillis) }
            .flowOn(Dispatchers.Default)
    }

    override fun getBabyDayDetail(
        accountId: String,
        babyId: String,
        date: String,
        sex: String?,
        birthDateMillis: Long,
    ): Flow<List<BabyEntry>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBabyEntries())
        return entryReadDao.getBabyDayDetail(accountId, babyId, date).map { entries ->
            entries.mapNotNull { it.toEntry() }
                .filterIsInstance<BabyEntry>()
                .map { it.copy(percentile = dayDetailPercentile(it, sex, birthDateMillis)) }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Per-entry percentile for the day-detail list. Each baby entry is a single type
     * ([BabyEntryType]); compute the percentile for that exact measurement — matching
     * babyApp's per-entry behaviour. Falls back to weight-then-length when the type is
     * unset. Returns null when sex/birthdate are unknown (handled inside the helper).
     */
    private fun dayDetailPercentile(entry: BabyEntry, sex: String?, birthDateMillis: Long): Int? {
        if (birthDateMillis <= 0L) return null
        BabyPercentileHelper.loadIfNeeded(context)
        val ageMillis = DateTimeConverter.isoToTimestamp(entry.entry.entryTimestamp)
        val weight = entry.babyWeightDecigrams
        val length = entry.babyLengthMillimeters
        val type = when (entry.entryType) {
            BabyEntryType.MEASURE_LENGTH.value -> BabyPercentileHelper.MeasurementType.LENGTH
            BabyEntryType.WEIGHT.value -> BabyPercentileHelper.MeasurementType.WEIGHT
            else -> if (weight != null) BabyPercentileHelper.MeasurementType.WEIGHT else BabyPercentileHelper.MeasurementType.LENGTH
        }
        val value = when (type) {
            BabyPercentileHelper.MeasurementType.WEIGHT -> weight
            BabyPercentileHelper.MeasurementType.LENGTH -> length
        } ?: return null
        return BabyPercentileHelper.calcPercentile(sex, birthDateMillis, value.toDouble(), type, ageMillis)
    }

    // ---------------------------------------------------------------------------
    // Weight Graph
    // ---------------------------------------------------------------------------

    override fun getWeightMonthlyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryReadDao.getWeightMonthlyGraphData(accountId)
            .map { list -> list.map { it.convertToDisplay() } }

    override fun getWeightDailyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryReadDao.getWeightDailyGraphData(accountId)
            .map { list -> list.map { it.convertToDisplay() } }

    // ---------------------------------------------------------------------------
    // Weight Snapshot (Dashboard mini-chart)
    // ---------------------------------------------------------------------------

    override fun getWeightSnapshotGraphData(accountId: String): Flow<List<WeightSnapshotPoint>> =
        entryReadDao.getWeightSnapshotGraphData(accountId)

    // ---------------------------------------------------------------------------
    // BPM Graph
    // ---------------------------------------------------------------------------

    override fun getBpmMonthlyGraphData(accountId: String): Flow<List<PeriodBpmSummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmMonthlyGraphData())
        return entryReadDao.getBpmMonthlyGraphData(accountId)
    }

    override fun getBpmDailyGraphData(accountId: String): Flow<List<PeriodBpmSummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmDailyGraphData())
        return entryReadDao.getBpmDailyGraphData(accountId)
    }

    // ---------------------------------------------------------------------------
    // BPM Snapshot (Dashboard mini-chart)
    // ---------------------------------------------------------------------------

    override fun getBpmSnapshotGraphData(accountId: String): Flow<List<PeriodBpmSummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmSnapshotData())
        return entryReadDao.getBpmSnapshotGraphData(accountId)
    }

    override fun getBpmLastNDayEntries(accountId: String, n: Int): Flow<List<PeriodBpmSummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmSnapshotData().takeLast(n).reversed())
        return entryReadDao.getBpmLastNDayEntries(accountId, n)
    }

    private fun sampleBpmSnapshotData(): List<PeriodBpmSummary> = listOf(
            PeriodBpmSummary("2026-03-30", "2026-03-30 00:00:00", avgSystolic = 118, avgDiastolic = 76, avgPulse = 68),
            PeriodBpmSummary("2026-03-31", "2026-03-31 00:00:00", avgSystolic = 115, avgDiastolic = 74, avgPulse = 65),
            PeriodBpmSummary("2026-04-01", "2026-04-01 00:00:00", avgSystolic = 120, avgDiastolic = 78, avgPulse = 70),
            PeriodBpmSummary("2026-04-02", "2026-04-02 00:00:00", avgSystolic = 112, avgDiastolic = 72, avgPulse = 62),
            PeriodBpmSummary("2026-04-03", "2026-04-03 00:00:00", avgSystolic = 116, avgDiastolic = 75, avgPulse = 66),
            PeriodBpmSummary("2026-04-04", "2026-04-04 00:00:00", avgSystolic = 119, avgDiastolic = 77, avgPulse = 69),
            PeriodBpmSummary("2026-04-05", "2026-04-05 00:00:00", avgSystolic = 114, avgDiastolic = 73, avgPulse = 64),
            PeriodBpmSummary("2026-04-06", "2026-04-06 00:00:00", avgSystolic = 111, avgDiastolic = 71, avgPulse = 63),
    )

    /** Sample BP daily data — with month-scale gaps for fallback testing. */
    private fun sampleBpmDailyGraphData(): List<PeriodBpmSummary> = listOf(
        // Cluster 1: Aug 2025 (early readings, higher values)
        PeriodBpmSummary("2025-08-01", "2025-08-01 00:00:00", avgSystolic = 138, avgDiastolic = 90, avgPulse = 82),
        PeriodBpmSummary("2025-08-03", "2025-08-03 00:00:00", avgSystolic = 135, avgDiastolic = 88, avgPulse = 80),
        PeriodBpmSummary("2025-08-05", "2025-08-05 00:00:00", avgSystolic = 136, avgDiastolic = 89, avgPulse = 81),
        PeriodBpmSummary("2025-08-08", "2025-08-08 00:00:00", avgSystolic = 133, avgDiastolic = 87, avgPulse = 79),
        PeriodBpmSummary("2025-08-10", "2025-08-10 00:00:00", avgSystolic = 134, avgDiastolic = 88, avgPulse = 80),
        // ── GAP: ~2 months (Aug 11 – Oct 14 empty) ──
        // Cluster 2: Oct 2025
        PeriodBpmSummary("2025-10-15", "2025-10-15 00:00:00", avgSystolic = 130, avgDiastolic = 85, avgPulse = 77),
        PeriodBpmSummary("2025-10-17", "2025-10-17 00:00:00", avgSystolic = 128, avgDiastolic = 84, avgPulse = 76),
        PeriodBpmSummary("2025-10-20", "2025-10-20 00:00:00", avgSystolic = 126, avgDiastolic = 82, avgPulse = 74),
        PeriodBpmSummary("2025-10-22", "2025-10-22 00:00:00", avgSystolic = 127, avgDiastolic = 83, avgPulse = 75),
        // ── GAP: ~1 month (Oct 23 – Nov 24 empty) ──
        // Cluster 3: Late Nov – early Dec 2025
        PeriodBpmSummary("2025-11-25", "2025-11-25 00:00:00", avgSystolic = 124, avgDiastolic = 81, avgPulse = 73),
        PeriodBpmSummary("2025-11-27", "2025-11-27 00:00:00", avgSystolic = 122, avgDiastolic = 80, avgPulse = 72),
        PeriodBpmSummary("2025-11-30", "2025-11-30 00:00:00", avgSystolic = 123, avgDiastolic = 80, avgPulse = 72),
        PeriodBpmSummary("2025-12-02", "2025-12-02 00:00:00", avgSystolic = 121, avgDiastolic = 79, avgPulse = 71),
        PeriodBpmSummary("2025-12-05", "2025-12-05 00:00:00", avgSystolic = 120, avgDiastolic = 78, avgPulse = 70),
        // ── GAP: ~2 months (Dec 6 – Jan 31 empty) ──
        // Cluster 4: Feb 2026
        PeriodBpmSummary("2026-02-01", "2026-02-01 00:00:00", avgSystolic = 119, avgDiastolic = 78, avgPulse = 70),
        PeriodBpmSummary("2026-02-04", "2026-02-04 00:00:00", avgSystolic = 118, avgDiastolic = 77, avgPulse = 69),
        PeriodBpmSummary("2026-02-07", "2026-02-07 00:00:00", avgSystolic = 116, avgDiastolic = 76, avgPulse = 68),
        PeriodBpmSummary("2026-02-10", "2026-02-10 00:00:00", avgSystolic = 117, avgDiastolic = 76, avgPulse = 68),
        PeriodBpmSummary("2026-02-14", "2026-02-14 00:00:00", avgSystolic = 115, avgDiastolic = 75, avgPulse = 67),
        // ── GAP: ~1 month (Feb 15 – Mar 19 empty) ──
        // Cluster 5: Late Mar – Apr 2026 (dense, daily — recent)
        PeriodBpmSummary("2026-03-20", "2026-03-20 00:00:00", avgSystolic = 118, avgDiastolic = 76, avgPulse = 68),
        PeriodBpmSummary("2026-03-22", "2026-03-22 00:00:00", avgSystolic = 116, avgDiastolic = 75, avgPulse = 67),
        PeriodBpmSummary("2026-03-24", "2026-03-24 00:00:00", avgSystolic = 114, avgDiastolic = 74, avgPulse = 66),
        PeriodBpmSummary("2026-03-27", "2026-03-27 00:00:00", avgSystolic = 115, avgDiastolic = 75, avgPulse = 67),
        PeriodBpmSummary("2026-03-30", "2026-03-30 00:00:00", avgSystolic = 113, avgDiastolic = 73, avgPulse = 65),
        PeriodBpmSummary("2026-04-01", "2026-04-01 00:00:00", avgSystolic = 120, avgDiastolic = 78, avgPulse = 70),
        PeriodBpmSummary("2026-04-02", "2026-04-02 00:00:00", avgSystolic = 112, avgDiastolic = 72, avgPulse = 62),
        PeriodBpmSummary("2026-04-03", "2026-04-03 00:00:00", avgSystolic = 116, avgDiastolic = 75, avgPulse = 66),
        PeriodBpmSummary("2026-04-04", "2026-04-04 00:00:00", avgSystolic = 119, avgDiastolic = 77, avgPulse = 69),
        PeriodBpmSummary("2026-04-05", "2026-04-05 00:00:00", avgSystolic = 114, avgDiastolic = 73, avgPulse = 64),
        PeriodBpmSummary("2026-04-06", "2026-04-06 00:00:00", avgSystolic = 111, avgDiastolic = 71, avgPulse = 63),
        PeriodBpmSummary("2026-04-08", "2026-04-08 00:00:00", avgSystolic = 113, avgDiastolic = 73, avgPulse = 65),
    )

    /** Sample BP monthly data — with year-scale gaps for fallback testing. */
    private fun sampleBpmMonthlyGraphData(): List<PeriodBpmSummary> = listOf(
        // Cluster 1: Early 2023 (high readings)
        PeriodBpmSummary("2023-01", "2023-01-01 00:00:00", avgSystolic = 142, avgDiastolic = 92, avgPulse = 84),
        PeriodBpmSummary("2023-02", "2023-02-01 00:00:00", avgSystolic = 140, avgDiastolic = 91, avgPulse = 83),
        PeriodBpmSummary("2023-03", "2023-03-01 00:00:00", avgSystolic = 138, avgDiastolic = 90, avgPulse = 82),
        PeriodBpmSummary("2023-04", "2023-04-01 00:00:00", avgSystolic = 137, avgDiastolic = 89, avgPulse = 81),
        // ── GAP: ~1 year (May 2023 – Mar 2024 empty) ──
        // Cluster 2: Spring 2024
        PeriodBpmSummary("2024-04", "2024-04-01 00:00:00", avgSystolic = 132, avgDiastolic = 86, avgPulse = 78),
        PeriodBpmSummary("2024-05", "2024-05-01 00:00:00", avgSystolic = 130, avgDiastolic = 85, avgPulse = 77),
        PeriodBpmSummary("2024-06", "2024-06-01 00:00:00", avgSystolic = 128, avgDiastolic = 84, avgPulse = 76),
        // ── GAP: ~8 months (Jul 2024 – Feb 2025 empty) ──
        // Cluster 3: Spring 2025
        PeriodBpmSummary("2025-03", "2025-03-01 00:00:00", avgSystolic = 126, avgDiastolic = 82, avgPulse = 74),
        PeriodBpmSummary("2025-04", "2025-04-01 00:00:00", avgSystolic = 124, avgDiastolic = 81, avgPulse = 73),
        PeriodBpmSummary("2025-05", "2025-05-01 00:00:00", avgSystolic = 122, avgDiastolic = 80, avgPulse = 72),
        // ── GAP: ~6 months (Jun – Nov 2025 empty) ──
        // Cluster 4: Late 2025 – current
        PeriodBpmSummary("2025-12", "2025-12-01 00:00:00", avgSystolic = 118, avgDiastolic = 76, avgPulse = 68),
        PeriodBpmSummary("2026-01", "2026-01-01 00:00:00", avgSystolic = 116, avgDiastolic = 75, avgPulse = 66),
        PeriodBpmSummary("2026-02", "2026-02-01 00:00:00", avgSystolic = 115, avgDiastolic = 75, avgPulse = 67),
        PeriodBpmSummary("2026-03", "2026-03-01 00:00:00", avgSystolic = 114, avgDiastolic = 73, avgPulse = 64),
        PeriodBpmSummary("2026-04", "2026-04-01 00:00:00", avgSystolic = 113, avgDiastolic = 73, avgPulse = 65),
    )

    // ---------------------------------------------------------------------------
    // Baby Graph
    // ---------------------------------------------------------------------------

    override fun getBabyMonthlyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBabyMonthlyGraphData())
        return entryReadDao.getBabyMonthlyGraphData(accountId, babyId)
    }

    override fun getBabyDailyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBabyDailyGraphData())
        return entryReadDao.getBabyDailyGraphData(accountId, babyId)
    }

    // ---------------------------------------------------------------------------
    // Baby Snapshot — all babies in one query (Dashboard mini-chart)
    // ---------------------------------------------------------------------------

    override fun getAllBabySnapshotGraphData(accountId: String): Flow<List<PeriodBabySummary>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBabySnapshotData(SAMPLE_BABY_ID))
        return entryReadDao.getAllBabySnapshotGraphData(accountId)
    }

    // ---------------------------------------------------------------------------
    // Cross-product read queries (moved from EntryRepository)
    // ---------------------------------------------------------------------------

    override fun getLatestEntry(accountId: String): Flow<Entry?> =
        entryReadDao.getLatestEntry(accountId).map { it?.toEntry() }

    override fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<Entry>> {
        val startInstant = java.time.Instant.now().minus(java.time.Duration.ofDays(days.toLong()))
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val startDate = java.time.ZonedDateTime.ofInstant(startInstant, java.time.ZoneOffset.UTC).format(formatter)
        return entryReadDao.getEntriesSince(accountId, startDate).map { list ->
            list.mapNotNull { it.toEntry() }
        }
    }

    override fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<Entry>> =
        entryReadDao.getEntriesByOperationType(accountId, operationType).map { flow ->
            flow.mapNotNull { it.toEntry() }
        }

    override fun getMonthlyHistoryLastYear(accountId: String): Flow<List<HistoryMonth>> =
        entryReadDao.getMonthlyHistoryLastYear(accountId).map { list -> list.map { it.convertToDisplay() } }

    override suspend fun getOldestEntry(accountId: String): Entry? =
        entryReadDao.getOldestEntry(accountId)?.toEntry()

    override suspend fun getStreakData(accountId: String): List<String> =
        entryReadDao.getStreakData(accountId)

    override suspend fun getLongestStreakCount(accountId: String): Int =
        entryReadDao.getLongestStreakCount(accountId)

    override suspend fun getTotalCount(accountId: String): Int =
        entryReadDao.getTotalCount(accountId)

    override fun getBpmStreakDays(accountId: String): Flow<List<String>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmStreakDays())
        return entryReadDao.getBpmStreakDays(accountId)
    }

    /** Sample BP streak days (newest first) — matches sampleBpmDailyGraphData dates.
     *  Clusters with gaps produce: current streak = 5 (Apr 1–8), longest streak = 5. */
    private fun sampleBpmStreakDays(): List<String> = listOf(
        "2026-04-08", "2026-04-06", "2026-04-05", "2026-04-04", "2026-04-03",
        "2026-04-02", "2026-04-01", "2026-03-30", "2026-03-27", "2026-03-24",
        "2026-03-22", "2026-03-20", "2026-02-14", "2026-02-10", "2026-02-07",
        "2026-02-04", "2026-02-01", "2025-12-05", "2025-12-02", "2025-11-30",
        "2025-11-27", "2025-11-25", "2025-10-22", "2025-10-20", "2025-10-17",
        "2025-10-15", "2025-08-10", "2025-08-08", "2025-08-05", "2025-08-03",
        "2025-08-01",
    )

    /**
     * Sample Baby daily data — born Jan 1 2026, starts day 10.
     * WHO p50 boy: birth ~3.3 kg (33000 dg), ~50 cm (500 mm).
     */
    private fun sampleBabyDailyGraphData(): List<PeriodBabySummary> = listOf(
        PeriodBabySummary("2026-01-11", "2026-01-11 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 33000, avgLengthMillimeters = 500),
        PeriodBabySummary("2026-01-14", "2026-01-14 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 33500, avgLengthMillimeters = 502),
        PeriodBabySummary("2026-01-18", "2026-01-18 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 34200, avgLengthMillimeters = 505),
        PeriodBabySummary("2026-01-22", "2026-01-22 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 35000, avgLengthMillimeters = 508),
        PeriodBabySummary("2026-01-26", "2026-01-26 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 36000, avgLengthMillimeters = 511),
        PeriodBabySummary("2026-01-30", "2026-01-30 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 37000, avgLengthMillimeters = 514),
        PeriodBabySummary("2026-02-03", "2026-02-03 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 38200, avgLengthMillimeters = 518),
        PeriodBabySummary("2026-02-07", "2026-02-07 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 39500, avgLengthMillimeters = 522),
        PeriodBabySummary("2026-02-11", "2026-02-11 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 40800, avgLengthMillimeters = 526),
        PeriodBabySummary("2026-02-15", "2026-02-15 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 42000, avgLengthMillimeters = 530),
        PeriodBabySummary("2026-02-20", "2026-02-20 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 43500, avgLengthMillimeters = 535),
        PeriodBabySummary("2026-02-25", "2026-02-25 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 45000, avgLengthMillimeters = 540),
        PeriodBabySummary("2026-03-02", "2026-03-02 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 46500, avgLengthMillimeters = 545),
        PeriodBabySummary("2026-03-07", "2026-03-07 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 48000, avgLengthMillimeters = 550),
        PeriodBabySummary("2026-03-12", "2026-03-12 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 49500, avgLengthMillimeters = 555),
        PeriodBabySummary("2026-03-17", "2026-03-17 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 51000, avgLengthMillimeters = 560),
        PeriodBabySummary("2026-03-22", "2026-03-22 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 52500, avgLengthMillimeters = 565),
        PeriodBabySummary("2026-03-27", "2026-03-27 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 54000, avgLengthMillimeters = 570),
        PeriodBabySummary("2026-04-01", "2026-04-01 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 55500, avgLengthMillimeters = 575),
        PeriodBabySummary("2026-04-05", "2026-04-05 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 57000, avgLengthMillimeters = 580),
        PeriodBabySummary("2026-04-08", "2026-04-08 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 58000, avgLengthMillimeters = 583),
    )

    /** Sample Baby monthly data — born Jan 1 2026, starts month 1. */
    private fun sampleBabyMonthlyGraphData(): List<PeriodBabySummary> = listOf(
        PeriodBabySummary("2026-01", "2026-01-01 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 33000, avgLengthMillimeters = 500),
        PeriodBabySummary("2026-02", "2026-02-01 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 42000, avgLengthMillimeters = 530),
        PeriodBabySummary("2026-03", "2026-03-01 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 50000, avgLengthMillimeters = 555),
        PeriodBabySummary("2026-04", "2026-04-01 00:00:00", SAMPLE_BABY_ID, avgWeightDecigrams = 58000, avgLengthMillimeters = 580),
    )

    /** Realistic sample: ~90-day-old baby around p50 (~60000 decigrams = 13.2 lbs) */
    private fun sampleBabySnapshotData(babyId: String = SAMPLE_BABY_ID): List<PeriodBabySummary> = listOf(
        PeriodBabySummary("2026-03-30", "2026-03-30 00:00:00", babyId, avgWeightDecigrams = 58200, avgLengthMillimeters = 580),
        PeriodBabySummary("2026-03-31", "2026-03-31 00:00:00", babyId, avgWeightDecigrams = 58500, avgLengthMillimeters = 581),
        PeriodBabySummary("2026-04-01", "2026-04-01 00:00:00", babyId, avgWeightDecigrams = 58800, avgLengthMillimeters = 582),
        PeriodBabySummary("2026-04-02", "2026-04-02 00:00:00", babyId, avgWeightDecigrams = 59200, avgLengthMillimeters = 583),
        PeriodBabySummary("2026-04-03", "2026-04-03 00:00:00", babyId, avgWeightDecigrams = 59500, avgLengthMillimeters = 584),
        PeriodBabySummary("2026-04-04", "2026-04-04 00:00:00", babyId, avgWeightDecigrams = 59900, avgLengthMillimeters = 585),
        PeriodBabySummary("2026-04-05", "2026-04-05 00:00:00", babyId, avgWeightDecigrams = 60200, avgLengthMillimeters = 587),
        PeriodBabySummary("2026-04-06", "2026-04-06 00:00:00", babyId, avgWeightDecigrams = 60500, avgLengthMillimeters = 588),
    )

    // ---------------------------------------------------------------------------
    // Baby weekly grouping (converts BabyDailySummaryResult → BabyWeekGroup)
    // ---------------------------------------------------------------------------

    private fun groupByWeek(
        dailySummaries: List<BabyDailySummaryResult>,
        sex: String?,
        birthDateMillis: Long,
    ): List<BabyWeekGroup> {
        if (birthDateMillis > 0L && dailySummaries.isNotEmpty()) BabyPercentileHelper.loadIfNeeded(context)
        return dailySummaries
            .groupBy { "${it.year}-${it.weekNumber}" }
            .map { (_, days) ->
                BabyWeekGroup(
                    weekLabel = "week ${days.first().weekNumber}",
                    entries = days.map { day ->
                        BabyWeekHistory(
                            date = day.date,
                            dateKey = day.dateKey,
                            entryCount = day.entryCount,
                            weightDecigrams = day.babyWeightDecigrams,
                            lengthMillimeters = day.babyLengthMillimeters,
                            percentile = weekRowPercentile(day, sex, birthDateMillis),
                        )
                    },
                )
            }
    }

    /**
     * Percentile for an aggregated day row. A day can hold both a weight and a length
     * reading; the row shows a single percentile, so we prefer weight (the primary baby
     * metric) and fall back to length. Returns null when sex/birthdate are unknown.
     */
    private fun weekRowPercentile(day: BabyDailySummaryResult, sex: String?, birthDateMillis: Long): Int? {
        if (birthDateMillis <= 0L) return null
        val ageMillis = DateTimeConverter.isoToTimestamp(day.dateKey)
        return when {
            day.babyWeightDecigrams != null -> BabyPercentileHelper.calcPercentile(
                sex, birthDateMillis, day.babyWeightDecigrams.toDouble(),
                BabyPercentileHelper.MeasurementType.WEIGHT, ageMillis,
            )
            day.babyLengthMillimeters != null -> BabyPercentileHelper.calcPercentile(
                sex, birthDateMillis, day.babyLengthMillimeters.toDouble(),
                BabyPercentileHelper.MeasurementType.LENGTH, ageMillis,
            )
            else -> null
        }
    }

    // ---------------------------------------------------------------------------
    // Sample data (matching DAO return types)
    // ---------------------------------------------------------------------------

    private fun sampleBpMonthlyHistory() = listOf(
        BpHistoryMonth("Dec 2025", avgSystolic = 115, avgDiastolic = 75, avgPulse = 60, entryCount = 5),
        BpHistoryMonth("Nov 2025", avgSystolic = 115, avgDiastolic = 75, avgPulse = 60, entryCount = 5),
        BpHistoryMonth("Oct 2025", avgSystolic = 115, avgDiastolic = 75, avgPulse = 60, entryCount = 5),
        BpHistoryMonth("Sep 2025", avgSystolic = 118, avgDiastolic = 76, avgPulse = 62, entryCount = 5),
        BpHistoryMonth("Aug 2025", avgSystolic = 117, avgDiastolic = 75, avgPulse = 61, entryCount = 4),
        BpHistoryMonth("Jul 2025", avgSystolic = 120, avgDiastolic = 78, avgPulse = 64, entryCount = 5),
        BpHistoryMonth("Jun 2025", avgSystolic = 116, avgDiastolic = 74, avgPulse = 63, entryCount = 5),
    )

    private fun sampleBpmEntries() = listOf(
        BpmEntry(
            entry = EntryEntity(id = 1001, accountId = "sample", entryTimestamp = "2025-08-07T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(id = 1001, systolic = 115, diastolic = 75, pulse = 60, meanArterial = "88", note = "This is where a note would go."),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1002, accountId = "sample", entryTimestamp = "2025-08-06T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(id = 1002, systolic = 115, diastolic = 75, pulse = 60, meanArterial = "88", note = "Another note here."),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1003, accountId = "sample", entryTimestamp = "2025-08-05T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(id = 1003, systolic = 118, diastolic = 76, pulse = 72, meanArterial = "90", note = null),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1004, accountId = "sample", entryTimestamp = "2025-08-04T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(id = 1004, systolic = 120, diastolic = 78, pulse = 75, meanArterial = "92", note = null),
        ),
    )

    private fun sampleBabyDailySummaries() = listOf(
        BabyDailySummaryResult("8/18/25", 3, 40360, 305, weekNumber = 33, year = 2025),
        BabyDailySummaryResult("8/17/25", 2, 40200, 304, weekNumber = 33, year = 2025),
        BabyDailySummaryResult("8/16/25", 5, 40100, 303, weekNumber = 33, year = 2025),
        BabyDailySummaryResult("8/15/25", 1, 39800, 302, weekNumber = 33, year = 2025),
        BabyDailySummaryResult("8/14/25", 2, 39600, 301, weekNumber = 33, year = 2025),
        BabyDailySummaryResult("8/11/25", 3, 39200, 299, weekNumber = 32, year = 2025),
        BabyDailySummaryResult("8/10/25", 2, 39000, 298, weekNumber = 32, year = 2025),
        BabyDailySummaryResult("8/9/25", 1, 38800, 297, weekNumber = 32, year = 2025),
        BabyDailySummaryResult("8/8/25", 4, 38600, 296, weekNumber = 32, year = 2025),
        BabyDailySummaryResult("8/7/25", 2, 38400, 295, weekNumber = 32, year = 2025),
    )

    private fun sampleBabyEntries() = listOf(
        BabyEntry(
            entry = EntryEntity(id = 2001, accountId = "sample", entryTimestamp = "2025-08-07T14:30:00.000Z", operationType = "create", deviceType = "baby", deviceId = "baby-1"),
            babyEntry = BabyEntryEntity(id = 2001, babyId = "sample-1", babyWeightDecigrams = 40360, babyLengthMillimeters = 305, entryNote = "This is where a note would go."),
        ),
        BabyEntry(
            entry = EntryEntity(id = 2002, accountId = "sample", entryTimestamp = "2025-08-07T09:52:00.000Z", operationType = "create", deviceType = "baby", deviceId = "baby-1"),
            babyEntry = BabyEntryEntity(id = 2002, babyId = "sample-1", babyWeightDecigrams = 40200, babyLengthMillimeters = 304, entryNote = "Another note here."),
        ),
        BabyEntry(
            entry = EntryEntity(id = 2003, accountId = "sample", entryTimestamp = "2025-08-06T11:00:00.000Z", operationType = "create", deviceType = "baby", deviceId = "baby-1"),
            babyEntry = BabyEntryEntity(id = 2003, babyId = "sample-1", babyWeightDecigrams = 40100, babyLengthMillimeters = 303, entryNote = null),
        ),
    )
}
