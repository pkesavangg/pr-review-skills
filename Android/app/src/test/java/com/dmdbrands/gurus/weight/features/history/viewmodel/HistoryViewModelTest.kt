package com.dmdbrands.gurus.weight.features.history.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var entryService: IEntryService

    @MockK(relaxed = true)
    lateinit var exportService: IExportService

    @MockK(relaxed = true)
    lateinit var entryReadService: IEntryReadService

    @MockK(relaxed = true)
    lateinit var entryCursorPager: com.dmdbrands.gurus.weight.data.services.EntryCursorPager

    @MockK(relaxed = true)
    lateinit var deviceService: IDeviceService

    @MockK(relaxed = true)
    lateinit var productSelectionRepository: IProductSelectionRepository

    @MockK(relaxed = true)
    lateinit var userDataStore: com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var productSelectionManager: IProductSelectionManager

    private lateinit var viewModel: HistoryViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        productSelectionManager = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
    }

    private fun stubDefaultFlows() {
        every { userDataStore.babyWeightUnitForCurrentAccountFlow } returns
            flowOf(com.dmdbrands.gurus.weight.domain.model.common.WeightUnit.LB_OZ)
        every { entryService.isUpdating } returns MutableStateFlow(false)
        // observeAndLoadHistory() collects availableProducts on init; without a real flow the
        // relaxed mock emits a default value that fails the List<ProductSelection> cast.
        every { productSelectionManager.availableProducts } returns
            MutableStateFlow(listOf(ProductSelection.MyWeight))
        every { productSelectionManager.selectedProduct } returns
            MutableStateFlow(ProductSelection.MyWeight)
        // observeDeviceFlags() collects pairedScales as its change trigger, then reads presence
        // from the hardened per-product sources. (MOB-1221)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.hasWeightScale } returns MutableStateFlow(false)
        every { productSelectionManager.hasBabyScaleDevice } returns MutableStateFlow(false)
        coEvery { productSelectionRepository.hasBpmDevice(any()) } returns false
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.historyItems).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Loading intent via isUpdating subscription
    // -------------------------------------------------------------------------

    @Test
    fun `isUpdating flow sets loading state`() = runTest(mainDispatcherRule.scheduler) {
        val isUpdatingFlow = MutableStateFlow(false)
        every { entryService.isUpdating } returns isUpdatingFlow

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        isUpdatingFlow.value = true
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh calls entryService syncOperations`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HistoryIntent.Refresh)
        advanceUntilIdle()
        coVerify { entryService.syncOperations() }
    }

    // -------------------------------------------------------------------------
    // Export — enqueues confirm dialog
    // -------------------------------------------------------------------------

    @Test
    fun `Export enqueues confirm dialog`() {
        viewModel.handleIntent(HistoryIntent.Export)
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `Export confirm callback exports the active product category`() = runTest(mainDispatcherRule.scheduler) {
        // Default selection is MyWeight → category "weight", no babyId.
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { exportService.exportEntriesCsv(category = "weight", babyId = null, download = false) }
    }

    @Test
    fun `Export for a baby passes baby category and babyId`() = runTest(mainDispatcherRule.scheduler) {
        val baby = ProductSelection.Baby(
            BabyProfile(id = "baby-1", accountId = "acc-1", name = "Emma"),
        )
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(baby)
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { exportService.exportEntriesCsv(category = "baby", babyId = "baby-1", download = false) }
    }

    @Test
    fun `Export confirm shows loader and dismisses it`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Export confirm dismisses loader on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { exportService.exportEntriesCsv(any(), any(), any()) } throws RuntimeException("export fail")
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // OnConnectScale — navigation
    // -------------------------------------------------------------------------

    @Test
    fun `OnConnectScale navigates to MyDevices`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HistoryIntent.OnConnectScale)
        advanceUntilIdle()
      coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyDevices) }
    }

    // -------------------------------------------------------------------------
    // OnLogManually — navigation (MOB-1221)
    // -------------------------------------------------------------------------

    @Test
    fun `OnLogManually navigates to Entry under Home top-level`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HistoryIntent.OnLogManually)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home) }
    }

    // -------------------------------------------------------------------------
    // Device flags derived from the hardened per-product sources (MOB-1221 / PR #2242)
    // -------------------------------------------------------------------------

    @Test
    fun `device flags come from hardened per-product sources`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.hasWeightScale } returns MutableStateFlow(true)
        every { productSelectionManager.hasBabyScaleDevice } returns MutableStateFlow(true)
        every { entryReadService.accountId } returns "acct-1"
        coEvery { productSelectionRepository.hasBpmDevice("acct-1") } returns true

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasWeightDevice).isTrue()
        assertThat(viewModel.state.value.hasBpmDevice).isTrue()
        assertThat(viewModel.state.value.hasBabyDevice).isTrue()
    }

    @Test
    fun `weight-only ownership sets only the weight flag`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.hasWeightScale } returns MutableStateFlow(true)
        every { productSelectionManager.hasBabyScaleDevice } returns MutableStateFlow(false)
        every { entryReadService.accountId } returns "acct-1"
        coEvery { productSelectionRepository.hasBpmDevice("acct-1") } returns false

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasWeightDevice).isTrue()
        assertThat(viewModel.state.value.hasBpmDevice).isFalse()
        assertThat(viewModel.state.value.hasBabyDevice).isFalse()
    }

    @Test
    fun `bpm device is not queried when accountId is null`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.accountId } returns null

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasBpmDevice).isFalse()
        coVerify(exactly = 0) { productSelectionRepository.hasBpmDevice(any()) }
    }

    // -------------------------------------------------------------------------
    // historyService grouped history subscription
    // -------------------------------------------------------------------------

    @Test
    fun `getGroupedHistory updates history items`() = runTest(mainDispatcherRule.scheduler) {
        val items = listOf(mockk<com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth>(relaxed = true))
        every { entryReadService.accountId } returns "test-account"
        every { entryReadService.getGroupedHistory(any()) } returns flowOf(GroupedHistory.Weight(items))
        val productSelectionManager = mockk<IProductSelectionManager>(relaxed = true)
        every { productSelectionManager.availableProducts } returns MutableStateFlow(listOf(ProductSelection.MyWeight))
        every { productSelectionManager.hasBabyScaleDevice } returns MutableStateFlow(false)

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
            entryReadService = entryReadService,
            entryCursorPager = entryCursorPager,
            deviceService = deviceService,
            productSelectionRepository = productSelectionRepository,
            userDataStore = userDataStore,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // Pure state — reducer intents via ViewModel
    // -------------------------------------------------------------------------

    @Test
    fun `Loading intent updates isLoading`() {
        viewModel.handleIntent(HistoryIntent.Loading(true))
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetError sets errorMessage and clears loading`() {
        viewModel.handleIntent(HistoryIntent.Loading(true))
        viewModel.handleIntent(HistoryIntent.SetError("Timeout"))
        assertThat(viewModel.state.value.errorMessage).isEqualTo("Timeout")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `ClearError nullifies errorMessage`() {
        viewModel.handleIntent(HistoryIntent.SetError("error"))
        viewModel.handleIntent(HistoryIntent.ClearError)
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `Retry sets isLoading to true`() {
        viewModel.handleIntent(HistoryIntent.Retry)
        assertThat(viewModel.state.value.isLoading).isTrue()
    }
}
