package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.data.storage.db.dao.HistoryDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.BabyDailySummaryResult
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.repository.IHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Single implementation of [IHistoryRepository].
 * Delegates all queries to [HistoryDao] and converts Room types to domain types.
 */
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
) : IHistoryRepository {

    companion object {
        /** Flip to true to return sample data instead of querying the database. */
        var USE_SAMPLE_DATA = true
    }

    // ---------------------------------------------------------------------------
    // Weight
    // ---------------------------------------------------------------------------

    override fun getWeightMonthlyHistory(accountId: String): Flow<List<HistoryMonth>> =
        historyDao.getWeightMonthlyHistory(accountId)

    override fun getWeightMonthDetail(accountId: String, month: String): Flow<List<ScaleEntry>> =
        historyDao.getWeightMonthDetail(accountId, month).map { entries ->
            entries.mapNotNull { it.toEntry() }.filterIsInstance<ScaleEntry>()
        }

    // ---------------------------------------------------------------------------
    // BPM
    // ---------------------------------------------------------------------------

    override fun getBpmMonthlyHistory(accountId: String): Flow<List<BpHistoryMonth>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpMonthlyHistory())
        return historyDao.getBpmMonthlyHistory(accountId)
    }

    override fun getBpmMonthDetail(accountId: String, month: String): Flow<List<BpmEntry>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBpmEntries())
        return historyDao.getBpmMonthDetail(accountId, month).map { entries ->
            entries.mapNotNull { it.toEntry() }.filterIsInstance<BpmEntry>()
        }
    }

    // ---------------------------------------------------------------------------
    // Baby
    // ---------------------------------------------------------------------------

    override fun getBabyWeeklyHistory(
        accountId: String,
        babyId: String,
    ): Flow<List<BabyWeekGroup>> {
        if (USE_SAMPLE_DATA) return flowOf(groupByWeek(sampleBabyDailySummaries()))
        return historyDao.getBabyWeeklyHistory(accountId, babyId).map { groupByWeek(it) }
    }

    override fun getBabyDayDetail(
        accountId: String,
        babyId: String,
        date: String,
    ): Flow<List<BabyEntry>> {
        if (USE_SAMPLE_DATA) return flowOf(sampleBabyEntries())
        return historyDao.getBabyDayDetail(accountId, babyId, date).map { entries ->
            entries.mapNotNull { it.toEntry() }.filterIsInstance<BabyEntry>()
        }
    }

    // ---------------------------------------------------------------------------
    // Weight Graph
    // ---------------------------------------------------------------------------

    override fun getWeightMonthlyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        historyDao.getWeightMonthlyGraphData(accountId)

    override fun getWeightDailyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        historyDao.getWeightDailyGraphData(accountId)

    // ---------------------------------------------------------------------------
    // Weight Snapshot (Dashboard mini-chart)
    // ---------------------------------------------------------------------------

    override fun getWeightSnapshotGraphData(accountId: String): Flow<List<WeightSnapshotPoint>> =
        historyDao.getWeightSnapshotGraphData(accountId)

    // ---------------------------------------------------------------------------
    // BPM Graph
    // ---------------------------------------------------------------------------

    override fun getBpmMonthlyGraphData(accountId: String): Flow<List<PeriodBpmSummary>> =
        historyDao.getBpmMonthlyGraphData(accountId)

    override fun getBpmDailyGraphData(accountId: String): Flow<List<PeriodBpmSummary>> =
        historyDao.getBpmDailyGraphData(accountId)

    // ---------------------------------------------------------------------------
    // BPM Snapshot (Dashboard mini-chart)
    // ---------------------------------------------------------------------------

    override fun getBpmSnapshotGraphData(accountId: String): Flow<List<PeriodBpmSummary>> =
        historyDao.getBpmSnapshotGraphData(accountId)

    // ---------------------------------------------------------------------------
    // Baby Graph
    // ---------------------------------------------------------------------------

    override fun getBabyMonthlyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>> =
        historyDao.getBabyMonthlyGraphData(accountId, babyId)

    override fun getBabyDailyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>> =
        historyDao.getBabyDailyGraphData(accountId, babyId)

    // ---------------------------------------------------------------------------
    // Baby weekly grouping (converts BabyDailySummaryResult → BabyWeekGroup)
    // ---------------------------------------------------------------------------

    private fun groupByWeek(dailySummaries: List<BabyDailySummaryResult>): List<BabyWeekGroup> =
        dailySummaries
            .groupBy { "${it.year}-${it.weekNumber}" }
            .map { (_, days) ->
                BabyWeekGroup(
                    weekLabel = "week ${days.first().weekNumber}",
                    entries = days.map { day ->
                        BabyWeekHistory(
                            date = day.date,
                            entryCount = day.entryCount,
                            weightLb = day.babyWeightDecigrams?.let { ConversionTools.convertDecigramsToLb(it) },
                            weightOz = day.babyWeightDecigrams?.let { ConversionTools.convertDecigramsToOz(it) },
                            lengthInches = day.babyLengthMillimeters?.let { ConversionTools.convertMmToInches(it) },
                            percentile = null,
                        )
                    },
                )
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
            bpmEntry = BpmEntryEntity(entryId = 1001, systolic = 115, diastolic = 75, pulse = 60, meanArterial = "88", note = "This is where a note would go."),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1002, accountId = "sample", entryTimestamp = "2025-08-06T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(entryId = 1002, systolic = 115, diastolic = 75, pulse = 60, meanArterial = "88", note = "Another note here."),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1003, accountId = "sample", entryTimestamp = "2025-08-05T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(entryId = 1003, systolic = 118, diastolic = 76, pulse = 72, meanArterial = "90", note = null),
        ),
        BpmEntry(
            entry = EntryEntity(id = 1004, accountId = "sample", entryTimestamp = "2025-08-04T09:52:00.000Z", operationType = "create", deviceType = "bpm", deviceId = "bpm-1"),
            bpmEntry = BpmEntryEntity(entryId = 1004, systolic = 120, diastolic = 78, pulse = 75, meanArterial = "92", note = null),
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
