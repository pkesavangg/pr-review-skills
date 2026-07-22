package com.dmdbrands.gurus.weight.data.repository.account

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.ProductSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.StreaksSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.enums.ProgressKeyConstants
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.model.api.auth.LoginResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Local persistence data source for the account aggregate: it writes the account entity and all of
 * its related settings entities (weight-comp, notifications, streaks, weightless, goal, integrations,
 * dashboard, product) through [AccountDao].
 *
 * Extracted from `AccountRepository` (MOB-1499) so the repository clears the detekt `LargeClass`
 * limit. Method bodies are relocated verbatim — no behaviour change.
 */
class AccountPersistenceDataSource
@Inject
constructor(
  private val accountDao: AccountDao,
) {
  companion object {
    private const val TAG = "AccountRepository"
  }

  /**
   * Adds an account to the database with all entity relations and returns the domain model.
   * Inserts the main account entity and all related settings entities.
   */
  suspend fun addAccount(account: Account): Account {
    val accountEntity = AccountEntityMapper.toEntity(account)
    val existingAccount = accountDao.getAccountEntity(account.id)
    val isUpdate = existingAccount != null
    if (isUpdate) {
      // Account exists - update it instead of replacing to avoid CASCADE DELETE
      val updatedAccountEntity = accountEntity.copy(
        // Preserve any local-only fields if needed
      )
      accountDao.updateAccount(updatedAccountEntity)
    } else {
      // New account - safe to insert
      accountDao.insertAccount(accountEntity)
    }

    persistAccountCoreSettings(account, isUpdate)
    persistAccountFeatureSettings(account, isUpdate)

    AppLog.d(TAG, "Added account with all entity relations: ${account.id}")
    return account
  }

  /** Upserts the weight-comp / notification / streaks / weightless settings for [account]. */
  private suspend fun persistAccountCoreSettings(account: Account, isUpdate: Boolean) {
    // Insert WeightCompSettings entity with data from account
    val weightCompSettings =
      WeightCompSettingsEntity(
        accountId = account.id,
        height = account.height ?: 1700, // Default height if not set
        activityLevel = account.activityLevel ?: "normal", // Default activity level
        weightUnit = account.weightUnit.value, // Default weight unit
        isSynced = true, // New account data is already synced
      )
    if (isUpdate) {
      accountDao.updateWeightCompSettings(weightCompSettings)
    } else {
      accountDao.insertWeightCompSettings(weightCompSettings)
    }

    val notificationCompSettings =
      NotificationSettingsEntity(
        accountId = account.id,
        isSynced = true,
        shouldSendEntryNotifications = account.shouldSendEntryNotifications ?: false,
        shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications ?: false,
      )
    if (isUpdate) {
      accountDao.updateNotificationSettings(notificationCompSettings)
    } else {
      accountDao.insertNotificationSettings(notificationCompSettings)
    }

    // Insert StreaksSettings entity with data from account
    val streaksSettings =
      StreaksSettingsEntity(
        accountId = account.id,
        isStreakOn = account.isStreakOn ?: false,
        streakTimestamp = System.currentTimeMillis().toString(),
        isSynced = true,
      )
    if (isUpdate) {
      accountDao.updateStreaksSettings(streaksSettings)
    } else {
      accountDao.insertStreaksSettings(streaksSettings)
    }

    // Insert WeightlessSettings entity with data from account
    val weightlessSettings =
      WeightlessSettingsEntity(
        accountId = account.id,
        isWeightlessOn = account.isWeightlessOn ?: false,
        weightlessTimestamp = System.currentTimeMillis().toString(),
        weightlessWeight = account.weightlessWeight?.toFloat() ?: 0.0f,
        isSynced = true,
      )
    if (isUpdate) {
      accountDao.updateWeightlessSettings(weightlessSettings)
    } else {
      accountDao.insertWeightlessSettings(weightlessSettings)
    }
  }

  /** Upserts the goal / integrations / dashboard / product settings for [account]. */
  private suspend fun persistAccountFeatureSettings(account: Account, isUpdate: Boolean) {
    val goalEntity =
      GoalSettingsEntity(
        accountId = account.id,
        goalType = account.goalType,
        weight = account.initialWeight.toFloat(),
        goalWeight = account.goalWeight.toString(),
        goalPercent = account.goalPercent.toFloat(), // Will be calculated when needed
        isSynced = true,
      )
    if (isUpdate) {
      accountDao.updateGoalSettings(goalEntity)
    } else {
      accountDao.insertGoalSettings(goalEntity)
    }

    val integrationEntity = IntegrationsSettingsEntity(
      accountId = account.id,
      isSynced = true,
      isFitbitOn = account.isFitbitOn,
      isFitbitValid = account.isFitbitValid,
      isHealthConnectOn = account.isHealthConnectOn,
      isHealthKitOn = account.isHealthKitOn,
      isMFPOn = account.isMFPOn,
      isMFPValid = account.isMFPValid,
    )
    if (isUpdate) {
      accountDao.updateIntegrationsSettings(integrationEntity)
    } else {
      accountDao.insertIntegrationsSettings(integrationEntity)
    }
    val dashboardSettings =
      DashboardSettingsEntity(
        accountId = account.id,
        dashboardMetrics = account.dashboardMetrics ?: MetricKeyConstants.DEFAULT_4_METRICS,
        dashboardMilestones = account.progressMetrics ?: MilestoneKey.getDefaultMilestones().map { ProgressKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase() },
        dashboardType = account.dashboardType ?: DashboardType.DASHBOARD_4_METRICS.name,
        isSynced = true,
      )
    if (isUpdate) {
      accountDao.updateDashboardSettings(dashboardSettings)
    } else {
      accountDao.insertDashboardSettings(dashboardSettings)
    }

    // Product settings (Phase 2 / MOB-377)
    val productSettings = ProductSettingsEntity(
      accountId = account.id,
      productTypes = account.productTypes,
      measurementUnits = account.measurementUnits.value,
      isSynced = true,
    )
    if (isUpdate) {
      accountDao.updateProductSettings(productSettings)
    } else {
      accountDao.insertProductSettings(productSettings)
    }
  }

  /**
   * Builds a domain [Account] from a [LoginResponse] and persists it via [addAccount].
   */
  suspend fun addAccountFromResponse(loginResponse: LoginResponse): Account {
    val account = loginResponse.account
    val userAccount =
      Account(
        id = account.id,
        firstName = account.firstName,
        lastName = account.lastName,
        dob = account.dob,
        email = account.email,
        expiresAt = loginResponse.expiresAt,
        fcmToken = null,
        gender = account.gender.orEmpty(),
        isActiveAccount = true,
        isLoggedIn = true,
        isExpired = false,
        isSynced = false,
        lastActiveTime = System.currentTimeMillis().toString(),
        zipcode = account.zipcode,
        weightUnit = WeightUnit.from(account.weightUnit),
        isWeightlessOn = account.isWeightlessOn,
        height = account.height,
        activityLevel = account.activityLevel,
        weightlessTimestamp = account.weightlessTimestamp,
        weightlessWeight = account.weightlessWeight,
        isStreakOn = account.isStreakOn,
        dashboardType = account.dashboardType,
        dashboardMetrics = account.dashboardMetrics,
        progressMetrics = account.progressMetrics,
        shouldSendEntryNotifications = account.shouldSendEntryNotifications,
        shouldSendWeightInEntryNotifications = account.shouldSendWeightInEntryNotifications,
        goalType = account.goalType,
        goalWeight = account.goalWeight?.toDouble(),
        initialWeight = account.initialWeight?.toDouble() ?: 0.0,
        metPreviousGoal = account.metPreviousGoal,
        goalPercent = account.goalPercent.toDouble(),
        // Phase 2 (MOB-377): account product list + measurement system (defaults if pre-Phase-2 response).
        productTypes = account.productTypes ?: listOf(ProductType.MY_WEIGHT.apiValue),
        measurementUnits = MeasurementUnits.fromValue(account.measurementUnits),
      )
    return addAccount(userAccount)
  }

  /**
   * Updates an account in the database with partial data.
   * Only the fields provided in partialUpdate will be updated, others will remain unchanged.
   * @param accountId The ID of the account to update
   * @param partialUpdate Partial account data to update
   */
  suspend fun updateAccount(
    accountId: String,
    partialUpdate: PartialAccount,
  ) {
    // Get current account entity from database by ID
    val accountEntity =
      accountDao.getAccountEntity(accountId)
        ?: throw IllegalStateException("Account not found for ID: $accountId")

    // Merge current account with partial update
    val updatedAccountEntity =
      accountEntity.copy(
        firstName = partialUpdate.firstName ?: accountEntity.firstName,
        lastName = partialUpdate.lastName ?: accountEntity.lastName,
        dob = partialUpdate.dob ?: accountEntity.dob,
        email = partialUpdate.email ?: accountEntity.email,
        expiresAt = partialUpdate.expiresAt ?: accountEntity.expiresAt,
        fcmToken = partialUpdate.fcmToken ?: accountEntity.fcmToken,
        gender = partialUpdate.gender ?: accountEntity.gender,
        isActiveAccount = partialUpdate.isActiveAccount ?: accountEntity.isActiveAccount,
        isLoggedIn = partialUpdate.isLoggedIn ?: accountEntity.isLoggedIn,
        isExpired = partialUpdate.isExpired ?: accountEntity.isExpired,
        isSynced = partialUpdate.isSynced ?: accountEntity.isSynced,
        lastActiveTime = partialUpdate.lastActiveTime ?: accountEntity.lastActiveTime,
        zipcode = partialUpdate.zipcode ?: accountEntity.zipcode,
      )

    // Update account entity in database
    accountDao.updateAccount(updatedAccountEntity)
  }

  suspend fun updateAccountInfo(
    accountId: String,
    accountInfo: AccountInfo,
  ) {
    try {
      // Use updateAccount with only the profile fields from API response
      val partialUpdate =
        PartialAccount(
          firstName = accountInfo.firstName,
          lastName = accountInfo.lastName,
          email = accountInfo.email,
          dob = accountInfo.dob,
          gender = accountInfo.gender,
          zipcode = accountInfo.zipcode,
        )
      updateAccount(accountId, partialUpdate)
      AppLog.d(TAG, "Updated account $accountId with API response data")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update account $accountId with API response data", e)
    }
  }

  /**
   * Updates only the dashboard type while preserving existing metrics and milestones.
   * @param accountId The account ID
   * @param dashboardType The new dashboard type to set
   */
  suspend fun updateLocalDashboardType(accountId: String, dashboardType: DashboardType) {
    // Get existing settings to preserve metrics and milestones
    val existingSettings = accountDao.getDashboardSettings(accountId).first()

    val settings = DashboardSettingsEntity(
      accountId = accountId,
      dashboardMetrics = existingSettings?.dashboardMetrics ?: emptyList(),
      dashboardMilestones = existingSettings?.dashboardMilestones ?: emptyList(),
      dashboardType = dashboardType.value,
      isSynced = true,
    )
    accountDao.insertDashboardSettings(settings)
  }

  /**
   * Updates dashboard settings including metrics and milestones for the given account.
   * @param accountId The account ID
   * @param dashboardMetrics List of metric keys
   * @param dashboardMilestones List of milestone keys
   * @param dashboardType The dashboard type
   */
  suspend fun updateDashboardSettings(
    accountId: String,
    dashboardMetrics: List<String>,
    dashboardMilestones: List<String>,
    dashboardType: DashboardType,
    isSynced: Boolean
  ) {
    val settings = DashboardSettingsEntity(
      accountId = accountId,
      dashboardMetrics = dashboardMetrics,
      dashboardMilestones = dashboardMilestones,
      dashboardType = dashboardType.value,
      isSynced = isSynced,
    )
    accountDao.insertDashboardSettings(settings)
  }

  /**
   * Syncs all account settings with server data.
   * Updates local database with the latest settings from server.
   * Follows the same pattern as addAccountFromResponse during login.
   * @param accountInfo The account info from server or local account containing latest settings
   * @param isOnline Whether the sync is from online API call (true) or offline local data (false)
   */
  suspend fun syncAccountSettingsWithServer(accountInfo: AccountInfo, isOnline: Boolean) {
    AppLog.d(TAG, "Syncing all settings for account: ${accountInfo.id}, isOnline: $isOnline")

    try {
      // Update basic account info first
      updateAccountInfo(accountInfo.id, accountInfo)

      // Update weightless settings
      val weightlessSetting = WeightlessSettingsEntity(
        accountId = accountInfo.id,
        isWeightlessOn = accountInfo.isWeightlessOn,
        weightlessTimestamp = accountInfo.weightlessTimestamp ?: "0",
        weightlessWeight = accountInfo.weightlessWeight ?: 0.0f,
        isSynced = true,
      )
      accountDao.updateWeightlessSettings(weightlessSetting)

      // Update weight composition settings
      val weightCompSettings = WeightCompSettingsEntity(
        accountId = accountInfo.id,
        height = accountInfo.height ?: BodyCompUpdateRequest.DEFAULT_HEIGHT,
        activityLevel = accountInfo.activityLevel ?: BodyCompUpdateRequest.DEFAULT_ACTIVITY_LEVEL,
        weightUnit = accountInfo.weightUnit,
        isSynced = true,
      )
      accountDao.updateWeightCompSettings(weightCompSettings)

      // Update goal settings
      val goalSettings = GoalSettingsEntity(
        accountId = accountInfo.id,
        goalType = accountInfo.goalType,
        weight = accountInfo.initialWeight ?: 0.0f,
        goalWeight = accountInfo.goalWeight?.toString() ?: "0",
        goalPercent = accountInfo.goalPercent.toFloat(),
        isSynced = true,
      )
      accountDao.updateGoalSettings(goalSettings)

      // Update notification settings
      val notificationSettings = NotificationSettingsEntity(
        accountId = accountInfo.id,
        shouldSendEntryNotifications = accountInfo.shouldSendEntryNotifications,
        shouldSendWeightInEntryNotifications = accountInfo.shouldSendWeightInEntryNotifications,
        isSynced = true,
      )
      accountDao.updateNotificationSettings(notificationSettings)

      // Update integration settings
      val integrationsSettings = IntegrationsSettingsEntity(
        accountId = accountInfo.id,
        isFitbitOn = accountInfo.isFitbitOn,
        isFitbitValid = accountInfo.isFitbitValid,
        isHealthConnectOn = accountInfo.isHealthConnectOn,
        isHealthKitOn = accountInfo.isHealthKitOn,
        isMFPOn = accountInfo.isMFPOn,
        isMFPValid = accountInfo.isMFPValid,
        isSynced = true,
      )
      accountDao.updateIntegrationsSettings(integrationsSettings)

      // Update product settings (Phase 2 / MOB-377).
      upsertProductSettings(accountInfo)

      // Mark account as synced only when online (from server)
      if (isOnline) {
        accountDao.markAccountSynced(accountInfo.id)
        AppLog.d(TAG, "Marked account as synced: ${accountInfo.id}")
      }
      AppLog.d(TAG, "Successfully synced all settings for account: ${accountInfo.id}")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to sync settings for account: ${accountInfo.id}", e)
      throw e
    }
  }

  /**
   * Upserts the account's product settings (productTypes + measurementUnits). Insert(REPLACE)
   * upserts in case the row predates this account's product_settings backfill. MOB-377 / §2.19.
   */
  private suspend fun upsertProductSettings(accountInfo: AccountInfo) {
    val productSettings = ProductSettingsEntity(
      accountId = accountInfo.id,
      productTypes = accountInfo.productTypes ?: listOf(ProductType.MY_WEIGHT.apiValue),
      measurementUnits = MeasurementUnits.fromValue(accountInfo.measurementUnits).value,
      isSynced = true,
    )
    accountDao.insertProductSettings(productSettings)
  }
}
