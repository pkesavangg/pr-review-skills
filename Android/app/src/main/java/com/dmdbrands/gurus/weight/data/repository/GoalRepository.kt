package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IGoalAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Repository implementation for goal operations.
 * Handles goal settings for user accounts.
 */
@Singleton
class GoalRepository
@Inject
constructor(
  private val goalAPI: IGoalAPI,
  private val accountDao: AccountDao,
  private val accountRepository: IAccountRepository,
) : IGoalRepository {
  private val TAG = "GoalRepository"

  override suspend fun updateGoalSettingsInDB(goalSettings: GoalSettingsEntity) {
    // Update local database
    val activeAccount = accountDao.getActiveAccount().first()
    activeAccount?.let { account ->
      val goalEntity =
        GoalSettingsEntity(
          accountId = activeAccount.account.id,
          goalType = goalSettings.goalType,
          weight = goalSettings.weight,
          goalWeight = goalSettings.goalWeight,
          goalPercent = goalSettings.goalPercent, // Will be calculated when needed
          isSynced = goalSettings.isSynced,
        )
      accountDao.updateGoalSettings(goalEntity)
      account
    }
  }

  /**
   * Updates the goal setting for the active account.
   * @param goalData The goal setting to update
   * @return Updated account with new goal settings
   */
  override suspend fun updateGoalSetting(goalData: GoalData): Account? =
    try {
      AppLog.d(TAG, "Updating goal setting online")

      // Call API to update goal
      val response = goalAPI.updateGoal(goalData)

      // Update local database with synced status
      val activeAccount = accountRepository.getActiveAccount().first()
      activeAccount?.let { account ->
        val goalEntity =
          GoalSettingsEntity(
            accountId = account.id,
            goalType = response.type,
            weight = response.initialWeight.toFloat(),
            goalWeight = response.goalWeight.toString(),
            goalPercent = 0f, // Will be calculated when needed
            isSynced = true,
          )
        updateGoalSettingsInDB(goalEntity)
        // Return the updated account
        account
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update goal setting online", e)
      throw e
    }

  /**
   * Updates goal setting offline (stores locally for later sync).
   * Used when network is unavailable.
   * @param request The goal setting request
   * @return Updated account with new goal settings
   */
  override suspend fun updateGoalSettingOffline(request: GoalData): Account? =
    try {
      AppLog.d(TAG, "Storing goal setting for offline sync")

      val activeAccount = accountRepository.getActiveAccount().first()
      activeAccount?.let { account ->
        val goalEntity =
          GoalSettingsEntity(
            accountId = account.id,
            goalType = request.type,
            weight = request.initialWeight.toFloat(),
            goalWeight = request.goalWeight.toString(),
            goalPercent = 0f, // Will be calculated when needed
            isSynced = false, // Mark as unsynced for later upload
          )
        updateGoalSettingsInDB(goalEntity)
        account
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to store goal setting offline", e)
      throw e
    }

  /**
   * Gets the current goal for the active account.
   * @return Current goal or null if no goal is set
   */
  override fun getCurrentGoal(): Flow<Goal?> {
    return accountRepository.getActiveAccount()
      .map { activeAccount ->
        if (activeAccount == null) {
          AppLog.w(TAG, "No active account found")
          return@map null
        }

        Goal(
          goalWeight = activeAccount.goalWeight ?: 0.0,
          initialWeight = activeAccount.initialWeight,
          type = activeAccount.goalType ?: GoalType.LOSE_GAIN.value,
          goalType = activeAccount.goalType ?: GoalType.LOSE_GAIN.value,
          percent = activeAccount.goalPercent,
          metPreviousGoal = activeAccount.metPreviousGoal ?: false,
        )
      }
      .catch { e ->
        AppLog.e(TAG, "Failed to get current goal", e)
        emit(null)
      }
  }

  /**
   * Calculates goal completion percentage based on current weight.
   * @param goal The goal to calculate percentage for
   * @param currentWeight The current weight to calculate against
   * @return Percentage completion (0-100) or null if calculation not possible
   */
  override fun calculateGoalPercent(
    goal: Goal,
    currentWeight: Double,
  ): Int? {
    val goalWeight = goal.goalWeight
    val initialWeight = goal.initialWeight

    return when (goal.type.lowercase()) {
      "lose" -> {
        val percent = (currentWeight - goalWeight) / (initialWeight - goalWeight)
        val result = 100 - floor(percent * 100).toInt()
        if (result < 0) 0 else result
      }

      "gain" -> {
        val percent = (currentWeight - initialWeight) / (goalWeight - initialWeight)
        val result = floor(percent * 100).toInt()
        if (result < 0) 0 else result
      }

      else -> null // Maintain goals don't have percentage
    }
  }

  /**
   * Gets the active account if it has unsynced goal settings.
   */
  override suspend fun getUnsyncedActiveGoalAccountFromDB(): Account? {
    val unsyncedActiveAccount = accountDao.getUnsyncedActiveGoalAccount().first()
    return unsyncedActiveAccount?.let {
      AccountEntityMapper.toDomainFromAccountWithRelations(it)
    }
  }
}
