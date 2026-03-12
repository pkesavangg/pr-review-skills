package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class OperationType {
    CREATE,
    DELETE,
}

internal object EntryServiceHelper {

    /**
     * Computes longest streak (max consecutive days with an entry) from a list of entry dates (yyyy-MM-dd).
     * Pure in-memory single pass; no DB access. Use in place of getLongestStreakCount for large datasets.
     */
    fun computeLongestStreakFromDates(entryDates: List<String>): Int {
        if (entryDates.isEmpty()) return 0
        val sorted = entryDates.distinct().sorted()
        var maxStreak = 1
        var current = 1
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fun toDayMillis(s: String): Long =
            (fmt.parse(s)?.time ?: 0L) / (24 * 60 * 60 * 1000)
        for (i in 1 until sorted.size) {
            val prevDay = toDayMillis(sorted[i - 1])
            val thisDay = toDayMillis(sorted[i])
            if (thisDay == prevDay + 1) {
                current++
                maxStreak = maxOf(maxStreak, current)
            } else {
                current = 1
            }
        }
        return maxStreak
    }

    /**
     * Computes current streak count from a list of entry dates (yyyy-MM-dd, newest first).
     * Pure in-memory calculation; no DB access.
     */
    fun computeCurrentStreakFromDates(entryDates: List<String>): Int {
        if (entryDates.isEmpty()) return 0
        var score = 0
        val dateToCheck = Calendar.getInstance()
        fun datesAreSame(d1: Calendar, d2: Calendar): Boolean =
            d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) &&
                d1.get(Calendar.MONTH) == d2.get(Calendar.MONTH) &&
                d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)
        fun addOne() {
            score++
            dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
        }
        val firstEntryTimestamp = entryDates.first()
        val remainingDates = entryDates.drop(1)
        val firstEntryDate = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(firstEntryTimestamp) ?: return 0
        }
        if (datesAreSame(dateToCheck, firstEntryDate)) {
            addOne()
        } else {
            dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
            if (datesAreSame(dateToCheck, firstEntryDate)) addOne()
        }
        for (entryTimestamp in remainingDates) {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entryTimestamp) ?: break
            val entryDate = Calendar.getInstance().apply { time = parsed }
            if (datesAreSame(dateToCheck, entryDate)) addOne() else break
        }
        return score
    }

    fun processWeight(
        weight: Double,
        unit: WeightUnit?,
        weightLess: Weightless?,
    ): Double {
        val convertedWeight = ConversionTools.convertStoredToDisplay(weight, unit == WeightUnit.KG)
        return if (weightLess?.isWeightlessOn == true) convertedWeight - weightLess.weightlessWeight else convertedWeight
    }

    /**
     * Executes a list of operations received from the server.
     */
    suspend fun executeOperations(
        entryRepository: IEntryRepository,
        operations: List<Entry>,
    ) {
        if (operations.isEmpty()) return
        try {
            val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
            entryRepository.insert(sortedOperations)
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error executing operations", e)
        }
    }

    /**
     * Executes a list of operations, handling both create and delete operations.
     * @param entryRepository The entry repository.
     * @param operations The list of operations to execute.
     * @param userHasOperations Whether the user has existing operations.
     * @param arePlaceholders Whether the operations are placeholders (not yet synced).
     */
    suspend fun executeOperations(
        entryRepository: IEntryRepository,
        operations: List<Entry>,
        userHasOperations: Boolean = true,
        arePlaceholders: Boolean = false,
    ) {
        if (operations.isEmpty()) return
        try {
            val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
            val createOperations = sortedOperations.filter { it.entry.operationType == OperationType.CREATE.name }
            val deleteOperations = sortedOperations.filter { it.entry.operationType == OperationType.DELETE.name }

            for (operation in createOperations) {
                val exists = if (userHasOperations) {
                    entryRepository.getEntryById(operation.entry.id) != null
                } else {
                    false
                }
                if (exists) entryRepository.update(operation) else entryRepository.insert(operation)
            }

            for (operation in deleteOperations) {
                entryRepository.delete(operation)
            }
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error executing operations", e)
            throw e
        }
    }
}
