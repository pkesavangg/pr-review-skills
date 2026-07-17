package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Provider

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

    private val accountService: IAccountService = mockk(relaxed = true)

    // Unconfined test scope for the manager's reactive account observer; no emissions by default.
    private val appScope = CoroutineScope(UnconfinedTestDispatcher())

    private lateinit var manager: ProductSelectionManager

    private val baby1 = BabyProfile(id = BABY_ID_1, name = BABY_NAME_1, birthdate = null, accountId = ACCOUNT_ID)
    private val baby2 = BabyProfile(id = BABY_ID_2, name = BABY_NAME_2, birthdate = null, accountId = ACCOUNT_ID)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit

        // Default: no prior selection → DataStore returns MY_WEIGHT default.
        every { productSelectionRepository.observeSelectedProductType() } returns flowOf(ProductType.MY_WEIGHT)
        every { productSelectionRepository.observeSelectedBabyProfileId() } returns flowOf(null)
        every { productSelectionRepository.observeHasUserSelected() } returns flowOf(false)
        // Default: no baby profiles for the reactive baby observer (overridden per-test). (MOB-1476)
        every { productSelectionRepository.observeBabyProfiles(any()) } returns flowOf(emptyList())
        // Default: account does not own a baby scale (overridden per-test). (MOB-416)
        coEvery { productSelectionRepository.hasBabyScaleDevice(any()) } returns false
        // Default: reactive account observer sees no emissions (overridden in the reactive test).
        every { accountService.activeAccountFlow } returns emptyFlow()

        ProductSelectionManager.USE_SAMPLE_PRODUCTS = false

        manager = ProductSelectionManager(
            productSelectionRepository = productSelectionRepository,
            accountService = Provider { accountService },
            appScope = appScope,
        )
    }

    @AfterEach
    fun tearDown() {
        appScope.cancel()
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

    // ── account productTypes drives availability (MOB-592) ────────────────────
    // A fresh baby-scale/BP signup has the product on the account before any baby
    // profile or paired device exists locally. Availability must honour productTypes.

    @Test
    fun `loadAvailableProducts shows only BabyScale for a baby-only account (no weight)`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns false
        val account = mockk<Account>(relaxed = true)
        every { account.productTypes } returns listOf(ProductType.BABY.apiValue)
        coEvery { accountService.getCurrentAccount() } returns account

        manager.loadAvailableProducts(ACCOUNT_ID)

        // Baby-only account must NOT surface My Weight. (MOB-592)
        assertThat(manager.availableProducts.value).containsExactly(ProductSelection.BabyScale)
    }

    @Test
    fun `loadAvailableProducts excludes MyWeight when account productTypes omits weight`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        val account = mockk<Account>(relaxed = true)
        every { account.productTypes } returns listOf(ProductType.BLOOD_PRESSURE.apiValue)
        coEvery { accountService.getCurrentAccount() } returns account

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).containsExactly(ProductSelection.BloodPressure)
    }

    @Test
    fun `loadAvailableProducts keeps MyWeight for legacy accounts with empty productTypes`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        val account = mockk<Account>(relaxed = true)
        every { account.productTypes } returns emptyList()
        coEvery { accountService.getCurrentAccount() } returns account

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).containsExactly(ProductSelection.MyWeight)
    }

    @Test
    fun `loadAvailableProducts includes BloodPressure when account productTypes has blood_pressure and no device`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        val account = mockk<Account>(relaxed = true)
        every { account.productTypes } returns listOf(ProductType.BLOOD_PRESSURE.apiValue)
        coEvery { accountService.getCurrentAccount() } returns account

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value).contains(ProductSelection.BloodPressure)
    }

    @Test
    fun `loadAvailableProducts prefers real Baby profiles over BabyScale even when productTypes has baby`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns false
        val account = mockk<Account>(relaxed = true)
        every { account.productTypes } returns listOf(ProductType.BABY.apiValue)
        coEvery { accountService.getCurrentAccount() } returns account

        manager.loadAvailableProducts(ACCOUNT_ID)

        val available = manager.availableProducts.value
        assertThat(available).contains(ProductSelection.Baby(baby1))
        assertThat(available).doesNotContain(ProductSelection.BabyScale)
    }

    // ── reactive refresh: availableProducts follows account productTypes ──────
    // The dashboard product switcher must update live when a device is paired (adds a
    // product to the account) — without an app restart. The active account is a Room
    // @Relation flow that re-emits on productTypes change; the manager observes it.

    @Test
    fun `availableProducts updates reactively when account gains the weight product`() = runTest {
        val babyOnly = mockk<Account>(relaxed = true)
        every { babyOnly.id } returns ACCOUNT_ID
        every { babyOnly.productTypes } returns listOf(ProductType.BABY.apiValue)
        val babyAndWeight = mockk<Account>(relaxed = true)
        every { babyAndWeight.id } returns ACCOUNT_ID
        every { babyAndWeight.productTypes } returns listOf(ProductType.BABY.apiValue, ProductType.MY_WEIGHT.apiValue)

        val accountFlow = MutableStateFlow<Account?>(babyOnly)
        every { accountService.activeAccountFlow } returns accountFlow
        coEvery { accountService.getCurrentAccount() } answers { accountFlow.value }
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns false

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val reactiveManager = ProductSelectionManager(
            productSelectionRepository = productSelectionRepository,
            accountService = Provider { accountService },
            appScope = scope,
        )
        advanceUntilIdle()

        // Baby-only account: weight must NOT be present yet.
        assertThat(reactiveManager.availableProducts.value).containsExactly(ProductSelection.BabyScale)

        // Pairing a weight scale adds "weight" to productTypes → the account flow re-emits.
        accountFlow.value = babyAndWeight
        advanceUntilIdle()

        assertThat(reactiveManager.availableProducts.value)
            .containsExactly(ProductSelection.MyWeight, ProductSelection.BabyScale)

        scope.cancel()
    }

    // ── dropdown visibility predicate (MOB-592) ──────────────────────────────
    // The product-type header shows its dropdown chevron only when there is more
    // than one product to switch between (availableProducts.size > 1). These tests
    // pin that predicate against each account configuration.

    @Test
    fun `dropdown hidden for weight-only account (single product)`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value.size > 1).isFalse()
    }

    @Test
    fun `dropdown shown when a BPM device adds a second product`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value.size > 1).isTrue()
    }

    @Test
    fun `dropdown shown when a baby profile adds a second product`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns listOf(baby1)
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value.size > 1).isTrue()
    }

    @Test
    fun `dropdown shown when baby scale (no profiles) adds a second product`() = runTest {
        coEvery { productSelectionRepository.getBabyProfiles(ACCOUNT_ID) } returns emptyList()
        coEvery { productSelectionRepository.hasBpmDevice(ACCOUNT_ID) } returns false
        coEvery { productSelectionRepository.hasBabyScaleDevice(ACCOUNT_ID) } returns true

        manager.loadAvailableProducts(ACCOUNT_ID)

        assertThat(manager.availableProducts.value.size > 1).isTrue()
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
