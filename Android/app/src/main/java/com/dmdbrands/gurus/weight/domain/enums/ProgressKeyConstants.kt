package com.dmdbrands.gurus.weight.domain.enums

/**
 * Constants for progress metric keys in camelCase format.
 * These are the standardized keys used for database and server communication.
 */
object ProgressKeyConstants {
    // Progress metrics
    const val GOAL = "goal"
    const val CURRENT_STREAK = "currentStreak"
    const val LONGEST_STREAK = "longestStreak"
    const val WEEKLY_CHANGE = "weeklyChange"
    const val MONTHLY_CHANGE = "monthlyChange"
    const val YEARLY_CHANGE = "yearlyChange"
    const val TOTAL_CHANGE = "totalChange"

    /**
     * All progress metric keys in camelCase format.
     */
    val ALL_PROGRESS_KEYS = listOf(
        GOAL,
        CURRENT_STREAK,
        LONGEST_STREAK,
        WEEKLY_CHANGE,
        MONTHLY_CHANGE,
        YEARLY_CHANGE,
        TOTAL_CHANGE
    )

    /**
     * Maps camelCase keys to MilestoneKey enums.
     */
    val CAMEL_CASE_TO_ENUM = mapOf(
        GOAL to MilestoneKey.TO_GOAL,
        CURRENT_STREAK to MilestoneKey.CURRENT_STREAK,
        LONGEST_STREAK to MilestoneKey.LONGEST_STREAK,
        WEEKLY_CHANGE to MilestoneKey.PER_WEEK,
        MONTHLY_CHANGE to MilestoneKey.PER_MONTH,
        YEARLY_CHANGE to MilestoneKey.PER_YEAR,
        TOTAL_CHANGE to MilestoneKey.TOTAL_CHANGE
    )

    /**
     * Maps MilestoneKey enums to camelCase keys.
     */
    val ENUM_TO_CAMEL_CASE = CAMEL_CASE_TO_ENUM.entries.associate { (key, value) -> value to key }
}

