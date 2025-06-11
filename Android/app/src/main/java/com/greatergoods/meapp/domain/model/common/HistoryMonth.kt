package com.greatergoods.meapp.domain.model.common

/**
 * Data class representing monthly aggregated history data.
 * Used for displaying monthly statistics and trends.
 */
data class HistoryMonth(
    val id: Long = 0,
    val entryTimestamp: String? = null,
    val weight: Double? = null,
    val bodyFat: Double? = null,
    val muscleMass: Double? = null,
    val water: Double? = null,
    val bmi: Double? = null,
    val change: String? = null,
    val count: Int? = null,
    val date: String? = null,
    val time: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val month: String? = null,
    val year: String? = null,
    val weights: String? = null
) {
    /**
     * Calculates the change in weight for this month.
     * @return The calculated change as a string
     */
    fun calculateChange(): String {
        if (weights.isNullOrEmpty()) return "--"

        var minTS = "Z"
        var maxTS = "0"
        var firstWeight = 0.0
        var lastWeight = 0.0

        weights.split(",").forEach { entry ->
            val (weight, entryTimestamp) = entry.split("|")
            if (entryTimestamp > maxTS) {
                maxTS = entryTimestamp
                lastWeight = weight.toDouble()
            }
            if (entryTimestamp < minTS) {
                minTS = entryTimestamp
                firstWeight = weight.toDouble()
            }
        }

        return String.format("%.1f", lastWeight - firstWeight)
    }

    companion object {
        /**
         * Creates a HistoryMonth from a database entity.
         * @param entity The database entity
         * @return A new HistoryMonth instance
         */
        fun fromEntity(entity: Any): HistoryMonth {
            // TODO: Implement conversion from database entity
            return HistoryMonth()
        }
    }
}
