package com.greatergoods.meapp.domain.model.api.goal

/**
 * Response model for goal API operations.
 * Represents the goal data returned from the server.
 */
data class GoalResponse(
    /** The target weight for the goal */
    val goalWeight: Double,

    /** The initial weight when the goal was set */
    val initialWeight: Double,

    /** The type of goal: 'lose', 'gain', or 'maintain' */
    val type: String,

    /** Legacy field for backward compatibility */
    val goalType: String?,

    /** Whether the previous goal was met */
    val metPreviousGoal: Boolean? = null,

    /** Timestamp when the goal was created */
    val createdAt: String? = null,

    /** Timestamp when the goal was last updated */
    val updatedAt: String? = null
)
