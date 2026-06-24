package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
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
    private lateinit var dataStore: UserDataStore

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
    fun `observeSelectedProductType maps stored name to enum`() = runTest {
        every { dataStore.selectedProductTypeForCurrentAccountFlow } returns flowOf(ProductType.BABY.name)

        val result = repository.observeSelectedProductType().first()

        assertThat(result).isEqualTo(ProductType.BABY)
    }

    @Test
    fun `observeSelectedProductType returns MY_WEIGHT as default when blank`() = runTest {
        every { dataStore.selectedProductTypeForCurrentAccountFlow } returns flowOf("")

        val result = repository.observeSelectedProductType().first()

        assertThat(result).isEqualTo(ProductType.MY_WEIGHT)
    }

    // ── observeSelectedBabyProfileId ────────────────────────────────────────────

    @Test
    fun `observeSelectedBabyProfileId returns id when set`() = runTest {
        every { dataStore.selectedBabyProfileIdForCurrentAccountFlow } returns flowOf("baby-1")

        val result = repository.observeSelectedBabyProfileId().first()

        assertThat(result).isEqualTo("baby-1")
    }

    @Test
    fun `observeSelectedBabyProfileId returns null when not set`() = runTest {
        every { dataStore.selectedBabyProfileIdForCurrentAccountFlow } returns flowOf(null)

        val result = repository.observeSelectedBabyProfileId().first()

        assertThat(result).isNull()
    }

    // ── saveSelectedProductType ─────────────────────────────────────────────────

    @Test
    fun `saveSelectedProductType persists name against active account`() = runTest {
        every { dataStore.currentAccountIdFlow } returns flowOf(ACCOUNT_ID)

        repository.saveSelectedProductType(ProductType.BLOOD_PRESSURE)

        coVerify { dataStore.setSelectedProductType(ACCOUNT_ID, ProductType.BLOOD_PRESSURE.name) }
    }

    @Test
    fun `saveSelectedProductType skips when no active account`() = runTest {
        every { dataStore.currentAccountIdFlow } returns flowOf(null)

        repository.saveSelectedProductType(ProductType.BLOOD_PRESSURE)

        coVerify(exactly = 0) { dataStore.setSelectedProductType(any(), any()) }
    }

    // ── saveSelectedBabyProfileId ───────────────────────────────────────────────

    @Test
    fun `saveSelectedBabyProfileId persists id against active account`() = runTest {
        every { dataStore.currentAccountIdFlow } returns flowOf(ACCOUNT_ID)

        repository.saveSelectedBabyProfileId("baby-1")

        coVerify { dataStore.setSelectedBabyProfileId(ACCOUNT_ID, "baby-1") }
    }

    @Test
    fun `saveSelectedBabyProfileId with null clears selection with empty string`() = runTest {
        every { dataStore.currentAccountIdFlow } returns flowOf(ACCOUNT_ID)

        repository.saveSelectedBabyProfileId(null)

        coVerify { dataStore.setSelectedBabyProfileId(ACCOUNT_ID, "") }
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

    // ── hasBabyScaleDevice (MOB-416) ────────────────────────────────────────────

    @Test
    fun `hasBabyScaleDevice returns true when baby scale devices exist`() = runTest {
        val device = mockk<DeviceDetails>()
        every { deviceDao.getDevicesByTypeWithAccount("babyScale", ACCOUNT_ID) } returns flowOf(listOf(device))

        val result = repository.hasBabyScaleDevice(ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasBabyScaleDevice returns false when no baby scale devices`() = runTest {
        every { deviceDao.getDevicesByTypeWithAccount("babyScale", ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.hasBabyScaleDevice(ACCOUNT_ID)

        assertThat(result).isFalse()
    }
}
