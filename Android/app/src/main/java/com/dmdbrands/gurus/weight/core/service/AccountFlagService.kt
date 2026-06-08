package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.AccountFlag
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IReviewService
import dagger.hilt.android.qualifiers.ApplicationContext
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
      private val reviewService: IReviewService,
    ) : IAccountFlagService {

    // Current account flag
    private var firstFlag: AccountFlag? = null

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

        val flag = firstFlag ?: return false
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
              AppLog.d("AccountFlagService", "Trigger app rate review")
              return true
            }
          }
          "scale-review-ask" -> {
            // Post a dismissal via the unified /v3/review/ endpoint then clear the flag.
            // Actual star rating / feedback is collected by the review UI (not wired here yet);
            // this handles the "user dismissed without rating" path so the flag is cleared.
            try {
              val sku = (flag.data as? Map<*, *>)?.get("sku") as? String
              reviewService.submitReview(
                reviewType = "scale",
                status = "dismissed",
                sku = sku,
                flagId = flagId,
              )
              // submitReview already deletes the flag server-side; clear the in-memory cache
              // too (mirrors the app-rate-ask path via deleteFlag) so a subsequent
              // checkAccountFlag in this session does not re-process the dismissed flag.
              firstFlag = null
              AppLog.d("AccountFlagService", "scale-review-ask dismissed sku=$sku flagId=$flagId")
              return true
            } catch (e: Exception) {
              AppLog.e("AccountFlagService", "Failed to dismiss scale review", e.toString())
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
}
