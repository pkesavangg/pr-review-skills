package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.AccountFlag
import com.dmdbrands.gurus.weight.domain.model.AppReview
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for account flag operations.
 * Handles all account flag related business logic and app review flows.
 */
interface IAccountFlagService {
    /**
     * Gets the first account flag for the current user.
     * Login flags take precedence over entry flags.
     * @return The first account flag or null if none exists
     */
    suspend fun getAccountFlag(): AccountFlag?

    /**
     * Checks if an account flag should be triggered for the given trigger type.
     * @param trigger The trigger type to check (e.g., "login", "entry")
     * @return true if a flag was found and processed, false otherwise
     */
    suspend fun checkAccountFlag(trigger: String): Boolean

    /**
     * Sets an app review request.
     * @param appReview The app review data to process
     */
    fun setAppReview(appReview: AppReview)

    suspend fun launchAppReview()

    /**
     * Gets the current app review flow.
     * @return Flow of app review data
     */
    val appReviewFlow: Flow<AppReview>

    /**
     * Deletes an account flag by ID.
     * @param flagId The ID of the flag to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteFlag(flagId: String): Boolean
}
