package com.dmdbrands.gurus.weight.migration.service

import android.content.Context
import android.util.Log
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.migration.model.IonicHealthConnectData
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationRepositoryTest {

    @MockK(relaxed = true)
    private lateinit var context: Context

    @MockK(relaxUnitFun = true)
    private lateinit var appDatabase: AppDatabase

    @MockK(relaxUnitFun = true)
    private lateinit var accountDao: AccountDao

    @MockK(relaxUnitFun = true)
    private lateinit var deviceDao: DeviceDao

    @MockK(relaxUnitFun = true)
    private lateinit var entryDao: EntryDao

    private lateinit var sut: MigrationRepository

    companion object {
        private const val ACCOUNT_ID = "acc-123"
        private const val ACCOUNT_EMAIL = "test@example.com"
        private const val INSERT_ERROR = "Insert failed"
        private const val ENTRY_ERROR = "Entry insert failed"
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkObject(AppDatabase.Companion)
        mockkStatic(Log::class)

        every { AppDatabase.getInstance(any()) } returns appDatabase
        every { appDatabase.deviceDao() } returns deviceDao
        every { appDatabase.accountDao() } returns accountDao
        every { appDatabase.entryDao() } returns entryDao
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        sut = MigrationRepository(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region insertDevice

    @Test
    fun `insertDevice inserts all devices via deviceDao`() = runTest {
        val devices = listOf(mockk<DeviceDetails>(), mockk<DeviceDetails>())

        sut.insertDevice(devices)

        coVerify(exactly = 2) { deviceDao.insertDevice(any<DeviceDetails>()) }
    }

    @Test
    fun `insertDevice with empty list does not call deviceDao`() = runTest {
        sut.insertDevice(emptyList())

        coVerify(exactly = 0) { deviceDao.insertDevice(any<DeviceDetails>()) }
    }

    @Test
    fun `insertDevice propagates exception from deviceDao`() = runTest {
        val device = mockk<DeviceDetails>()
        coEvery { deviceDao.insertDevice(any<DeviceDetails>()) } throws RuntimeException(INSERT_ERROR)

        try {
            sut.insertDevice(listOf(device))
            @Suppress("UNREACHABLE_CODE")
            assertThat(false).isTrue() // should not reach
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(INSERT_ERROR)
        }
    }

    // endregion

    // region saveIntegrationSettings

    @Test
    fun `saveIntegrationSettings saves data for each account`() = runTest {
        mockkConstructor(HealthConnectDataStore::class)
        coEvery { anyConstructed<HealthConnectDataStore>().setHealthConnectData(any(), any()) } returns Unit

        val ionicData = mockk<IonicHealthConnectData>(relaxed = true)
        val settings = mapOf(ACCOUNT_ID to ionicData)

        sut.saveIntegrationSettings(settings)

        coVerify { anyConstructed<HealthConnectDataStore>().setHealthConnectData(eq(ACCOUNT_ID), any()) }
    }

    @Test
    fun `saveIntegrationSettings with empty map does not call dataStore`() = runTest {
        mockkConstructor(HealthConnectDataStore::class)
        coEvery { anyConstructed<HealthConnectDataStore>().setHealthConnectData(any(), any()) } returns Unit

        sut.saveIntegrationSettings(emptyMap())

        coVerify(exactly = 0) { anyConstructed<HealthConnectDataStore>().setHealthConnectData(any(), any()) }
    }

    // endregion

    // region insertAccountWithSettings

    @Test
    fun `insertAccountWithSettings inserts account and all settings`() = runTest {
        val accountEntity = mockk<AccountEntity> { every { email } returns ACCOUNT_EMAIL }
        val goalSettings = mockk<GoalSettingsEntity>()
        val weightlessSettings = mockk<WeightlessSettingsEntity>()
        val integrationsSettings = mockk<IntegrationsSettingsEntity>()
        val weightCompSettings = mockk<WeightCompSettingsEntity>()
        val notificationSettings = mockk<NotificationSettingsEntity>()
        val dashboardSettings = mockk<DashboardSettingsEntity>()

        sut.insertAccountWithSettings(
            accountEntity, goalSettings, weightlessSettings,
            integrationsSettings, weightCompSettings,
            notificationSettings, dashboardSettings
        )

        coVerify { accountDao.insertAccount(accountEntity) }
        coVerify { accountDao.insertGoalSettings(goalSettings) }
        coVerify { accountDao.insertWeightlessSettings(weightlessSettings) }
        coVerify { accountDao.insertIntegrationsSettings(integrationsSettings) }
        coVerify { accountDao.insertWeightCompSettings(weightCompSettings) }
        coVerify { accountDao.insertNotificationSettings(notificationSettings) }
        coVerify { accountDao.insertDashboardSettings(dashboardSettings) }
    }

    @Test
    fun `insertAccountWithSettings rethrows exception on failure`() = runTest {
        val accountEntity = mockk<AccountEntity> { every { email } returns ACCOUNT_EMAIL }
        coEvery { accountDao.insertAccount(any()) } throws RuntimeException(INSERT_ERROR)

        try {
            sut.insertAccountWithSettings(
                accountEntity, mockk(), mockk(), mockk(), mockk(), mockk(), mockk()
            )
            @Suppress("UNREACHABLE_CODE")
            assertThat(false).isTrue()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(INSERT_ERROR)
        }
    }

    @Test
    fun `insertAccountWithSettings does not insert remaining settings when account insert fails`() = runTest {
        val accountEntity = mockk<AccountEntity> { every { email } returns ACCOUNT_EMAIL }
        coEvery { accountDao.insertAccount(any()) } throws RuntimeException(INSERT_ERROR)

        try {
            sut.insertAccountWithSettings(
                accountEntity, mockk(), mockk(), mockk(), mockk(), mockk(), mockk()
            )
        } catch (_: RuntimeException) { }

        coVerify(exactly = 0) { accountDao.insertGoalSettings(any()) }
        coVerify(exactly = 0) { accountDao.insertWeightlessSettings(any()) }
        coVerify(exactly = 0) { accountDao.insertIntegrationsSettings(any()) }
        coVerify(exactly = 0) { accountDao.insertWeightCompSettings(any()) }
        coVerify(exactly = 0) { accountDao.insertNotificationSettings(any()) }
        coVerify(exactly = 0) { accountDao.insertDashboardSettings(any()) }
    }

    // endregion

    // region insertScaleEntries

    @Test
    fun `insertScaleEntries returns count of successfully inserted entries`() = runTest {
        val entries = listOf(mockk<ScaleEntry>(), mockk<ScaleEntry>(), mockk<ScaleEntry>())
        coEvery { entryDao.insert(any<Entry>()) } returns 1L

        val result = sut.insertScaleEntries(entries)

        assertThat(result).isEqualTo(3)
        coVerify(exactly = 3) { entryDao.insert(any<Entry>()) }
    }

    @Test
    fun `insertScaleEntries returns partial count when some inserts fail`() = runTest {
        val entries = listOf(mockk<ScaleEntry>(), mockk<ScaleEntry>(), mockk<ScaleEntry>())
        coEvery { entryDao.insert(entries[0] as Entry) } returns 1L
        coEvery { entryDao.insert(entries[1] as Entry) } throws RuntimeException(ENTRY_ERROR)
        coEvery { entryDao.insert(entries[2] as Entry) } returns 3L

        val result = sut.insertScaleEntries(entries)

        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `insertScaleEntries returns zero when all inserts fail`() = runTest {
        val entries = listOf(mockk<ScaleEntry>(), mockk<ScaleEntry>())
        coEvery { entryDao.insert(any<Entry>()) } throws RuntimeException(ENTRY_ERROR)

        val result = sut.insertScaleEntries(entries)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `insertScaleEntries returns zero for empty list`() = runTest {
        val result = sut.insertScaleEntries(emptyList())

        assertThat(result).isEqualTo(0)
        coVerify(exactly = 0) { entryDao.insert(any<Entry>()) }
    }

    @Test
    fun `insertScaleEntries continues inserting after individual failure`() = runTest {
        val entries = listOf(mockk<ScaleEntry>(), mockk<ScaleEntry>(), mockk<ScaleEntry>())
        coEvery { entryDao.insert(entries[0] as Entry) } throws RuntimeException(ENTRY_ERROR)
        coEvery { entryDao.insert(entries[1] as Entry) } returns 2L
        coEvery { entryDao.insert(entries[2] as Entry) } returns 3L

        val result = sut.insertScaleEntries(entries)

        assertThat(result).isEqualTo(2)
        coVerify(exactly = 3) { entryDao.insert(any<Entry>()) }
    }

    // endregion
}
