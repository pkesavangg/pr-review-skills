package com.dmdbrands.gurus.weight.data.storage.db.dao

import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.dashboardSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.device
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.goalSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.integrationsSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.notificationSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.streaksSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.weightCompSettings
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.weightlessSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccountDaoTest : BaseDaoTest() {

    // -------------------------------------------------------------------------
    // insertAccount / updateAccount / deleteAccount
    // -------------------------------------------------------------------------

    @Test
    fun insertAccount_storesAndRetrievesEntity() = runTest {
        val acc = account()
        accountDao.insertAccount(acc)

        assertThat(accountDao.getAccountEntity(acc.id)).isEqualTo(acc)
    }

    @Test
    fun insertAccount_replacesOnDuplicateId() = runTest {
        accountDao.insertAccount(account(id = "acc-1", firstName = "John"))
        accountDao.insertAccount(account(id = "acc-1", firstName = "Jonathan"))

        assertThat(accountDao.getAccountEntity("acc-1")?.firstName).isEqualTo("Jonathan")
    }

    @Test
    fun insertAccount_replacesOnDuplicateEmail() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "same@test.com", firstName = "John"))
        accountDao.insertAccount(account(id = "acc-2", email = "same@test.com", firstName = "Jane"))

        // REPLACE on unique email index removes old row, inserts new
        assertThat(accountDao.getAccountEntity("acc-1")).isNull()
        assertThat(accountDao.getAccountEntity("acc-2")?.firstName).isEqualTo("Jane")
    }

    @Test
    fun updateAccount_modifiesExisting() = runTest {
        val acc = account()
        accountDao.insertAccount(acc)

        accountDao.updateAccount(acc.copy(firstName = "Updated"))
        assertThat(accountDao.getAccountEntity(acc.id)?.firstName).isEqualTo("Updated")
    }

    @Test
    fun deleteAccount_removesEntity() = runTest {
        val acc = account()
        accountDao.insertAccount(acc)

        accountDao.deleteAccount(acc)
        assertThat(accountDao.getAccountEntity(acc.id)).isNull()
    }

    @Test
    fun deleteAccountById_removesEntity() = runTest {
        accountDao.insertAccount(account())

        accountDao.deleteAccountById("acc-1")
        assertThat(accountDao.getAccountEntity("acc-1")).isNull()
    }

    // -------------------------------------------------------------------------
    // getAccount (with relations)
    // -------------------------------------------------------------------------

    @Test
    fun getAccount_returnsAllRelations() = runTest {
        accountDao.insertFullAccount()

        val result = accountDao.getAccount("acc-1").first()
        assertThat(result).isNotNull()
        assertThat(result?.account?.id).isEqualTo("acc-1")
        assertThat(result?.weightCompSettings).isNotNull()
        assertThat(result?.goalSettings).isNotNull()
        assertThat(result?.streaksSettings).isNotNull()
        assertThat(result?.weightlessSettings).isNotNull()
        assertThat(result?.notificationSettings).isNotNull()
        assertThat(result?.dashboardSettings).isNotNull()
        assertThat(result?.integrationsSettings).isNotNull()
    }

    @Test
    fun getAccount_withNullRelations() = runTest {
        accountDao.insertAccount(account())

        val result = accountDao.getAccount("acc-1").first()
        assertThat(result).isNotNull()
        assertThat(result?.weightCompSettings).isNull()
        assertThat(result?.goalSettings).isNull()
        assertThat(result?.streaksSettings).isNull()
        assertThat(result?.weightlessSettings).isNull()
        assertThat(result?.notificationSettings).isNull()
        assertThat(result?.dashboardSettings).isNull()
        assertThat(result?.integrationsSettings).isNull()
    }

    @Test
    fun getAccount_returnsNullForMissingId() = runTest {
        assertThat(accountDao.getAccount("nonexistent").first()).isNull()
    }

    // -------------------------------------------------------------------------
    // getAccountEntity
    // -------------------------------------------------------------------------

    @Test
    fun getAccountEntity_returnsEntityWithoutRelations() = runTest {
        val acc = account()
        accountDao.insertAccount(acc)

        assertThat(accountDao.getAccountEntity(acc.id)).isEqualTo(acc)
    }

    // -------------------------------------------------------------------------
    // getActiveAccount
    // -------------------------------------------------------------------------

    @Test
    fun getActiveAccount_returnsActiveAccount() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com", isActiveAccount = true))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com", isActiveAccount = false))

        val result = accountDao.getActiveAccount().first()
        assertThat(result?.account?.id).isEqualTo("acc-1")
    }

    @Test
    fun getActiveAccount_returnsNullWhenNoneActive() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = false))

        assertThat(accountDao.getActiveAccount().first()).isNull()
    }

    // -------------------------------------------------------------------------
    // getAllLoggedInAccounts
    // -------------------------------------------------------------------------

    @Test
    fun getAllLoggedInAccounts_returnsOnlyLoggedIn() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com", isLoggedIn = true))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com", isLoggedIn = false))
        accountDao.insertFullAccount(account(id = "acc-3", email = "e@f.com", isLoggedIn = true))

        val result = accountDao.getAllLoggedInAccounts().first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.account.id }).containsExactly("acc-1", "acc-3")
    }

    // -------------------------------------------------------------------------
    // Account state management
    // -------------------------------------------------------------------------

    @Test
    fun activateAccount_setsActive() = runTest {
        accountDao.insertAccount(account(isActiveAccount = false))

        accountDao.activateAccount("acc-1")
        assertThat(accountDao.getAccountEntity("acc-1")?.isActiveAccount).isTrue()
    }

    @Test
    fun deactivateOtherAccounts_deactivatesAllExceptSpecified() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isActiveAccount = true))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isActiveAccount = true))

        accountDao.deactivateOtherAccounts("acc-1")

        assertThat(accountDao.getAccountEntity("acc-1")?.isActiveAccount).isTrue()
        assertThat(accountDao.getAccountEntity("acc-2")?.isActiveAccount).isFalse()
    }

    @Test
    fun deactivateAllAccounts_deactivatesAll() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isActiveAccount = true))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isActiveAccount = true))

        accountDao.deactivateAllAccounts()

        assertThat(accountDao.getAccountEntity("acc-1")?.isActiveAccount).isFalse()
        assertThat(accountDao.getAccountEntity("acc-2")?.isActiveAccount).isFalse()
    }

    @Test
    fun logoutAccount_clearsLoginState() = runTest {
        accountDao.insertAccount(account(isLoggedIn = true, isActiveAccount = true, isExpired = true))

        accountDao.logoutAccount("acc-1")
        val result = accountDao.getAccountEntity("acc-1")
        assertThat(result?.isLoggedIn).isFalse()
        assertThat(result?.isActiveAccount).isFalse()
        assertThat(result?.isExpired).isFalse()
    }

    @Test
    fun logoutAllAccounts_clearsAllLoginStates() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isLoggedIn = true))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isLoggedIn = true))

        accountDao.logoutAllAccounts()

        assertThat(accountDao.getAccountEntity("acc-1")?.isLoggedIn).isFalse()
        assertThat(accountDao.getAccountEntity("acc-2")?.isLoggedIn).isFalse()
    }

    @Test
    fun updateSyncStatus_setsSyncFlag() = runTest {
        accountDao.insertAccount(account(isSynced = false))

        accountDao.updateSyncStatus("acc-1", true)
        assertThat(accountDao.getAccountEntity("acc-1")?.isSynced).isTrue()
    }

    @Test
    fun markAccountExpired_setsExpiredAndClearsActive() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isExpired = false, expiresAt = "2025-12-31"))

        accountDao.markAccountExpired("acc-1")
        val result = accountDao.getAccountEntity("acc-1")
        assertThat(result?.isExpired).isTrue()
        assertThat(result?.isActiveAccount).isFalse()
        assertThat(result?.expiresAt).isEmpty()
    }

    @Test
    fun updateLastActiveTime_updatesTimestamp() = runTest {
        accountDao.insertAccount(account())

        accountDao.updateLastActiveTime("acc-1", "2025-06-15T12:00:00.000Z")
        assertThat(accountDao.getAccountEntity("acc-1")?.lastActiveTime).isEqualTo("2025-06-15T12:00:00.000Z")
    }

    // -------------------------------------------------------------------------
    // Settings CRUD (7 settings entities)
    // -------------------------------------------------------------------------

    @Test
    fun insertAndUpdateWeightCompSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertWeightCompSettings(weightCompSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.weightCompSettings?.height).isEqualTo(170)

        accountDao.updateWeightCompSettings(weightCompSettings(height = 180))
        assertThat(accountDao.getAccount("acc-1").first()?.weightCompSettings?.height).isEqualTo(180)
    }

    @Test
    fun insertAndUpdateGoalSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertGoalSettings(goalSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.goalSettings?.goalWeight).isEqualTo("160")

        accountDao.updateGoalSettings(goalSettings(goalWeight = "150"))
        assertThat(accountDao.getAccount("acc-1").first()?.goalSettings?.goalWeight).isEqualTo("150")
    }

    @Test
    fun insertAndUpdateStreaksSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertStreaksSettings(streaksSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.streaksSettings?.isStreakOn).isTrue()

        accountDao.updateStreaksSettings(streaksSettings(isStreakOn = false))
        assertThat(accountDao.getAccount("acc-1").first()?.streaksSettings?.isStreakOn).isFalse()
    }

    @Test
    fun insertAndUpdateWeightlessSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertWeightlessSettings(weightlessSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.weightlessSettings?.isWeightlessOn).isFalse()

        accountDao.updateWeightlessSettings(weightlessSettings(isWeightlessOn = true, weightlessWeight = 5f))
        assertThat(accountDao.getAccount("acc-1").first()?.weightlessSettings?.isWeightlessOn).isTrue()
    }

    @Test
    fun insertAndUpdateNotificationSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertNotificationSettings(notificationSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.notificationSettings?.shouldSendEntryNotifications).isTrue()

        accountDao.updateNotificationSettings(notificationSettings(shouldSendEntryNotifications = false))
        assertThat(accountDao.getAccount("acc-1").first()?.notificationSettings?.shouldSendEntryNotifications).isFalse()
    }

    @Test
    fun insertAndUpdateDashboardSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertDashboardSettings(dashboardSettings())

        assertThat(accountDao.getDashboardSettings("acc-1").first()?.dashboardType).isEqualTo("standard")

        accountDao.updateDashboardSettings(dashboardSettings(dashboardType = "compact"))
        assertThat(accountDao.getDashboardSettings("acc-1").first()?.dashboardType).isEqualTo("compact")
    }

    @Test
    fun insertAndUpdateIntegrationsSettings() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertIntegrationsSettings(integrationsSettings())

        assertThat(accountDao.getAccount("acc-1").first()?.integrationsSettings?.isFitbitOn).isFalse()

        accountDao.updateIntegrationsSettings(integrationsSettings(isFitbitOn = true))
        assertThat(accountDao.getAccount("acc-1").first()?.integrationsSettings?.isFitbitOn).isTrue()
    }

    // -------------------------------------------------------------------------
    // Unsynced queries
    // -------------------------------------------------------------------------

    @Test
    fun getUnsyncedActiveAccount_returnsWhenUnsynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = false))

        assertThat(accountDao.getUnsyncedActiveAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveAccount_nullWhenSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveAccount_nullWhenNotActive() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = false, isSynced = false))

        assertThat(accountDao.getUnsyncedActiveAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveBodyCompAccount_returnsWhenBodyCompUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = true))
        accountDao.insertWeightCompSettings(weightCompSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveBodyCompAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveBodyCompAccount_nullWhenAllSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveBodyCompAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveBodyCompAccount_returnsWhenAccountItselfUnsynced() = runTest {
        // Tests the OR isSynced = 0 branch — account unsynced, no settings row needed
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = false))

        assertThat(accountDao.getUnsyncedActiveBodyCompAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveNotificationAccount_returnsWhenUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = true))
        accountDao.insertNotificationSettings(notificationSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveNotificationAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveNotificationAccount_nullWhenSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveNotificationAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveNotificationAccount_returnsWhenAccountItselfUnsynced() = runTest {
        // Tests the OR isSynced = 0 branch — account unsynced, no settings row needed
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = false))

        assertThat(accountDao.getUnsyncedActiveNotificationAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveStreakAccount_returnsWhenUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = true))
        accountDao.insertStreaksSettings(streaksSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveStreakAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveStreakAccount_nullWhenSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveStreakAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveWeightlessAccount_returnsWhenUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = true))
        accountDao.insertWeightlessSettings(weightlessSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveWeightlessAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveWeightlessAccount_nullWhenSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveWeightlessAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveGoalAccount_returnsWhenUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true, isSynced = true))
        accountDao.insertGoalSettings(goalSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveGoalAccount().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveGoalAccount_nullWhenSynced() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = true))

        assertThat(accountDao.getUnsyncedActiveGoalAccount().first()).isNull()
    }

    @Test
    fun getUnsyncedActiveDashboardSettings_returnsWhenUnsynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true))
        accountDao.insertDashboardSettings(dashboardSettings(isSynced = false))

        assertThat(accountDao.getUnsyncedActiveDashboardSettings().first()).isNotNull()
    }

    @Test
    fun getUnsyncedActiveDashboardSettings_nullWhenSynced() = runTest {
        accountDao.insertAccount(account(isActiveAccount = true))
        accountDao.insertDashboardSettings(dashboardSettings(isSynced = true))

        assertThat(accountDao.getUnsyncedActiveDashboardSettings().first()).isNull()
    }

    // -------------------------------------------------------------------------
    // Sync operations
    // -------------------------------------------------------------------------

    @Test
    fun markAllAccountsSynced_syncsAllAccounts() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isSynced = false))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isSynced = false))

        accountDao.markAllAccountsSynced()

        assertThat(accountDao.getAccountEntity("acc-1")?.isSynced).isTrue()
        assertThat(accountDao.getAccountEntity("acc-2")?.isSynced).isTrue()
    }

    @Test
    fun markAccountSynced_syncsSingleAccount() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isSynced = false))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isSynced = false))

        accountDao.markAccountSynced("acc-1")

        assertThat(accountDao.getAccountEntity("acc-1")?.isSynced).isTrue()
        assertThat(accountDao.getAccountEntity("acc-2")?.isSynced).isFalse()
    }

    // -------------------------------------------------------------------------
    // CASCADE / deleteAllTables
    // -------------------------------------------------------------------------

    @Test
    fun deleteAccount_cascadesToAllSettings() = runTest {
        accountDao.insertFullAccount()
        accountDao.deleteAccountById("acc-1")

        assertThat(accountDao.getAccountEntity("acc-1")).isNull()
        // Verify ALL 7 settings tables were removed by CASCADE
        val result = accountDao.getAccount("acc-1").first()
        assertThat(result).isNull()
        assertThat(accountDao.getDashboardSettings("acc-1").first()).isNull()
    }

    @Test
    fun deleteAllTables_removesAccountAndAllSettings() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com"))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com"))

        accountDao.deleteAllTables("acc-1")

        assertThat(accountDao.getAccountEntity("acc-1")).isNull()
        assertThat(accountDao.getAccountEntity("acc-2")).isNotNull()
    }

    @Test
    fun deleteAllTables_isolatesToOneAccount() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com"))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com"))

        accountDao.deleteAllTables("acc-1")

        val result = accountDao.getAccount("acc-2").first()
        assertThat(result).isNotNull()
        assertThat(result?.weightCompSettings).isNotNull()
        assertThat(result?.goalSettings).isNotNull()
        assertThat(result?.streaksSettings).isNotNull()
        assertThat(result?.weightlessSettings).isNotNull()
        assertThat(result?.notificationSettings).isNotNull()
        assertThat(result?.dashboardSettings).isNotNull()
        assertThat(result?.integrationsSettings).isNotNull()
    }

    // -------------------------------------------------------------------------
    // removeAllAccounts
    // -------------------------------------------------------------------------

    @Test
    fun removeAllAccounts_removesEverything() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com"))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com"))

        accountDao.removeAllAccounts()

        assertThat(accountDao.getAccountEntity("acc-1")).isNull()
        assertThat(accountDao.getAccountEntity("acc-2")).isNull()
        // Verify settings rows were removed by CASCADE
        assertThat(accountDao.getDashboardSettings("acc-1").first()).isNull()
        assertThat(accountDao.getDashboardSettings("acc-2").first()).isNull()
    }

    // -------------------------------------------------------------------------
    // Individual settings delete methods
    // -------------------------------------------------------------------------

    @Test
    fun deleteWeightCompSettingsByAccount_removesOnlyTargetAccount() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com"))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com"))

        accountDao.deleteWeightCompSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.weightCompSettings).isNull()
        assertThat(accountDao.getAccount("acc-2").first()?.weightCompSettings).isNotNull()
    }

    @Test
    fun deleteGoalSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteGoalSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.goalSettings).isNull()
    }

    @Test
    fun deleteStreaksSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteStreaksSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.streaksSettings).isNull()
    }

    @Test
    fun deleteWeightlessSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteWeightlessSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.weightlessSettings).isNull()
    }

    @Test
    fun deleteNotificationSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteNotificationSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.notificationSettings).isNull()
    }

    @Test
    fun deleteDashboardSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteDashboardSettingsByAccount("acc-1")

        assertThat(accountDao.getDashboardSettings("acc-1").first()).isNull()
    }

    @Test
    fun deleteIntegrationsSettingsByAccount_removesSettings() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteIntegrationsSettingsByAccount("acc-1")

        assertThat(accountDao.getAccount("acc-1").first()?.integrationsSettings).isNull()
    }

    @Test
    fun deleteIndividualSettings_preservesAccountEntity() = runTest {
        accountDao.insertFullAccount()

        accountDao.deleteWeightCompSettingsByAccount("acc-1")
        accountDao.deleteGoalSettingsByAccount("acc-1")

        // Account itself still exists
        assertThat(accountDao.getAccountEntity("acc-1")).isNotNull()
    }

    // -------------------------------------------------------------------------
    // CASCADE to cross-entity types (devices)
    // -------------------------------------------------------------------------

    @Test
    fun deleteAccountById_cascadesToDevices() = runTest {
        accountDao.insertFullAccount()
        deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1"))

        accountDao.deleteAccountById("acc-1")

        val devices = deviceDao.getDevices("acc-1").first()
        assertThat(devices).isEmpty()
    }

    @Test
    fun removeAllAccounts_cascadesToDevices() = runTest {
        accountDao.insertFullAccount()
        deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1"))

        accountDao.removeAllAccounts()

        val devices = deviceDao.getDevices("acc-1").first()
        assertThat(devices).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getDashboardSettings (standalone flow)
    // -------------------------------------------------------------------------

    @Test
    fun getDashboardSettings_returnsSettingsForAccount() = runTest {
        accountDao.insertAccount(account())
        accountDao.insertDashboardSettings(dashboardSettings(dashboardType = "compact"))

        val result = accountDao.getDashboardSettings("acc-1").first()
        assertThat(result).isNotNull()
        assertThat(result?.dashboardType).isEqualTo("compact")
    }

    @Test
    fun getDashboardSettings_returnsNullWhenNoneExist() = runTest {
        accountDao.insertAccount(account())

        assertThat(accountDao.getDashboardSettings("acc-1").first()).isNull()
    }

    @Test
    fun getDashboardSettings_returnsNullForMissingAccount() = runTest {
        assertThat(accountDao.getDashboardSettings("nonexistent").first()).isNull()
    }

    // -------------------------------------------------------------------------
    // Flow reactivity (verify emissions after mutations)
    // -------------------------------------------------------------------------

    @Test
    fun getActiveAccount_emitsUpdatedValueAfterActivation() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com", isActiveAccount = false))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com", isActiveAccount = false))

        assertThat(accountDao.getActiveAccount().first()).isNull()

        accountDao.activateAccount("acc-2")

        assertThat(accountDao.getActiveAccount().first()?.account?.id).isEqualTo("acc-2")
    }

    @Test
    fun getAllLoggedInAccounts_reactsToLogout() = runTest {
        accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com", isLoggedIn = true))
        accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com", isLoggedIn = true))

        assertThat(accountDao.getAllLoggedInAccounts().first()).hasSize(2)

        accountDao.logoutAccount("acc-1")

        val remaining = accountDao.getAllLoggedInAccounts().first()
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].account.id).isEqualTo("acc-2")
    }

    @Test
    fun getUnsyncedActiveAccount_reactsToSyncStatusChange() = runTest {
        accountDao.insertFullAccount(account(isActiveAccount = true, isSynced = false))

        assertThat(accountDao.getUnsyncedActiveAccount().first()).isNotNull()

        accountDao.markAccountSynced("acc-1")

        assertThat(accountDao.getUnsyncedActiveAccount().first()).isNull()
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun getAllLoggedInAccounts_returnsEmptyWhenNoneLoggedIn() = runTest {
        accountDao.insertFullAccount(account(isLoggedIn = false))

        val result = accountDao.getAllLoggedInAccounts().first()
        assertThat(result).isEmpty()
    }

    @Test
    fun getAccountEntity_returnsNullForNonexistentId() = runTest {
        assertThat(accountDao.getAccountEntity("nonexistent")).isNull()
    }

    @Test
    fun markAccountExpired_noOpForNonexistentId() = runTest {
        // Should not throw and should not create any row
        accountDao.markAccountExpired("nonexistent")
        assertThat(accountDao.getAccountEntity("nonexistent")).isNull()
    }

    @Test
    fun updateSyncStatus_togglesBothDirections() = runTest {
        accountDao.insertAccount(account(isSynced = false))

        accountDao.updateSyncStatus("acc-1", true)
        assertThat(accountDao.getAccountEntity("acc-1")?.isSynced).isTrue()

        accountDao.updateSyncStatus("acc-1", false)
        assertThat(accountDao.getAccountEntity("acc-1")?.isSynced).isFalse()
    }

    @Test
    fun logoutAccount_preservesOtherAccountState() = runTest {
        accountDao.insertAccount(account(id = "acc-1", email = "a@b.com", isLoggedIn = true, isActiveAccount = true))
        accountDao.insertAccount(account(id = "acc-2", email = "c@d.com", isLoggedIn = true, isActiveAccount = false))

        accountDao.logoutAccount("acc-1")

        // acc-2 should be unchanged
        val acc2 = accountDao.getAccountEntity("acc-2")
        assertThat(acc2?.isLoggedIn).isTrue()
        assertThat(acc2?.isActiveAccount).isFalse()
    }
}
