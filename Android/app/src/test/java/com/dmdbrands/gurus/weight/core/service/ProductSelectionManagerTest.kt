package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.flow.flowOf
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
        every { AppLog.d(any(), any()) } returns Unit

        // Default: no prior selection → DataStore returns MY_WEIGHT default.
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.MY_WEIGHT)
        every { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf(null)
        every { productSelectionRepository.observeHasUserSelected() } returns flowOf(false)
        // Default: account does not own a baby scale (overridden per-test). (MOB-416)
        coEvery { productSelectionRepository.hasBabyScaleDevice(any()) } returns false

        ProductSelectionManager.USE_SAMPLE_PRODUCTS = false

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

    // ── BabyScale: device owned but no profiles (MOB-416) ─────────────────────

    @Test
    fun `loadAvailableProducts includes BabyScale when baby scale owned and no profiles`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).containsExactly(
            ProductSelection.MyWeight,
            ProductSelection.BabyScale,
        )
    }

    @Test
    fun `loadAvailableProducts prefers Baby profiles over BabyScale when profiles exist`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        val available = manager.availableProducts.value
        assertThat(available).contains(ProductSelection.Baby(baby1))
        assertThat(available).doesNotContain(ProductSelection.BabyScale)
    }

    @Test
    fun `loadAvailableProducts excludes BabyScale when no device and no profiles`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).doesNotContain(ProductSelection.BabyScale)
    }

    @Test
    fun `loadAvailableProducts sets hasBabyScaleDevice from repository`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.hasBabyScaleDevice.value).isTrue()
    }

    @Test
    fun `initial selection restores BabyScale when saved Baby but no profiles remain`() = runTest {
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.BABY)
        every { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf(BABY_ID_1)
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.BabyScale)
    }

    @Test
    fun `selectProduct BabyScale persists BABY type with null profile id`() = runTest {
        manager.selectProduct(ProductSelection.BabyScale)

        coVerify { productSelectionRepository.saveSelectedProductType(ProductType.BABY) }
        coVerify { productSelectionRepository.saveSelectedBabyProfileId(null) }
        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.BabyScale)
    }

    // ── initial selection: restore from storage ──────────────────────────────

    @Test
    fun `initial selection defaults to MyWeight for legacy users with no saved choice`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.MyWeight)
    }

    @Test
    fun `initial selection restores saved BloodPressure pick`() = runTest {
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.BLOOD_PRESSURE)
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.BloodPressure)
    }

    @Test
    fun `initial selection restores saved Baby with matching profile id`() = runTest {
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.BABY)
        every { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf(BABY_ID_2)
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1, baby2)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.Baby(baby2))
    }

    @Test
    fun `initial selection falls back to MyWeight when saved type no longer registered`() = runTest {
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.BLOOD_PRESSURE)
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false // BPM unregistered now

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.selectedProduct.value).isEqualTo(ProductSelection.MyWeight)
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
