package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryServiceTest {

    // --- Mocks ---
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk()
    private val goalService: IGoalService = mockk(relaxed = true)
    private val healthConnectService: IHealthConnectService = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)

    private lateinit var service: EntryService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"
    private val fakeAccount: Account = mockk {
        every { initialWeight } returns 180.0
    }
    private val fakeEntry: Entry = mockk(relaxed = true)
    private val fakeEntry2: Entry = mockk(relaxed = true)
    private val fakeEntry3: Entry = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.d(any(), any(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.w(any<String>(), any<String>()) } returns Unit
        every { AppLog.i(any<String>(), any<String>()) } returns Unit
        every { AppLog.v(any<String>(), any<String>()) } returns Unit

        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)

        service = EntryService(
            entryRepository = entryRepository,
            accountRepository = accountRepository,
            goalService = goalService,
            healthConnectService = healthConnectService,
            healthConnectRepository = healthConnectRepository,
            analyticsService = analyticsService,
            appScope = TestScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // isUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `isUpdating defaults to false`() {
        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateAllData — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData calls syncOperations with account id`() = runTest {
        service.updateAllData(testAccountId)

        coVerify { entryRepository.getUnSynced(testAccountId) }
    }

    // -------------------------------------------------------------------------
    // updateAllData — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData returns early when accountId is null`() = runTest {
        service.updateAllData(null)

        coVerify(exactly = 0) { entryRepository.getUnSynced(any()) }
    }

    // -------------------------------------------------------------------------
    // addEntry — single entry
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single entry triggers sync`() = runTest {
        service.updateAllData(testAccountId)

        service.addEntry(fakeEntry)

        coVerify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // addEntry — list of entries
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list triggers sync`() = runTest {
        service.updateAllData(testAccountId)
        val entries = listOf(fakeEntry, fakeEntry2)

        service.addEntry(entries)

        coVerify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // syncOperations
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations returns early when accountId is null`() = runTest {
        val newEntries = listOf(fakeEntry)
        val deleteOps = listOf(fakeEntry2)

        service.syncOperations(newEntries, deleteOps)

        // Should not call entryRepository since accountId is null
        coVerify(exactly = 0) { entryRepository.getUnSynced(any()) }
    }

    // -------------------------------------------------------------------------
    // addBabyEntry / deleteBabyEntryLocally — local-only baby persistence (MOB-428)
    // -------------------------------------------------------------------------

    private fun babyEntry(babyId: String = "baby-1") = BabyEntry(
        entry = EntryEntity(
            id = 0L,
            accountId = "",
            entryTimestamp = "2026-06-10T10:00:00.000Z",
            operationType = "create",
            deviceType = "baby",
            deviceId = "dev-1",
        ),
        babyEntry = BabyEntryEntity(id = 0L, babyId = babyId, babyWeightDecigrams = 40_000),
    )

    @Test
    fun `addBabyEntry returns -1 and does not insert when accountId is null`() = runTest {
        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(-1)
        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `addBabyEntry persists under active account as synced create and returns new id`() = runTest {
        service.updateAllData(testAccountId)
        val captured = slot<Entry>()
        coEvery { entryRepository.insert(capture(captured)) } returns 99L

        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(99L)
        // Stamped with the active (parent) account and kept OUT of the sync queue.
        assertThat(captured.captured.entry.accountId).isEqualTo(testAccountId)
        assertThat(captured.captured.entry.isSynced).isTrue()
        assertThat(captured.captured.entry.operationType).isEqualTo(OperationType.CREATE.name)
    }

    @Test
    fun `addBabyEntry never sends the baby entry to the server`() = runTest {
        service.updateAllData(testAccountId)
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L

        service.addBabyEntry(babyEntry())

        coVerify(exactly = 0) { entryRepository.sendOperationToAPI(any()) }
    }

    @Test
    fun `addBabyEntry returns -1 and logs when insert fails`() = runTest {
        service.updateAllData(testAccountId)
        coEvery { entryRepository.insert(any<Entry>()) } throws RuntimeException("DB error")

        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(-1)
        verify { AppLog.e("EntryService", match { it.contains("Error saving baby entry") }, any<Throwable>()) }
    }

    @Test
    fun `deleteBabyEntryLocally hard-deletes by id`() = runTest {
        service.deleteBabyEntryLocally(99L)

        coVerify { entryRepository.deleteById(99L) }
    }

    // -------------------------------------------------------------------------
    // initializeGoalCardMonitoring — entry count >= 3
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring calls checkGoalCard when entries at least 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2, fakeEntry3)

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify { goalService.checkGoalCard() }
    }

    @Test
    fun `initializeGoalCardMonitoring does not call checkGoalCard when entries less than 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2)

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify(exactly = 0) { goalService.checkGoalCard() }
    }

    // -------------------------------------------------------------------------
    // initializeGoalCardMonitoring — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring handles exception gracefully`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } throws RuntimeException("DB error")

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        verify { AppLog.e("EntryService", match { it.contains("Error checking entries for goal card") }, any<String>()) }
    }
}
