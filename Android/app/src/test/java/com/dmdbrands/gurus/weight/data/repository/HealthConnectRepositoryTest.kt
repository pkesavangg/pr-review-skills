package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.data.api.IHealthConnectAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HealthConnectRepositoryTest {

    @MockK
    lateinit var accountRepository: IAccountRepository

    @MockK
    lateinit var healthConnectAPI: IHealthConnectAPI

    @MockK
    lateinit var healthConnectDataStore: HealthConnectDataStore

    private lateinit var repository: HealthConnectRepository

    private val accountId = "acc-123"
    private val mockHealthConnectData: HealthConnectData = mockk(relaxed = true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = HealthConnectRepository(accountRepository, healthConnectAPI, healthConnectDataStore)
    }

    // -----------------------------------------------------------------------
    // syncEntry
    // -----------------------------------------------------------------------

    @Test
    fun `syncEntry delegates to healthConnectAPI sync with correct entry`() = runTest {
        val entry = HealthConnectSyncEntry(
            type = "weight",
            sentAt = "2026-03-12T00:00:00Z",
            timestamp = "2026-03-12T00:00:00Z",
            weight = 80.0,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = null,
            data = null
        )
        coEvery { healthConnectAPI.sync(entry) } returns Unit

        repository.syncEntry(entry)

        coVerify { healthConnectAPI.sync(entry) }
    }

    @Test
    fun `syncEntry rethrows exception when API fails`() = runTest {
        val entry = mockk<HealthConnectSyncEntry>(relaxed = true)
        coEvery { healthConnectAPI.sync(entry) } throws IOException("Network error")

        var threw = false
        try {
            repository.syncEntry(entry)
        } catch (e: IOException) {
            threw = true
        }

        assertThat(threw).isTrue()
    }

    // -----------------------------------------------------------------------
    // updateOutOfSyncStatus
    // -----------------------------------------------------------------------

    @Test
    fun `updateOutOfSyncStatus calls updateOutOfSync on DataStore`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(accountId, true) } returns Unit

        repository.updateOutOfSyncStatus(accountId, true)

        coVerify { healthConnectDataStore.updateOutOfSync(accountId, true) }
    }

    @Test
    fun `updateOutOfSyncStatus swallows exception when DataStore throws`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(accountId, false) } throws RuntimeException("DataStore error")

        // Should not throw
        repository.updateOutOfSyncStatus(accountId, false)
    }

    // -----------------------------------------------------------------------
    // getAccountDataMap
    // -----------------------------------------------------------------------

    @Test
    fun `getAccountDataMap returns map from DataStore`() = runTest {
        val expectedMap = mapOf(accountId to mockHealthConnectData)
        coEvery { healthConnectDataStore.healthConnectData() } returns expectedMap

        val result = repository.getAccountDataMap()

        assertThat(result).isEqualTo(expectedMap)
        coVerify { healthConnectDataStore.healthConnectData() }
    }

    // -----------------------------------------------------------------------
    // addAccount
    // -----------------------------------------------------------------------

    @Test
    fun `addAccount calls setHealthConnectData with correct args`() = runTest {
        coEvery { healthConnectDataStore.setHealthConnectData(accountId, mockHealthConnectData) } returns Unit

        repository.addAccount(accountId, mockHealthConnectData)

        coVerify { healthConnectDataStore.setHealthConnectData(accountId, mockHealthConnectData) }
    }

    // -----------------------------------------------------------------------
    // clearData
    // -----------------------------------------------------------------------

    @Test
    fun `clearData delegates to DataStore clearData`() = runTest {
        coEvery { healthConnectDataStore.clearData() } returns Unit

        repository.clearData()

        coVerify { healthConnectDataStore.clearData() }
    }

    // -----------------------------------------------------------------------
    // getAccountByID
    // -----------------------------------------------------------------------

    @Test
    fun `getAccountByID returns data from DataStore`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(accountId) } returns mockHealthConnectData

        val result = repository.getAccountByID(accountId)

        assertThat(result).isEqualTo(mockHealthConnectData)
        coVerify { healthConnectDataStore.getHealthConnectData(accountId) }
    }

    @Test
    fun `getAccountByID returns null when no data exists`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(accountId) } returns null

        val result = repository.getAccountByID(accountId)

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // hasAccountData
    // -----------------------------------------------------------------------

    @Test
    fun `hasAccountData returns true when DataStore has data`() = runTest {
        coEvery { healthConnectDataStore.hasHealthConnectData(accountId) } returns true

        val result = repository.hasAccountData(accountId)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasAccountData returns false when DataStore has no data`() = runTest {
        coEvery { healthConnectDataStore.hasHealthConnectData(accountId) } returns false

        val result = repository.hasAccountData(accountId)

        assertThat(result).isFalse()
    }

    // -----------------------------------------------------------------------
    // updateOutOfSync (direct)
    // -----------------------------------------------------------------------

    @Test
    fun `updateOutOfSync delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(accountId, true) } returns Unit

        repository.updateOutOfSync(accountId, true)

        coVerify { healthConnectDataStore.updateOutOfSync(accountId, true) }
    }

    // -----------------------------------------------------------------------
    // setOpen / getOpen
    // -----------------------------------------------------------------------

    @Test
    fun `setOpen delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.setOpen(accountId, true) } returns Unit

        repository.setOpen(accountId, true)

        coVerify { healthConnectDataStore.setOpen(accountId, true) }
    }

    @Test
    fun `getOpen returns value from DataStore`() = runTest {
        coEvery { healthConnectDataStore.getOpen(accountId) } returns true

        val result = repository.getOpen(accountId)

        assertThat(result).isTrue()
    }
}
