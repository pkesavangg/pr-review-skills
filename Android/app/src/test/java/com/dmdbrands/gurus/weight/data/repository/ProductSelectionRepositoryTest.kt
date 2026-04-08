package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.ProductSelectionDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProductSelectionRepositoryTest {

    companion object {
        private const val ACCOUNT_ID = "account-123"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var dataStore: ProductSelectionDataStore

    @MockK
    private lateinit var babyProfileDao: BabyProfileDao

    @MockK
    private lateinit var deviceDao: DeviceDao

    private lateinit var repository: ProductSelectionRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = ProductSelectionRepository(dataStore, babyProfileDao, deviceDao)
    }

    // ── observeSelectedProductType ──────────────────────────────────────────────

    @Test
    fun `observeSelectedProductType delegates to dataStore`() = runTest {
        every { dataStore.observeSelectedProductType() } returns flowOf(ProductType.BABY)

        val result = repository.observeSelectedProductType().first()

        assertThat(result).isEqualTo(ProductType.BABY)
    }

    @Test
    fun `observeSelectedProductType returns MY_WEIGHT as default`() = runTest {
        every { dataStore.observeSelectedProductType() } returns flowOf(ProductType.MY_WEIGHT)

        val result = repository.observeSelectedProductType().first()

        assertThat(result).isEqualTo(ProductType.MY_WEIGHT)
    }

    // ── observeSelectedBabyProfileId ────────────────────────────────────────────

    @Test
    fun `observeSelectedBabyProfileId returns id when set`() = runTest {
        every { dataStore.observeSelectedBabyProfileId() } returns flowOf("baby-1")

        val result = repository.observeSelectedBabyProfileId().first()

        assertThat(result).isEqualTo("baby-1")
    }

    @Test
    fun `observeSelectedBabyProfileId returns null when not set`() = runTest {
        every { dataStore.observeSelectedBabyProfileId() } returns flowOf(null)

        val result = repository.observeSelectedBabyProfileId().first()

        assertThat(result).isNull()
    }

    // ── saveSelectedProductType ─────────────────────────────────────────────────

    @Test
    fun `saveSelectedProductType delegates to dataStore`() = runTest {
        repository.saveSelectedProductType(ProductType.BLOOD_PRESSURE)

        coVerify { dataStore.saveSelectedProductType(ProductType.BLOOD_PRESSURE) }
    }

    // ── saveSelectedBabyProfileId ───────────────────────────────────────────────

    @Test
    fun `saveSelectedBabyProfileId delegates to dataStore`() = runTest {
        repository.saveSelectedBabyProfileId("baby-1")

        coVerify { dataStore.saveSelectedBabyProfileId("baby-1") }
    }

    @Test
    fun `saveSelectedBabyProfileId with null clears selection`() = runTest {
        repository.saveSelectedBabyProfileId(null)

        coVerify { dataStore.saveSelectedBabyProfileId(null) }
    }

    // ── getBabyProfiles ─────────────────────────────────────────────────────────

    @Test
    fun `getBabyProfiles returns mapped profiles from DAO`() = runTest {
        val entity = BabyProfileEntity(babyId = "baby-1", name = "Luna", birthdate = null, accountId = ACCOUNT_ID)
        every { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(listOf(entity))

        val result = repository.getBabyProfiles(ACCOUNT_ID)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("baby-1")
        assertThat(result[0].name).isEqualTo("Luna")
        assertThat(result[0].accountId).isEqualTo(ACCOUNT_ID)
    }

    @Test
    fun `getBabyProfiles returns empty list when no profiles`() = runTest {
        every { babyProfileDao.observeByAccountId(ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.getBabyProfiles(ACCOUNT_ID)

        assertThat(result).isEmpty()
    }

    // ── hasBpmDevice ────────────────────────────────────────────────────────────

    @Test
    fun `hasBpmDevice returns true when BPM devices exist`() = runTest {
        val device = mockk<DeviceDetails>()
        every { deviceDao.getDevicesByTypeWithAccount("BPM", ACCOUNT_ID) } returns flowOf(listOf(device))

        val result = repository.hasBpmDevice(ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasBpmDevice returns false when no BPM devices`() = runTest {
        every { deviceDao.getDevicesByTypeWithAccount("BPM", ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.hasBpmDevice(ACCOUNT_ID)

        assertThat(result).isFalse()
    }
}
