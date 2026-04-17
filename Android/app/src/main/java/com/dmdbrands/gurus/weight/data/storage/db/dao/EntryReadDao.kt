package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.dmdbrands.gurus.weight.domain.model.common.BabyDailySummaryResult
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedActiveEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import kotlinx.coroutines.flow.Flow

/**
 * DAO for history-specific read queries across all product types.
 * Separate from [EntryDao] to keep CRUD/sync operations isolated.
 * All queries use entry_view (filters deleted entries).
 */
@Dao
interface EntryReadDao {

  companion object {
    const val UTC = "'utc'"
    const val LOCAL_TIME = "'localtime'"

    const val MONTH_JAN = "'Jan'"
    const val MONTH_FEB = "'Feb'"
    const val MONTH_MAR = "'Mar'"
    const val MONTH_APR = "'Apr'"
    const val MONTH_MAY = "'May'"
    const val MONTH_JUN = "'Jun'"
    const val MONTH_JUL = "'Jul'"
    const val MONTH_AUG = "'Aug'"
    const val MONTH_SEP = "'Sep'"
    const val MONTH_OCT = "'Oct'"
    const val MONTH_NOV = "'Nov'"
    const val MONTH_DEC = "'Dec'"
  }

  // ---------------------------------------------------------------------------
  // Weight History
  // ---------------------------------------------------------------------------

  /**
   * Monthly aggregated weight history — avg weight, entry count, change per month.
   * Same query as EntryDao.getMonthlyHistory — duplicated here for clean separation.
   * @param accountId The account ID
   * @return Flow of monthly weight summaries ordered by most recent first
   */
  /**
   * Optimised: single GROUP BY computes avg, count, first/last in one pass.
   * No correlated subqueries.
   */
  @Query(
    """
    WITH period_stats AS (
        SELECT
            strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
            MIN(e.entryTimestamp) AS firstTimestamp,
            MAX(e.entryTimestamp) AS lastTimestamp,
            AVG(bse.weight) AS avgWeight,
            COUNT(*) AS entryCount
        FROM entry_view e
        INNER JOIN body_scale_entry bse ON e.id = bse.id
        WHERE e.accountId = :accountId
          AND (e.operationType IS NULL OR e.operationType != 'delete')
          AND bse.weight IS NOT NULL
        GROUP BY period
    )
    SELECT
        CASE CAST(strftime('%m', datetime(ps.firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
            WHEN 1 THEN $MONTH_JAN WHEN 2 THEN $MONTH_FEB
            WHEN 3 THEN $MONTH_MAR WHEN 4 THEN $MONTH_APR
            WHEN 5 THEN $MONTH_MAY WHEN 6 THEN $MONTH_JUN
            WHEN 7 THEN $MONTH_JUL WHEN 8 THEN $MONTH_AUG
            WHEN 9 THEN $MONTH_SEP WHEN 10 THEN $MONTH_OCT
            WHEN 11 THEN $MONTH_NOV WHEN 12 THEN $MONTH_DEC
        END || ' ' || strftime('%Y', datetime(ps.firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS entryTimestamp,
        ps.avgWeight,
        ps.entryCount,
        CASE
            WHEN first_e.weight IS NOT NULL AND last_e.weight IS NOT NULL
            THEN last_e.weight - first_e.weight
            ELSE NULL
        END AS change
    FROM period_stats ps
    LEFT JOIN body_scale_entry first_e
        ON first_e.id = (
            SELECT e2.id FROM entry_view e2
            INNER JOIN body_scale_entry b2 ON e2.id = b2.id
            WHERE e2.accountId = :accountId AND e2.entryTimestamp = ps.firstTimestamp
            LIMIT 1
        )
    LEFT JOIN body_scale_entry last_e
        ON last_e.id = (
            SELECT e3.id FROM entry_view e3
            INNER JOIN body_scale_entry b3 ON e3.id = b3.id
            WHERE e3.accountId = :accountId AND e3.entryTimestamp = ps.lastTimestamp
            LIMIT 1
        )
    ORDER BY ps.period DESC
    """,
  )
  fun getWeightMonthlyHistory(accountId: String): Flow<List<HistoryMonth>>

  /**
   * Individual weight entries for a specific month.
   * @param accountId The account ID
   * @param month The month in YYYY-MM format
   * @return Flow of entries (convert via toEntry(), filter ScaleEntry in repo)
   */
 /**
   * Individual weight entries for a specific month.
   * @param accountId The account ID
   * @param month The month in "Mon YYYY" format (e.g. "Mar 2025") — matches HistoryMonth.entryTimestamp
   * @return Flow of entries (convert via toEntry(), filter ScaleEntry in repo)
   */
  @Transaction
  @Query(
    """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
        AND (operationType IS NULL OR operationType != 'delete')
        AND (
            CASE CAST(strftime('%m', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
                WHEN 1 THEN $MONTH_JAN WHEN 2 THEN $MONTH_FEB
                WHEN 3 THEN $MONTH_MAR WHEN 4 THEN $MONTH_APR
                WHEN 5 THEN $MONTH_MAY WHEN 6 THEN $MONTH_JUN
                WHEN 7 THEN $MONTH_JUL WHEN 8 THEN $MONTH_AUG
                WHEN 9 THEN $MONTH_SEP WHEN 10 THEN $MONTH_OCT
                WHEN 11 THEN $MONTH_NOV WHEN 12 THEN $MONTH_DEC
            END || ' ' || strftime('%Y', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}))
        ) = :month
        ORDER BY datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}) DESC
    """,
  )
  fun getWeightMonthDetail(accountId: String, month: String): Flow<List<PopulatedActiveEntry>>

  // ---------------------------------------------------------------------------
  // BPM History
  // ---------------------------------------------------------------------------

  /**
   * Monthly aggregated BP history — avg systolic, diastolic, pulse per month.
   * @param accountId The account ID
   * @return Flow of monthly BP summaries ordered by most recent first
   */
  @Query(
    """
    WITH bpm_entries AS (
        SELECT
            e.entryTimestamp,
            bp.systolic,
            bp.diastolic,
            bp.pulse,
            strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period
        FROM entry_view e
        INNER JOIN bpm_entry bp ON e.id = bp.entryId
        WHERE e.accountId = :accountId
          AND (e.operationType IS NULL OR e.operationType != 'delete')
    )
    SELECT
        CASE CAST(strftime('%m', MIN(entryTimestamp)) AS INTEGER)
            WHEN 1 THEN $MONTH_JAN WHEN 2 THEN $MONTH_FEB
            WHEN 3 THEN $MONTH_MAR WHEN 4 THEN $MONTH_APR
            WHEN 5 THEN $MONTH_MAY WHEN 6 THEN $MONTH_JUN
            WHEN 7 THEN $MONTH_JUL WHEN 8 THEN $MONTH_AUG
            WHEN 9 THEN $MONTH_SEP WHEN 10 THEN $MONTH_OCT
            WHEN 11 THEN $MONTH_NOV WHEN 12 THEN $MONTH_DEC
        END || ' ' || strftime('%Y', MIN(entryTimestamp)) AS entryTimestamp,
        CAST(AVG(systolic) AS INTEGER) AS avgSystolic,
        CAST(AVG(diastolic) AS INTEGER) AS avgDiastolic,
        CAST(AVG(pulse) AS INTEGER) AS avgPulse,
        COUNT(*) AS entryCount
    FROM bpm_entries
    GROUP BY period
    ORDER BY period DESC
    """,
  )
  fun getBpmMonthlyHistory(accountId: String): Flow<List<BpHistoryMonth>>

  /**
   * Individual BPM entries for a specific month.
   * @param accountId The account ID
   * @param month The month in "Mon YYYY" format (e.g. "Mar 2025") — matches BpHistoryMonth.entryTimestamp
   * @return Flow of entries (convert via toEntry(), filter BpmEntry in repo)
   */
  @Transaction
  @Query(
    """
    SELECT * FROM entry_view
    WHERE accountId = :accountId
      AND (operationType IS NULL OR operationType != 'delete')
      AND (
          CASE CAST(strftime('%m', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
              WHEN 1 THEN $MONTH_JAN WHEN 2 THEN $MONTH_FEB
              WHEN 3 THEN $MONTH_MAR WHEN 4 THEN $MONTH_APR
              WHEN 5 THEN $MONTH_MAY WHEN 6 THEN $MONTH_JUN
              WHEN 7 THEN $MONTH_JUL WHEN 8 THEN $MONTH_AUG
              WHEN 9 THEN $MONTH_SEP WHEN 10 THEN $MONTH_OCT
              WHEN 11 THEN $MONTH_NOV WHEN 12 THEN $MONTH_DEC
          END || ' ' || strftime('%Y', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}))
      ) = :month
    ORDER BY datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}) DESC
    """,
  )
  fun getBpmMonthDetail(accountId: String, month: String): Flow<List<PopulatedActiveEntry>>

  // ---------------------------------------------------------------------------
  // Baby History
  // ---------------------------------------------------------------------------

  /**
   * Baby daily summaries with week number for weekly grouping.
   * Returns [BabyDailySummaryResult] with raw DB values (decigrams, millimeters).
   * Repository converts to display format and groups by weekNumber into BabyWeekGroup.
   * @param accountId The account ID
   * @param babyId The baby ID
   * @return Flow of daily summaries ordered by most recent first
   */
  @Query(
    """
    SELECT
        strftime('%m/%d/%y', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS date,
        COUNT(*) AS entryCount,
        CAST(AVG(be.babyWeightDecigrams) AS INTEGER) AS babyWeightDecigrams,
        CAST(AVG(be.babyLengthMillimeters) AS INTEGER) AS babyLengthMillimeters,
        CAST(strftime('%W', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER) AS weekNumber,
        CAST(strftime('%Y', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER) AS year
    FROM entry_view e
    INNER JOIN baby_entry be ON e.id = be.id
    WHERE e.accountId = :accountId
      AND be.babyId = :babyId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}) DESC
    """,
  )
  fun getBabyWeeklyHistory(
    accountId: String,
    babyId: String,
  ): Flow<List<BabyDailySummaryResult>>

  /**
   * Individual baby entries for a specific date.
   * @param accountId The account ID
   * @param babyId The baby ID
   * @param date The date in YYYY-MM-DD format
   * @return Flow of individual baby entries (convert via toEntry(), filter BabyEntry in repo)
   */
  @Transaction
  @Query(
    """
    SELECT * FROM entry_view
    WHERE accountId = :accountId
      AND (operationType IS NULL OR operationType != 'delete')
      AND id IN (SELECT id FROM baby_entry WHERE babyId = :babyId)
      AND strftime('%Y-%m-%d', datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME})) = :date
    ORDER BY datetime(entryTimestamp, ${UTC}, ${LOCAL_TIME}) DESC
    """,
  )
  fun getBabyDayDetail(
    accountId: String,
    babyId: String,
    date: String,
  ): Flow<List<PopulatedActiveEntry>>

  // ---------------------------------------------------------------------------
  // Weight Graph Queries (moved from EntryDao)
  // ---------------------------------------------------------------------------

  /**
   * Weight monthly averages for graph — all body scale metrics grouped by month.
   * Used for MONTH/YEAR/TOTAL graph segments.
   */
  @Query(
    """
    SELECT
      strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
      datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of month') AS entryTimestamp,
      AVG(CASE WHEN bse.weight > 0 THEN bse.weight ELSE NULL END) AS weight,
      AVG(CASE WHEN bse.bodyFat > 0 THEN bse.bodyFat ELSE NULL END) AS bodyFat,
      AVG(CASE WHEN bse.muscleMass > 0 THEN bse.muscleMass ELSE NULL END) AS muscleMass,
      AVG(CASE WHEN bse.water > 0 THEN bse.water ELSE NULL END) AS water,
      AVG(CASE WHEN bse.bmi > 0 THEN bse.bmi ELSE NULL END) AS bmi,
      AVG(CASE WHEN bsem.bmr > 0 THEN bsem.bmr ELSE NULL END) AS bmr,
      AVG(CASE WHEN bsem.metabolicAge > 0 THEN bsem.metabolicAge ELSE NULL END) AS metabolicAge,
      AVG(CASE WHEN bsem.proteinPercent > 0 THEN bsem.proteinPercent ELSE NULL END) AS proteinPercent,
      AVG(CASE WHEN bsem.pulse > 0 THEN bsem.pulse ELSE NULL END) AS pulse,
      AVG(CASE WHEN bsem.skeletalMusclePercent > 0 THEN bsem.skeletalMusclePercent ELSE NULL END) AS skeletalMusclePercent,
      AVG(CASE WHEN bsem.subcutaneousFatPercent > 0 THEN bsem.subcutaneousFatPercent ELSE NULL END) AS subcutaneousFatPercent,
      AVG(CASE WHEN bsem.visceralFatLevel > 0 THEN bsem.visceralFatLevel ELSE NULL END) AS visceralFatLevel,
      AVG(CASE WHEN bsem.boneMass > 0 THEN bsem.boneMass ELSE NULL END) AS boneMass,
      AVG(CASE WHEN bsem.impedance > 0 THEN bsem.impedance ELSE NULL END) AS impedance,
      MAX(e.unit) AS unit
    FROM entry_view AS e
    LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
    LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY period
    ORDER BY period DESC
    """,
  )
  fun getWeightMonthlyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>>

  /**
   * Weight daily averages for graph — all body scale metrics grouped by day.
   * Used for WEEK graph segment.
   */
  @Query(
    """
    SELECT
      strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
      datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of day') AS entryTimestamp,
      AVG(CASE WHEN bse.weight > 0 THEN bse.weight ELSE NULL END) AS weight,
      AVG(CASE WHEN bse.bodyFat > 0 THEN bse.bodyFat ELSE NULL END) AS bodyFat,
      AVG(CASE WHEN bse.muscleMass > 0 THEN bse.muscleMass ELSE NULL END) AS muscleMass,
      AVG(CASE WHEN bse.water > 0 THEN bse.water ELSE NULL END) AS water,
      AVG(CASE WHEN bse.bmi > 0 THEN bse.bmi ELSE NULL END) AS bmi,
      AVG(CASE WHEN bsem.bmr > 0 THEN bsem.bmr ELSE NULL END) AS bmr,
      AVG(CASE WHEN bsem.metabolicAge > 0 THEN bsem.metabolicAge ELSE NULL END) AS metabolicAge,
      AVG(CASE WHEN bsem.proteinPercent > 0 THEN bsem.proteinPercent ELSE NULL END) AS proteinPercent,
      AVG(CASE WHEN bsem.pulse > 0 THEN bsem.pulse ELSE NULL END) AS pulse,
      AVG(CASE WHEN bsem.skeletalMusclePercent > 0 THEN bsem.skeletalMusclePercent ELSE NULL END) AS skeletalMusclePercent,
      AVG(CASE WHEN bsem.subcutaneousFatPercent > 0 THEN bsem.subcutaneousFatPercent ELSE NULL END) AS subcutaneousFatPercent,
      AVG(CASE WHEN bsem.visceralFatLevel > 0 THEN bsem.visceralFatLevel ELSE NULL END) AS visceralFatLevel,
      AVG(CASE WHEN bsem.boneMass > 0 THEN bsem.boneMass ELSE NULL END) AS boneMass,
      AVG(CASE WHEN bsem.impedance > 0 THEN bsem.impedance ELSE NULL END) AS impedance,
      MAX(e.unit) AS unit
    FROM entry_view AS e
    LEFT JOIN body_scale_entry AS bse ON e.id = bse.id
    LEFT JOIN body_scale_entry_metric AS bsem ON e.id = bsem.id
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY period
    ORDER BY period DESC
    """,
  )
  fun getWeightDailyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>>

  // ---------------------------------------------------------------------------
  // Weight Snapshot Query (Dashboard mini-chart)
  // ---------------------------------------------------------------------------

  /**
   * Last 10 weight entries for dashboard snapshot mini-chart.
   * Two-step: find last 10 distinct days FIRST, then aggregate only those days.
   */
  @Query(
    """
    WITH last_10_days AS (
      SELECT DISTINCT strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS day
      FROM entry_view AS e
      INNER JOIN body_scale_entry AS bse ON e.id = bse.id
      WHERE e.accountId = :accountId
        AND (e.operationType IS NULL OR e.operationType != 'delete')
        AND bse.weight > 0
      ORDER BY day DESC
      LIMIT 10
    )
    SELECT
      d.day AS period,
      datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of day') AS entryTimestamp,
      AVG(bse.weight) AS weight,
      MAX(e.unit) AS unit
    FROM last_10_days AS d
    INNER JOIN entry_view AS e
      ON strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) = d.day
    INNER JOIN body_scale_entry AS bse ON e.id = bse.id
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
      AND bse.weight > 0
    GROUP BY d.day
    ORDER BY d.day ASC
    """,
  )
  fun getWeightSnapshotGraphData(accountId: String): Flow<List<WeightSnapshotPoint>>

  // ---------------------------------------------------------------------------
  // BPM Graph Queries
  // ---------------------------------------------------------------------------

  /**
   * BP monthly averages for graph — avg systolic/diastolic/pulse grouped by month.
   * Used for MONTH/YEAR/TOTAL graph segments.
   */
  @Query(
    """
    SELECT
        strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        MAX(e.entryTimestamp) AS entryTimestamp,
        CAST(AVG(bp.systolic) AS INTEGER) AS avgSystolic,
        CAST(AVG(bp.diastolic) AS INTEGER) AS avgDiastolic,
        CAST(AVG(bp.pulse) AS INTEGER) AS avgPulse
    FROM entry_view e
    INNER JOIN bpm_entry bp ON e.id = bp.entryId
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY period DESC
    """,
  )
  fun getBpmMonthlyGraphData(accountId: String): Flow<List<PeriodBpmSummary>>

  /**
   * BP daily averages for graph — avg systolic/diastolic/pulse grouped by day.
   * Used for WEEK graph segment.
   */
  @Query(
    """
    SELECT
        strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        MAX(e.entryTimestamp) AS entryTimestamp,
        CAST(AVG(bp.systolic) AS INTEGER) AS avgSystolic,
        CAST(AVG(bp.diastolic) AS INTEGER) AS avgDiastolic,
        CAST(AVG(bp.pulse) AS INTEGER) AS avgPulse
    FROM entry_view e
    INNER JOIN bpm_entry bp ON e.id = bp.entryId
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY period DESC
    """,
  )
  fun getBpmDailyGraphData(accountId: String): Flow<List<PeriodBpmSummary>>

  /**
   * Last 10 daily BP averages for dashboard snapshot mini-chart.
   */
  @Query(
    """
    SELECT * FROM (
      SELECT
        strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of day') AS entryTimestamp,
        CAST(AVG(bp.systolic) AS INTEGER) AS avgSystolic,
        CAST(AVG(bp.diastolic) AS INTEGER) AS avgDiastolic,
        CAST(AVG(bp.pulse) AS INTEGER) AS avgPulse
      FROM entry_view e
      INNER JOIN bpm_entry bp ON e.id = bp.entryId
      WHERE e.accountId = :accountId
        AND (e.operationType IS NULL OR e.operationType != 'delete')
      GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
      ORDER BY period DESC
      LIMIT 10
    )
    ORDER BY entryTimestamp ASC
    """,
  )
  fun getBpmSnapshotGraphData(accountId: String): Flow<List<PeriodBpmSummary>>

  /**
   * Last N per-day BP averages for the account (most recent day first).
   *
   * Each row is a day of BP data averaged into a single [PeriodBpmSummary]. Scoped
   * to the account — NOT to any selected chart window — used by the dashboard
   * three-reading-average card and sheet.
   *
   * If the account has BP entries on < n days, fewer than n rows are returned.
   */
  @Query(
    """
    SELECT
        strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of day') AS entryTimestamp,
        CAST(AVG(bp.systolic) AS INTEGER) AS avgSystolic,
        CAST(AVG(bp.diastolic) AS INTEGER) AS avgDiastolic,
        CAST(AVG(bp.pulse) AS INTEGER) AS avgPulse
    FROM entry_view e
    INNER JOIN bpm_entry bp ON e.id = bp.entryId
    WHERE e.accountId = :accountId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY period DESC
    LIMIT :n
    """,
  )
  fun getBpmLastNDayEntries(accountId: String, n: Int): Flow<List<PeriodBpmSummary>>

  // ---------------------------------------------------------------------------
  // Cross-product read queries (moved from EntryDao)
  // ---------------------------------------------------------------------------

  /**
   * Get the latest entry for a specific account with all related details.
   * @param accountId The account ID
   * @return The latest Entry with relations if found, null otherwise
   */
  @Transaction
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete') ORDER BY datetime(entryTimestamp) DESC LIMIT 1")
  fun getLatestEntry(accountId: String): Flow<PopulatedActiveEntry?>

  /**
   * Get entries by operation type for a specific account.
   * @param accountId The account ID
   * @param operationType The operation type
   * @return A Flow of entries with the specified operation type
   */
  @Transaction
  @Query("SELECT * FROM entry WHERE accountId = :accountId AND operationType = :operationType")
  fun getEntriesByOperationType(
    accountId: String,
    operationType: String,
  ): Flow<List<PopulatedEntry>>

  /**
   * Get the oldest entry for an account.
   * @param accountId The account ID
   * @return The oldest entry if found, null otherwise
   */
  @Query("SELECT * FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete') ORDER BY entryTimestamp ASC LIMIT 1")
  suspend fun getOldestEntry(accountId: String): PopulatedActiveEntry?

  /**
   * Get entry timestamps for streak calculation.
   * Returns one entry timestamp per day, ordered with newest first.
   * @param accountId The account ID
   * @return List of entry timestamps for streak calculation
   */
  @Query(
    """
         SELECT
        strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME}))
        FROM entry_view
        WHERE accountId = :accountId
          AND (operationType IS NULL OR operationType != 'delete')
        GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC}, ${LOCAL_TIME}))
        ORDER BY datetime(entryTimestamp,${UTC},${LOCAL_TIME}) DESC
        """,
  )
  suspend fun getStreakData(accountId: String): List<String>

  /**
   * Get the total count of entries for an account.
   * @param accountId The account ID
   * @return The total count of entries
   */
  @Query("SELECT COUNT(entryTimestamp) as total FROM entry_view WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')")
  suspend fun getTotalCount(accountId: String): Int

  /**
   * Get the longest streak count for an account.
   * Uses window function (row_number) + GROUP BY to compute max consecutive days; faster than recursive CTE for large datasets.
   * @param accountId The account ID
   * @return The longest streak count
   */
  @Query(
    """
        SELECT COALESCE(MAX(streak_count), 0) AS longestStreak FROM (
          SELECT COUNT(*) AS streak_count FROM (
            SELECT day_start, row_number() OVER (ORDER BY day_start) AS row_num FROM (
              SELECT strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME})) AS day_start
              FROM entry_view
              WHERE accountId = :accountId AND (operationType IS NULL OR operationType != 'delete')
              GROUP BY strftime('%Y-%m-%d', datetime(entryTimestamp,${UTC},${LOCAL_TIME}))
            )
          ) AS t1
          GROUP BY strftime('%Y-%m-%d', date(day_start, '-' || (row_num - 1) || ' day'))
        )
        """,
  )
  suspend fun getLongestStreakCount(accountId: String): Int

  /**
   * Days (newest first) on which the account has at least one BP reading.
   *
   * Mirrors [getStreakData] but narrowed to BP entries via an inner join against
   * `bpm_entry`. Current and longest BP streak are computed in Kotlin from this
   * list via [com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.computeCurrentStreakFromDates] /
   * [com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.computeLongestStreakFromDates].
   *
   * Returns a [Flow] so the BP progress flow on EntryReadService re-emits
   * automatically when BP entries are inserted, updated, or deleted.
   */
  @Query(
    """
        SELECT strftime('%Y-%m-%d', datetime(e.entryTimestamp,${UTC},${LOCAL_TIME}))
        FROM entry_view AS e
        INNER JOIN bpm_entry AS b ON b.entryId = e.id
        WHERE e.accountId = :accountId
          AND (e.operationType IS NULL OR e.operationType != 'delete')
        GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp,${UTC},${LOCAL_TIME}))
        ORDER BY datetime(e.entryTimestamp,${UTC},${LOCAL_TIME}) DESC
        """,
  )
  fun getBpmStreakDays(accountId: String): Flow<List<String>>

  /**
   * Get monthly history for an account for the last 365 days.
   * Filters entries from the last 365 days, groups by month, and calculates averages.
   * @param accountId The account ID
   * @return Flow of list of monthly history for the last 365 days
   */
  @Query(
    """
    WITH entries_with_period AS (
        SELECT
            e.entryTimestamp,
            bse.weight,
            strftime('%Y-%m', datetime(e.entryTimestamp,${UTC},${LOCAL_TIME})) AS period
        FROM entry_view e
        LEFT JOIN body_scale_entry bse ON e.id = bse.id
        WHERE e.accountId = :accountId
          AND bse.weight IS NOT NULL
          AND (e.operationType IS NULL OR e.operationType != 'delete')
          AND datetime(e.entryTimestamp) >= datetime('now', '-365 days')
          AND datetime(e.entryTimestamp) <= datetime('now')
    ),
    first_last AS (
        SELECT
            period,
            MIN(entryTimestamp) AS firstTimestamp,
            MAX(entryTimestamp) AS lastTimestamp
        FROM entries_with_period
        GROUP BY period
    ),
    joined AS (
        SELECT
            fl.period,
            fl.firstTimestamp,
            fl.lastTimestamp,
            first_entry.weight AS firstWeight,
            last_entry.weight AS lastWeight,
            (SELECT AVG(weight) FROM entries_with_period WHERE period = fl.period) AS avgWeight,
            (SELECT COUNT(*) FROM entries_with_period WHERE period = fl.period) AS entryCount
        FROM first_last fl
        LEFT JOIN entries_with_period first_entry ON fl.firstTimestamp = first_entry.entryTimestamp
        LEFT JOIN entries_with_period last_entry ON fl.lastTimestamp = last_entry.entryTimestamp
    )
    SELECT
        CASE CAST(strftime('%m', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS INTEGER)
            WHEN 1 THEN $MONTH_JAN WHEN 2 THEN $MONTH_FEB
            WHEN 3 THEN $MONTH_MAR WHEN 4 THEN $MONTH_APR
            WHEN 5 THEN $MONTH_MAY WHEN 6 THEN $MONTH_JUN
            WHEN 7 THEN $MONTH_JUL WHEN 8 THEN $MONTH_AUG
            WHEN 9 THEN $MONTH_SEP WHEN 10 THEN $MONTH_OCT
            WHEN 11 THEN $MONTH_NOV WHEN 12 THEN $MONTH_DEC
        END || ' ' || strftime('%Y', datetime(firstTimestamp, ${UTC}, ${LOCAL_TIME})) AS entryTimestamp,
        avgWeight,
        entryCount,
        CASE
            WHEN firstWeight IS NOT NULL AND lastWeight IS NOT NULL
            THEN lastWeight - firstWeight
            ELSE NULL
        END AS change
    FROM joined
    ORDER BY period DESC
    """,
  )
  fun getMonthlyHistoryLastYear(accountId: String): Flow<List<HistoryMonth>>

  /**
   * Get entries since a given start date.
   * @param accountId The account ID
   * @param startDate The start date (ISO 8601 string)
   * @return Flow of list of entries since the start date
   */
  @Transaction
  @Query(
    """
        SELECT * FROM entry_view
        WHERE accountId = :accountId
          AND (operationType IS NULL OR operationType != 'delete')
          AND entryTimestamp >= :startDate
        ORDER BY datetime(entryTimestamp) DESC
    """,
  )
  fun getEntriesSince(accountId: String, startDate: String): Flow<List<PopulatedActiveEntry>>

  // ---------------------------------------------------------------------------
  // Baby Graph Queries
  // ---------------------------------------------------------------------------

  /**
   * Baby monthly averages for graph — avg weight/length grouped by month.
   * Used for MONTH/YEAR/TOTAL graph segments.
   */
  @Query(
    """
    SELECT
        strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        MAX(e.entryTimestamp) AS entryTimestamp,
        CAST(AVG(be.babyWeightDecigrams) AS INTEGER) AS avgWeightDecigrams,
        CAST(AVG(be.babyLengthMillimeters) AS INTEGER) AS avgLengthMillimeters
    FROM entry_view e
    INNER JOIN baby_entry be ON e.id = be.id
    WHERE e.accountId = :accountId
      AND be.babyId = :babyId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY period DESC
    """,
  )
  fun getBabyMonthlyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>

  /**
   * Baby daily averages for graph — avg weight/length grouped by day.
   * Used for WEEK graph segment.
   */
  @Query(
    """
    SELECT
        strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        MAX(e.entryTimestamp) AS entryTimestamp,
        CAST(AVG(be.babyWeightDecigrams) AS INTEGER) AS avgWeightDecigrams,
        CAST(AVG(be.babyLengthMillimeters) AS INTEGER) AS avgLengthMillimeters
    FROM entry_view e
    INNER JOIN baby_entry be ON e.id = be.id
    WHERE e.accountId = :accountId
      AND be.babyId = :babyId
      AND (e.operationType IS NULL OR e.operationType != 'delete')
    GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
    ORDER BY period DESC
    """,
  )
  fun getBabyDailyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>

  /**
   * Last 10 daily baby weight averages for dashboard snapshot mini-chart.
   */
  @Query(
    """
    SELECT * FROM (
      SELECT
        strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME})) AS period,
        datetime(MIN(e.entryTimestamp), ${UTC}, ${LOCAL_TIME}, 'start of day') AS entryTimestamp,
        CAST(AVG(be.babyWeightDecigrams) AS INTEGER) AS avgWeightDecigrams,
        CAST(AVG(be.babyLengthMillimeters) AS INTEGER) AS avgLengthMillimeters
      FROM entry_view e
      INNER JOIN baby_entry be ON e.id = be.id
      WHERE e.accountId = :accountId
        AND be.babyId = :babyId
        AND (e.operationType IS NULL OR e.operationType != 'delete')
      GROUP BY strftime('%Y-%m-%d', datetime(e.entryTimestamp, ${UTC}, ${LOCAL_TIME}))
      ORDER BY period DESC
      LIMIT 10
    )
    ORDER BY entryTimestamp ASC
    """,
  )
  fun getBabySnapshotGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>
}
