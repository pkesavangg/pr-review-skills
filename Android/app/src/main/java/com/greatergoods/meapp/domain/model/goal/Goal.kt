package com.greatergoods.meapp.domain.model.goal

/**
 * Domain model representing a user's weight goal.
 * Based on Angular Goal interface from goal.service.ts
 */
data class Goal(
    /** The target weight for the goal */
    val goalWeight: Double,

    /** The initial weight when the goal was set */
    val initialWeight: Double,

    /** The type of goal: 'lose', 'gain', or 'maintain' */
    val type: String,

    /** Legacy field for backward compatibility */
    val goalType: String = type,

    /** Whether the previous goal was met */
    val metPreviousGoal: Boolean? = null,

    /** Percentage completion of the goal (calculated based on current weight) */
    val percent: Int? = null
)
