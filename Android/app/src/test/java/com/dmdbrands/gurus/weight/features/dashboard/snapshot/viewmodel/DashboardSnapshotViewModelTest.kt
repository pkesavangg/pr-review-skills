package com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel

import android.content.Context
import android.content.res.Resources
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardSnapshotViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK(relaxUnitFun = true)
    lateinit var entryReadService: IEntryReadService

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    private lateinit var productSelectionManager: IProductSelectionManager
    private lateinit var accountRepository: IAccountRepository
    private lateinit var viewModel: DashboardSnapshotViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        productSelectionManager = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        stubRawResources()
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    /**
     * The ViewModel calls [BabyPercentileHelper.loadIfNeeded] which reads WHO percentile
     * CSVs from res/raw via [Resources.openRawResource]. A relaxed Context mock returns a
     * relaxed InputStream whose read() yields 0 bytes, which makes BufferedReader throw
     * "Underlying input stream returned zero bytes". Return a real, valid CSV stream
     * (8 columns satisfies both the percentile and measurement parsers) on each open call.
     */
    private fun stubRawResources() {
        val resources = mockk<Resources>(relaxed = true)
        every { context.resources } returns resources
        every { resources.openRawResource(any()) } answers {
            ByteArrayInputStream(RAW_CSV.toByteArray())
        }
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { entryReadService.snapshots } returns MutableStateFlow(emptyMap())
        every { productSelectionManager.availableProducts } returns MutableStateFlow(emptyList())
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(ProductSelection.MyWeight)
    }

    private fun createViewModel(): DashboardSnapshotViewModel =
        DashboardSnapshotViewModel(
            context = context,
            entryReadService = entryReadService,
            accountService = accountService,
            accountRepository = accountRepository,
        ).initTestDependencies(
            productSelectionManager = productSelectionManager,
        )

    @Test
    fun `snapshot collapses multiple babies to a single active-baby card`() = runTest(mainDispatcherRule.scheduler) {
        val baby1 = ProductSelection.Baby(BabyProfile(id = "b1", name = "Ann", accountId = "acc"))
        val baby2 = ProductSelection.Baby(BabyProfile(id = "b2", name = "Bob", accountId = "acc"))
        val baby3 = ProductSelection.Baby(BabyProfile(id = "b3", name = "Cy", accountId = "acc"))
        every { productSelectionManager.availableProducts } returns
            MutableStateFlow(listOf(ProductSelection.MyWeight, baby1, baby2, baby3))
        coEvery { accountRepository.getActiveBabyId() } returns "b2"

        viewModel = createViewModel()
        advanceUntilIdle()

        val products = viewModel.snapshotProducts.value
        val babies = products.filterIsInstance<ProductSelection.Baby>()
        // Three baby profiles collapse to one snapshot card — the active baby.
        assertThat(babies).hasSize(1)
        assertThat(babies.first().profile.id).isEqualTo("b2")
        // Non-baby products are untouched.
        assertThat(products.any { it is ProductSelection.MyWeight }).isTrue()
    }

    @Test
    fun `snapshot scopes the baby card to the selected baby over a stale active id`() = runTest(mainDispatcherRule.scheduler) {
        val baby1 = ProductSelection.Baby(BabyProfile(id = "b1", name = "Ann", accountId = "acc"))
        val baby2 = ProductSelection.Baby(BabyProfile(id = "b2", name = "Bob", accountId = "acc"))
        every { productSelectionManager.availableProducts } returns
            MutableStateFlow(listOf(baby1, baby2))
        // The switcher bottom sheet selected baby-2; the persisted Room activeBabyId is still baby-1.
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(baby2)
        coEvery { accountRepository.getActiveBabyId() } returns "b1"

        viewModel = createViewModel()
        advanceUntilIdle()

        val babies = viewModel.snapshotProducts.value.filterIsInstance<ProductSelection.Baby>()
        assertThat(babies).hasSize(1)
        // MOB-1449: the snapshot follows the live selection, not the stale active id.
        assertThat(babies.first().profile.id).isEqualTo("b2")
    }

    @Test
    fun `snapshot falls back to first baby when no active id is set`() = runTest(mainDispatcherRule.scheduler) {
        val baby1 = ProductSelection.Baby(BabyProfile(id = "b1", name = "Ann", accountId = "acc"))
        val baby2 = ProductSelection.Baby(BabyProfile(id = "b2", name = "Bob", accountId = "acc"))
        every { productSelectionManager.availableProducts } returns
            MutableStateFlow(listOf(baby1, baby2))
        coEvery { accountRepository.getActiveBabyId() } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        val babies = viewModel.snapshotProducts.value.filterIsInstance<ProductSelection.Baby>()
        assertThat(babies).hasSize(1)
        assertThat(babies.first().profile.id).isEqualTo("b1")
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has isLoading true`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `initial state has default weight unit`() {
        val state = viewModel.state.value
        assertThat(state.weightUnit).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `initial state has empty chart data`() {
        val state = viewModel.state.value
        assertThat(state.weight.label).isEmpty()
        assertThat(state.bp.label).isEmpty()
        assertThat(state.baby).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Empty Products — Empty State
    // -------------------------------------------------------------------------

    @Test
    fun `empty snapshots produces empty chart state and clears loading`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.snapshots } returns MutableStateFlow(emptyMap())
        every { productSelectionManager.availableProducts } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.weight.label).isEmpty()
        assertThat(state.bp.label).isEmpty()
        assertThat(state.baby).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Reducer — SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading false clears loading`() {
        viewModel.handleIntent(DashboardSnapshotIntent.SetLoading(false))
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `SetLoading true sets loading`() {
        viewModel.handleIntent(DashboardSnapshotIntent.SetLoading(false))
        viewModel.handleIntent(DashboardSnapshotIntent.SetLoading(true))
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // Reducer — SetWeightUnit
    // -------------------------------------------------------------------------

    @Test
    fun `SetWeightUnit updates unit`() {
        viewModel.handleIntent(DashboardSnapshotIntent.SetWeightUnit(WeightUnit.KG))
        assertThat(viewModel.state.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // Reducer — SetWeightChart
    // -------------------------------------------------------------------------

    @Test
    fun `SetWeightChart updates weight chart data`() {
        val data = SnapshotChartData(label = "150.5", yMin = 140.0, yMax = 160.0)
        viewModel.handleIntent(DashboardSnapshotIntent.SetWeightChart(data))
        assertThat(viewModel.state.value.weight).isEqualTo(data)
    }

    // -------------------------------------------------------------------------
    // Reducer — SetBpChart
    // -------------------------------------------------------------------------

    @Test
    fun `SetBpChart updates bp chart data`() {
        val data = SnapshotChartData(label = "120/80", secondaryLabel = "72")
        viewModel.handleIntent(DashboardSnapshotIntent.SetBpChart(data))
        assertThat(viewModel.state.value.bp).isEqualTo(data)
    }

    // -------------------------------------------------------------------------
    // Reducer — SetBabyChart
    // -------------------------------------------------------------------------

    @Test
    fun `SetBabyChart adds entry to baby map`() {
        val data = SnapshotChartData(label = "8 lbs 4.0 oz")
        viewModel.handleIntent(DashboardSnapshotIntent.SetBabyChart("baby-1", data))
        assertThat(viewModel.state.value.baby).containsKey("baby-1")
        assertThat(viewModel.state.value.baby["baby-1"]).isEqualTo(data)
    }

    @Test
    fun `SetBabyChart accumulates multiple babies`() {
        val data1 = SnapshotChartData(label = "8 lbs")
        val data2 = SnapshotChartData(label = "10 lbs")
        viewModel.handleIntent(DashboardSnapshotIntent.SetBabyChart("baby-1", data1))
        viewModel.handleIntent(DashboardSnapshotIntent.SetBabyChart("baby-2", data2))
        assertThat(viewModel.state.value.baby).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // getBabyModelProducer
    // -------------------------------------------------------------------------

    @Test
    fun `getBabyModelProducer returns same instance for same profileId`() {
        val producer1 = viewModel.getBabyModelProducer("baby-1")
        val producer2 = viewModel.getBabyModelProducer("baby-1")
        assertThat(producer1).isSameInstanceAs(producer2)
    }

    @Test
    fun `getBabyModelProducer returns different instances for different profileIds`() {
        val producer1 = viewModel.getBabyModelProducer("baby-1")
        val producer2 = viewModel.getBabyModelProducer("baby-2")
        assertThat(producer1).isNotSameInstanceAs(producer2)
    }

    // TODO: Add tests for:
    // - observeWeightUnit updates when account flow emits new weight unit
    // - updateWeightChart produces correct label from WeightSnapshotPoint data
    // - updateBpChart produces correct systolic/diastolic label
    // - updateBabyChart produces correct lbs/oz label with percentile data
    // - loadAllGraphs combines snapshots + products correctly
    // - weightModelProducer receives data after updateWeightChart
    // - bpModelProducer receives data after updateBpChart
    // - babyModelProducers receives data after updateBabyChart

    companion object {
        private const val TAG = "DashboardSnapshotVMTest"

        /** Minimal valid CSV (header + 2 data rows); 8 columns covers both raw parsers. */
        private val RAW_CSV = buildString {
            appendLine("Day,5th,10th,25th,50th,75th,90th,95th")
            appendLine("0,25427,27200,30166,33464,36762,39728,41501")
            appendLine("8,26568,28402,31469,34879,38289,41356,43190")
        }
    }
}
