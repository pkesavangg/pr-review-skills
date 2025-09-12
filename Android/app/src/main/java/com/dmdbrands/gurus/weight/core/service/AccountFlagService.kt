package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.AccountFlag
import com.dmdbrands.gurus.weight.domain.model.AppReview
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Service implementation for account flag operations.
 * Handles all account flag related business logic and app review flows.
 *
 * @property accountFlagRepository Repository for account flag data operations
 * @property appReviewManager Manager for app review flows
 */
@Singleton
class AccountFlagService
    @Inject
    constructor(
      @ApplicationContext val context: Context,
      private val accountFlagRepository: IAccountFlagRepository,
      private val appReviewManager: IAppReviewManager,
    ) : IAccountFlagService {

    // Current account flag
    private var firstFlag: AccountFlag? = null

    // App review flow
    private val _appReviewFlow = MutableSharedFlow<AppReview>()
    override val appReviewFlow: Flow<AppReview> = _appReviewFlow.asSharedFlow()

    /**
     * Gets the first account flag for the current user.
     * Login flags take precedence over entry flags.
     * @return The first account flag or null if none exists
     */
    override suspend fun getAccountFlag(): AccountFlag? =
        try {
            val flags = accountFlagRepository.getAccountFlags()
            if (flags.isNotEmpty()) {
                // Login flags should always take precedence over entry flags
                val loginFlag = flags.find { it.trigger == "login" }
                firstFlag = loginFlag ?: flags.first()
                AppLog.d("AccountFlagService", "Retrieved account flag: ${firstFlag?.type}")
            } else {
                firstFlag = null
                AppLog.d("AccountFlagService", "No account flags found")
            }
            firstFlag
        } catch (e: Exception) {
            AppLog.e("AccountFlagService", "Failed to get account flag", e.toString())
            firstFlag = null
            null
        }

    /**
     * Checks if an account flag should be triggered for the given trigger type.
     * @param trigger The trigger type to check (e.g., "login", "entry")
     * @return true if a flag was found and processed, false otherwise
     */
    override suspend fun checkAccountFlag(trigger: String): Boolean {
      return try {
        if (firstFlag == null) {
          AppLog.d("AccountFlagService", "No account flag available for trigger: $trigger")
          return false
        }

        val flag = firstFlag!!
        val flagType = flag.type.split(" ")[0]
        val flagTrigger = flag.trigger
        val flagId = flag.id

        if (trigger != flagTrigger) {
          AppLog.d("AccountFlagService", "Trigger mismatch: expected $flagTrigger, got $trigger")
          return false
        }

        when (flagType) {
          "app-rate-ask" -> {
            val wasDeleted = deleteFlag(flagId)
            if (wasDeleted) {
              // Trigger app review flow using AppReviewManager
              val shouldPrompt = appReviewManager.shouldPromptForReview()
              if (shouldPrompt) {
                launchAppReviewFlow()
                // Note: Activity will need to be provided by the caller
                // For now, we'll just log that the review should be triggered
                AppLog.d("AccountFlagService", "App review flow prepared, waiting for activity")
              }
              AppLog.d("AccountFlagService", "Triggered app rate review")
              return true
            }
          }

          else -> {
            AppLog.w("AccountFlagService", "Unknown flag type: $flagType")
          }
        }
        false
      } catch (e: Exception) {
        AppLog.e("AccountFlagService", "Failed to check account flag for trigger: $trigger", e.toString())
        false
      }
    }


    /**
     * Sets an app review request.
     * @param appReview The app review data to process
     */
    override fun setAppReview(appReview: AppReview) {
        AppLog.d("AccountFlagService", "Setting app review: ${appReview.screen}")
        _appReviewFlow.tryEmit(appReview)
    }

  override suspend fun launchAppReview() {
    try {
      val shouldPrompt = appReviewManager.shouldPromptForReview()
      if (shouldPrompt) {
        launchAppReviewFlow()
        // Note: Activity will need to be provided by the caller
        // For now, we'll just log that the review should be triggered
        AppLog.d("AccountFlagService", "App review flow prepared, waiting for activity from debug menu")
      }
    }
    catch (e: Exception){
      AppLog.e("AccountFlagService", "Failed to launch app review flow", e.toString())
    }

  }

  /**
     * Deletes an account flag by ID.
     * @param flagId The ID of the flag to delete
     * @return true if deletion was successful, false otherwise
     */
    override suspend fun deleteFlag(flagId: String): Boolean =
        try {
            val result = accountFlagRepository.deleteAccountFlag(flagId)
            if (result) {
                firstFlag = null
                AppLog.d("AccountFlagService", "Successfully deleted flag: $flagId")
            } else {
                AppLog.w("AccountFlagService", "Failed to delete flag: $flagId")
            }
            result
        } catch (e: Exception) {
            AppLog.e("AccountFlagService", "Exception while deleting flag: $flagId", e.toString())
            firstFlag = null
            false
        }

    /**
     * Launches the app review flow for the given activity.
     * This method should be called when an activity is available to show the review.
     * @return true if the review flow was launched successfully, false otherwise
     */
    suspend fun launchAppReviewFlow(): Boolean =
        try {
            appReviewManager.launchReviewFlow()
        } catch (e: Exception) {
            AppLog.e("AccountFlagService", "Failed to launch app review flow", e.toString())
            throw e
        }
}
