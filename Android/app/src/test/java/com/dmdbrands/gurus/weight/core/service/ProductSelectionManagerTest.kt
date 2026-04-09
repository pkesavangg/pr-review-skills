package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProductSelectionManagerTest {

    companion object {
        private const val ACCOUNT_ID = "account-123"
        private const val BABY_ID_1 = "baby-1"
        private const val BABY_NAME_1 = "Luna"
        private const val BABY_ID_2 = "baby-2"
        private const val BABY_NAME_2 = "Max"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var productSelectionRepository: IProductSelectionRepository

    private lateinit var manager: ProductSelectionManager

    private val baby1 = BabyProfile(id = BABY_ID_1, name = BABY_NAME_1, birthdate = null, accountId = ACCOUNT_ID)
    private val baby2 = BabyProfile(id = BABY_ID_2, name = BABY_NAME_2, birthdate = null, accountId = ACCOUNT_ID)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(AppLog)
        io.mockk.every { AppLog.d(any(), any()) } returns Unit

        manager = ProductSelectionManager(
            productSelectionRepository = productSelectionRepository,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── loadAvailableProducts ─────────────────────────────────────────────────

    @Test
    fun `loadAvailableProducts always includes MyWeight`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).containsExactly(ProductSelection.MyWeight)
    }

    @Test
    fun `loadAvailableProducts includes BloodPressure when BPM device exists`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).contains(ProductSelection.BloodPressure)
    }

    @Test
    fun `loadAvailableProducts includes Baby entries for each profile`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1, baby2)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        val available = manager.availableProducts.value
        assertThat(available).hasSize(3) // MyWeight + 2 babies
        assertThat(available).contains(ProductSelection.Baby(baby1))
        assertThat(available).contains(ProductSelection.Baby(baby2))
    }

    @Test
    fun `loadAvailableProducts includes all types when BPM and babies exist`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        val available = manager.availableProducts.value
        assertThat(available).hasSize(3) // MyWeight + BloodPressure + Baby
        assertThat(available).contains(ProductSelection.MyWeight)
        assertThat(available).contains(ProductSelection.BloodPressure)
        assertThat(available).contains(ProductSelection.Baby(baby1))
    }

    @Test
    fun `loadAvailableProducts excludes BloodPressure when no BPM device`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).doesNotContain(ProductSelection.BloodPressure)
    }

    // ── selectProduct ─────────────────────────────────────────────────────────

    @Test
    fun `selectProduct MyWeight persists and updates state`() = runTest {
        manager.selectProduct(ProductSelection.MyWeight)

        coVerify { productSelectionRepository.saveSelectedProductType(ProductType.MY_WEIGHT) }
        coVerify { productSelectionRepository.saveSelectedBabyProfileId(null) }
        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.MyWeight)
    }

    @Test
    fun `selectProduct BloodPressure persists and updates state`() = runTest {
        manager.selectProduct(ProductSelection.BloodPressure)

        coVerify { productSelectionRepository.saveSelectedProductType(ProductType.BLOOD_PRESSURE) }
        coVerify { productSelectionRepository.saveSelectedBabyProfileId(null) }
        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.BloodPressure)
    }

    @Test
    fun `selectProduct Baby persists profile id and updates state`() = runTest {
        val selection = ProductSelection.Baby(baby1)

        manager.selectProduct(selection)

        coVerify { productSelectionRepository.saveSelectedProductType(ProductType.BABY) }
        coVerify { productSelectionRepository.saveSelectedBabyProfileId(BABY_ID_1) }
        assertThat(manager.selectedProduct.value).isEqualTo(selection)
    }

    // ── default state ─────────────────────────────────────────────────────────

    @Test
    fun `default selectedProduct is MyWeight`() {
        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.MyWeight)
    }

    @Test
    fun `default availableProducts contains only MyWeight`() {
        assertThat(manager.availableProducts.value).containsExactly(ProductSelection.MyWeight)
    }

    // ── bottom sheet state ────────────────────────────────────────────────────

    @Test
    fun `showProductSheet sets showSheet true and title`() {
        manager.showProductSheet("Dashboard")

        assertThat(manager.showSheet.value).isTrue()
        assertThat(manager.sheetTitle.value).isEqualTo("Dashboard")
    }

    @Test
    fun `dismissProductSheet sets showSheet false`() {
        manager.showProductSheet("Dashboard")
        manager.dismissProductSheet()

        assertThat(manager.showSheet.value).isFalse()
    }

    @Test
    fun `showProductSheet updates title on subsequent calls`() {
        manager.showProductSheet("Dashboard")
        manager.showProductSheet("History")

        assertThat(manager.sheetTitle.value).isEqualTo("History")
    }
}
