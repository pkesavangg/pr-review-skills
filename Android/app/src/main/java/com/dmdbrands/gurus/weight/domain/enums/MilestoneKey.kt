package com.dmdbrands.gurus.weight.domain.enums

/**
 * Enum representing different milestone keys for dashboard milestones.
 * Replaces the proto MilestoneKey definition.
 */
enum class MilestoneKey {
    TO_GOAL,
    CURRENT_STREAK,
    LONGEST_STREAK,
    PER_WEEK,
    PER_MONTH,
    PER_YEAR,
    TOTAL_CHANGE;

    /**
     * Converts string to MilestoneKey enum.
     * @param value The string value to convert
     * @return The corresponding MilestoneKey or null if not found
     */
    companion object {
        fun fromString(value: String): MilestoneKey? {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Gets the default milestone keys.
         */
        fun getDefaultMilestones(): List<MilestoneKey> = listOf(
            TO_GOAL,
            CURRENT_STREAK,
            LONGEST_STREAK,
            PER_WEEK,
            PER_MONTH,
            PER_YEAR,
            TOTAL_CHANGE
        )

        /**
         * Gets all available milestone keys.
         */
        fun getAllMilestones(): List<MilestoneKey> = listOf(
            TO_GOAL,
            CURRENT_STREAK,
            LONGEST_STREAK,
            PER_WEEK,
            PER_MONTH,
            PER_YEAR,
            TOTAL_CHANGE
        )
    }
}
