package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.goal.GoalData
import com.greatergoods.meapp.domain.model.api.goal.GoalResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API interface for goal endpoints.
 * Based on Angular goal.service.ts API operations.
 */
interface IGoalAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val GOAL = "goal/"
    }

    /**
     * Updates the goal for the current user.
     * @param request The goal request containing goalWeight, initialWeight, type, etc.
     * @return GoalResponse with updated goal data
     */
    @POST(ACCOUNT + GOAL)
    suspend fun updateGoal(
        @Body request: GoalData,
    ): GoalResponse
}
