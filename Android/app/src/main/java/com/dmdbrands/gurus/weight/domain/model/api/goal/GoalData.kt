package com.dmdbrands.gurus.weight.domain.model.api.goal

/**
 * Request model for updating user goal settings.
 * Based on Angular Goal interface from goal.service.ts
 */
data class GoalData(
    /** The target weight for the goal */
    val goalWeight: Double,

    /** The initial weight when the goal was set */
    val initialWeight: Double,

    /** The type of goal: 'lose', 'gain', or 'maintain' */
    val type: String,

    /** Whether the previous goal was met (optional) */
    val metPreviousGoal: Boolean? = null
)
