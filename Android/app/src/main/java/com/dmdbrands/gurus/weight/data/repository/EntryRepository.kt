// data/repository/EntryRepository.kt
package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter.isValidIsoTimestamp
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToDisplay
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToStored
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for managing entries.
 * Only supports the 14 direct functions from EntryDao.
 */
@Singleton
class EntryRepository @Inject constructor(
  private val entryDao: EntryDao,
  private val entryApi: EntryApi,
) : IEntryRepository {
  /**
   * Inserts a single entry.
   */
  override suspend fun insert(entry: Entry): Long {
    return if (isValidIsoTimestamp(entry.entry.entryTimestamp))
      entryDao.insert(entry.convertToStored())
    else
      -1
  }

  /**
   * Updates a single entry.
   */
  override suspend fun update(entry: Entry): Long {
    return entryDao.update(entry.convertToStored())
  }

  /**
   * Inserts a list of entries.
   */
  override suspend fun insert(entries: List<Entry>) {
    val validEntries = entries.filter { isValidIsoTimestamp(it.entry.entryTimestamp) }.map { it.convertToStored() }
    entryDao.insert(validEntries)
  }

  /**
   * Marks an entry as deleted.
   */
  override suspend fun delete(entry: Entry) = entryDao.delete(entry)

  /**
   * Deletes an entry by its ID.
   */
  override suspend fun deleteById(id: Long): Int = entryDao.deleteById(id)

  /**
   * Gets an entry by its ID.
   */
  override suspend fun getEntryById(id: Long): Entry? =
    entryDao.getEntryById(id)?.toEntry()

  /**
   * Gets the latest valid entry for an account.
   */
  override suspend fun getLatestEntry(accountId: String): Flow<Entry?> =
    entryDao.getLatestEntry(accountId).map { it?.toEntry() }

  /**
   * Gets all valid entries for an account.
   */
  override suspend fun getEntriesByAccount(accountId: String, convertToDisplay: Boolean): List<Entry> =
    entryDao.getEntriesByAccount(accountId).mapNotNull { it.toEntry(convertToDisplay) }

  /**
   * Gets valid entries for an account within a time range.
   */
  //TODO: This is not used at the moment and is retained for possible future requirements
  override fun getEntriesByTimeRange(
    accountId: String,
    startTime: String,
    endTime: String
  ): Flow<List<Entry>> =
    entryDao.getEntriesByTimeRange(accountId, startTime, endTime).map { flow ->
      flow.mapNotNull { it.toEntry() }
    }

  /**
   * Gets valid entries for an account by device type.
   */
  override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>> =
    entryDao.getEntriesByDeviceType(accountId, deviceType).map { flow ->
      flow.mapNotNull { it.toEntry() }
    }

  /**
   * Retrieves entries for the last N days for a given account.
   * @param accountId The account ID
   * @param days Number of days to look back
   * @return Flow of list of Entry objects
   */
  override suspend fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<Entry>> {
    val startInstant = java.time.Instant.now().minus(java.time.Duration.ofDays(days.toLong()))
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val startDate = java.time.ZonedDateTime.ofInstant(startInstant, java.time.ZoneOffset.UTC).format(formatter)
    return entryDao.getEntriesSince(accountId, startDate).map { list ->
      list.mapNotNull { it.toEntry() }
    }
  }

  override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> = flow {
    // emit(entryDao.deleteAllEntriesForAccount(accountId))
  }

  /**
   * Gets entries for an account by operation type.
   */
  override fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<Entry>> =
    entryDao.getEntriesByOperationType(accountId, operationType).map { flow ->
      flow.mapNotNull { it.toEntry() }
    }

  /**
   * Gets all unsynced entries for an account.
   */
  override suspend fun getUnSynced(accountId: String): List<Entry> =
    entryDao.getUnSynced(accountId).mapNotNull { it.toEntry() }

  /**
   * Increments the attempts count for an entry.
   */
  override suspend fun incrementAttempts(id: Long): Int = entryDao.incrementAttempts(id)

  /**
   * Gets failed operations for an account.
   */
  override suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<Entry> =
    entryDao.getFailedOperations(accountId, maxAttempts).mapNotNull { it.toEntry() }

  /**
   * Clears all unsynced entries for an account.
   */
  override suspend fun clearUnSynced(accountId: String): Int = entryDao.clearUnSynced(accountId)

  /**
   * Sends an operation to the API and marks it as synced if successful. Adds to opstack if failed.
   * @param operation The operation entry to send.
   * @throws Exception if the API call fails.
   */
  override suspend fun sendOperationToAPI(operation: ScaleApiEntry?) {
    try {
      if (operation == null) {
        throw IllegalArgumentException("Operation cannot be null")
      }
      AppLog.d("EntryRepository", "Sending operation to API: ${operation}")
      entryApi.sendOperation(operation)
      AppLog.i("EntryRepository", "Operation sent successfully: ${operation}")
    } catch (e: Exception) {
      AppLog.e("EntryRepository", "Failed to send operation to API: ${operation}", e)
      throw e
    }
  }

  /**
   * Gets operations from the API since the last update, or all if lastUpdated is null.
   * @param lastUpdated The last updated timestamp, or null.
   * @return List of operations.
   */
  override suspend fun getOperationsFromAPI(syncTimeStamp: String): OperationsResponse? {
    AppLog.d("EntryRepository","getOperationsFromAPI - $syncTimeStamp")
    return try {
      val response = if (syncTimeStamp.isNotBlank()) {
        AppLog.d("EntryRepository","getOperationsFromAPI using sync timestamp - $syncTimeStamp")
        entryApi.getOperations(syncTimeStamp)
      } else {
        AppLog.d("EntryRepository","getOperationsFromAPI without sync timestamp - $syncTimeStamp")
        entryApi.getAllOperations()
      }
      response
    } catch (e: Exception) {
      AppLog.e("EntryRepository", "getOperationsFromAPI failed for syncTimeStamp: $syncTimeStamp", e)
      null
    }
  }

  /**
   * Gets entries for a specific month and year.
   * @param accountId The account ID
   * @param month The month in YYYY-MM format
   * @return Flow of list of entries for the specified month
   */
  override fun getMonthDetail(accountId: String, month: String): Flow<List<Entry>> =
    entryDao.getMonthDetail(accountId, month).map { views ->
      views.mapNotNull { it.toEntry() }
    }

  /**
   * Gets all monthly aggregated data.
   * @param accountId The account ID
   * @return Flow of list of all monthly aggregated data
   */
  override fun getMonthlyAverage(accountId: String): Flow<List<HistoryMonth>> {
    return entryDao.getMonthlyHistory(accountId).map { list -> list.map { it.convertToDisplay() } }
  }

  /**
   * Gets monthly history for an account for the last 365 days.
   * This method automatically filters entries from the last 365 days, groups by month, and calculates averages.
   */
  override fun getMonthlyHistoryLastYear(accountId: String): Flow<List<HistoryMonth>> {
    return entryDao.getMonthlyHistoryLastYear(accountId).map { list -> list.map { it.convertToDisplay() } }
  }

  /**
   * Gets the operation count for an account.
   * @param accountId The account ID
   * @return The number of operations
   */
  override suspend fun getOperationCount(accountId: String): Int =
    entryDao.getOperationCount(accountId)

  /**
   * Gets monthly averages of body scale data for an account using JOINs.
   */
  override fun getMonthlyBodyScaleAveragesWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
    entryDao.getMonthlyBodyScaleAveragesWithJoin(accountId).map { list -> list.map { it.convertToDisplay() } }

  /**
   * Gets the latest body scale entry for each month for an account using JOINs.
   */
  override fun getMonthlyBodyScaleLatestWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
    entryDao.getMonthlyBodyScaleLatestWithJoin(accountId).map { list -> list.map { it.convertToDisplay() } }

  /**
   * Gets daywise averages of body scale data for an account using JOINs.
   */
  override fun getDaywiseBodyScaleAveragesWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
    entryDao.getDaywiseBodyScaleAveragesWithJoin(accountId).map { list -> list.map { it.convertToDisplay() } }

  /**
   * Gets the latest body scale entry for each day for an account using JOINs.
   */
  override fun getDaywiseBodyScaleLatestWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
    entryDao.getDaywiseBodyScaleLatestWithJoin(accountId).map { list -> list.map { it.convertToDisplay() } }

  /**
   * Get the oldest entry for an account.
   * @param accountId The account ID
   * @return The oldest entry if found, null otherwise
   */
  override suspend fun getOldestEntry(accountId: String): Entry? =
    entryDao.getOldestEntry(accountId)?.toEntry()

  /**
   * Get entry timestamps for streak calculation.
   * Returns one entry timestamp per day, ordered with newest first.
   * @param accountId The account ID
   * @return List of entry timestamps for streak calculation
   */
  override suspend fun getStreakData(accountId: String): List<String> =
    entryDao.getStreakData(accountId)

  /**
   * Get the total count of entries for an account.
   * @param accountId The account ID
   * @return The total count of entries
   */
  override suspend fun getTotalCount(accountId: String): Int =
    entryDao.getTotalCount(accountId)

  /**
   * Get the longest streak count for an account.
   * @param accountId The account ID
   * @return The longest streak count
   */
  override suspend fun getLongestStreakCount(accountId: String): Int =
    entryDao.getLongestStreakCount(accountId)

  /**
   * Get entries for an account in a specific date range (inclusive).
   * @param accountId The account ID
   * @param startDate The start date (ISO 8601 string)
   * @param endDate The end date (ISO 8601 string)
   * @return List of entries in the date range
   */
  override suspend fun getEntriesInRange(accountId: String, startDate: String, endDate: String): List<Entry> =
    entryDao.getEntriesInRange(accountId, startDate, endDate).first().mapNotNull { it.toEntry() }
}
