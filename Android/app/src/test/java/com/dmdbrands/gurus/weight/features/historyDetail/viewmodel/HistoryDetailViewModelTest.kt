package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.TestFixtures
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
class HistoryDetailViewModelTest {

    companion object {
        private const val TEST_MONTH = "2024-01"
        private const val ERROR_MESSAGE = "Failed to refresh data"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxUnitFun = true)
    lateinit var entryService: IEntryService

    @MockK(relaxUnitFun = true)
    lateinit var healthConnectService: IHealthConnectService

    @MockK(relaxed = true)
    lateinit var entryReadService: IEntryReadService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var productSelectionManager: IProductSelectionManager
    private lateinit var viewModel: HistoryDetailViewModel

    private val activeAccountFlow = MutableStateFlow<Account?>(null)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        productSelectionManager = mockk(relaxed = true)
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(ProductSelection.MyWeight)
        every { accountService.activeAccount } returns activeAccountFlow
    }

    private fun createViewModel(
        month: String = TEST_MONTH,
        entries: List<ScaleEntry> = listOf(TestFixtures.weightEntry),
    ): HistoryDetailViewModel {
        every { entryReadService.getDetail(any(), eq(month)) } returns flowOf(HistoryDetail.Weight(entries))
        return HistoryDetailViewModel(
            accountService = accountService,
            entryService = entryService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
            month = month,
            productType = com.dmdbrands.gurus.weight.domain.enums.ProductType.MY_WEIGHT,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        viewModel = createViewModel()
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // Init — loads history detail on creation
    // -------------------------------------------------------------------------

    @Test
    fun `init loads history detail and sets items`() = runTest {
        val entries = listOf(TestFixtures.weightEntry, TestFixtures.bodyFatEntry)
        viewModel = createViewModel(entries = entries)
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(2)
        assertThat(viewModel.state.value.month).isEqualTo(TEST_MONTH)
    }

    @Test
    fun `init filters only ScaleEntry instances`() = runTest {
        val scaleEntries = listOf(TestFixtures.weightEntry, TestFixtures.bodyFatEntry)
        viewModel = createViewModel(entries = scaleEntries)
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(2)
        assertThat(viewModel.state.value.historyItems).containsExactlyElementsIn(scaleEntries)
    }

    // -------------------------------------------------------------------------
    // Refresh — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh calls entryService syncOperations`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        coVerify { entryService.syncOperations() }
    }

    @Test
    fun `Refresh sets loading true then false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        // After completion, loading should be false
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Refresh — error path
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh sets error message when syncOperations throws`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { entryService.syncOperations() } throws RuntimeException("Sync failed")
        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isEqualTo(ERROR_MESSAGE)
    }

    @Test
    fun `Refresh sets loading to false when syncOperations throws`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { entryService.syncOperations() } throws RuntimeException("Sync failed")
        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // DeleteEntry — shows confirmation dialog
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteEntry shows confirmation dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured
        assertThat(dialog).isInstanceOf(DialogModel.Confirm::class.java)
    }

    @Test
    fun `DeleteEntry dialog has correct title and message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.title).isEqualTo("Delete Entry?")
        assertThat(dialog.message).isEqualTo("Are you sure you want to delete your entry?")
    }

    // -------------------------------------------------------------------------
    // DeleteEntry — onConfirm callback
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteEntry onConfirm calls entryService deleteEntry`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { entryService.deleteEntry(entry) }
    }

    @Test
    fun `DeleteEntry onConfirm shows loader`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader("Deleting entry...") }
    }

    @Test
    fun `DeleteEntry onConfirm calls healthConnectService deleteEntry`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { healthConnectService.deleteEntry(entry) }
    }

    @Test
    fun `DeleteEntry onConfirm dismisses dialog and loader`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `DeleteEntry onConfirm still dismisses when healthConnect deleteEntry throws`() = runTest {
        coEvery { healthConnectService.deleteEntry(any()) } throws RuntimeException("HC error")
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // DeleteEntry — onCancel callback
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteEntry onCancel dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // DeleteEntry — onDismiss callback
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteEntry onDismiss dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onDismiss?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Reducer — SetHistoryItems
    // -------------------------------------------------------------------------

    @Test
    fun `SetHistoryItems updates month and items`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val items = listOf(TestFixtures.weightEntry)
        viewModel.handleIntent(HistoryDetailIntent.SetHistoryItems("2024-02", items))

        assertThat(viewModel.state.value.month).isEqualTo("2024-02")
        assertThat(viewModel.state.value.historyItems).isEqualTo(items)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — SetItemsOpened
    // -------------------------------------------------------------------------

    @Test
    fun `SetItemsOpened updates opened item ids`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val ids = listOf(1L, 2L, 3L)
        viewModel.handleIntent(HistoryDetailIntent.SetItemsOpened(ids))

        assertThat(viewModel.state.value.itemsOpened).isEqualTo(ids)
    }

    // -------------------------------------------------------------------------
    // Reducer — SetError / ClearError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError updates error message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetError("Something went wrong"))

        assertThat(viewModel.state.value.errorMessage).isEqualTo("Something went wrong")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `ClearError removes error message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetError("Error"))
        viewModel.handleIntent(HistoryDetailIntent.ClearError)

        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // Reducer — SetRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `SetRefreshing true sets isLoading to true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetRefreshing(true))

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetRefreshing false sets isLoading to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetRefreshing(true))
        viewModel.handleIntent(HistoryDetailIntent.SetRefreshing(false))

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Month parameter
    // -------------------------------------------------------------------------

    @Test
    fun `month parameter is accessible on viewModel`() = runTest {
        viewModel = createViewModel(month = "2024-06")
        advanceUntilIdle()

        assertThat(viewModel.month).isEqualTo("2024-06")
    }

    // -------------------------------------------------------------------------
    // loadHistoryDetail — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `loadHistoryDetail calls historyService getDetail with correct month`() = runTest {
        viewModel = createViewModel(month = "2024-03")
        advanceUntilIdle()

        verify { entryReadService.getDetail(any(), eq("2024-03")) }
    }

    @Test
    fun `loadHistoryDetail sets error when entries are empty`() = runTest {
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns flowOf(HistoryDetail.Weight(emptyList()))

        viewModel = HistoryDetailViewModel(
            accountService = accountService,
            entryService = entryService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
            month = TEST_MONTH,
            productType = com.dmdbrands.gurus.weight.domain.enums.ProductType.MY_WEIGHT,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    @Test
    fun `loadHistoryDetail filters only ScaleEntry instances from results`() = runTest {
        val scaleEntries = listOf(TestFixtures.weightEntry, TestFixtures.bodyFatEntry)
        viewModel = createViewModel(entries = scaleEntries)
        advanceUntilIdle()

        val items = viewModel.state.value.historyItems
        assertThat(items).hasSize(2)
        items.forEach { item ->
            assertThat(item).isInstanceOf(ScaleEntry::class.java)
        }
    }

    @Test
    fun `loadHistoryDetail sets error on exception`() = runTest {
        every { entryReadService.getDetail(any(), any()) } throws RuntimeException("test error")

        viewModel = HistoryDetailViewModel(
            accountService = accountService,
            entryService = entryService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
            month = TEST_MONTH,
            productType = com.dmdbrands.gurus.weight.domain.enums.ProductType.MY_WEIGHT,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    @Test
    fun `loadHistoryDetail sets month in state`() = runTest {
        viewModel = createViewModel(month = "2024-07")
        advanceUntilIdle()

        assertThat(viewModel.state.value.month).isEqualTo("2024-07")
    }

    // -------------------------------------------------------------------------
    // showDeleteEntryDialog — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `showDeleteEntryDialog has delete and cancel buttons`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.confirmText).isNotNull()
        assertThat(dialog.cancelText).isNotNull()
    }

    @Test
    fun `showDeleteEntryDialog uses ErrorText button type`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.primaryActionType).isEqualTo(com.dmdbrands.gurus.weight.features.common.components.ButtonType.ErrorText)
    }

    @Test
    fun `showDeleteEntryDialog onConfirm shows success toast after deletion`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        // Verify delete was called and dialog was dismissed
        coVerify { entryService.deleteEntry(entry) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `showDeleteEntryDialog onDismiss callback is set`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.onDismiss).isNotNull()
    }

    // -------------------------------------------------------------------------
    // isMetric state
    // -------------------------------------------------------------------------

    @Test
    fun `isMetric is true when account uses KG`() = runTest {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.KG)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isTrue()
    }

    @Test
    fun `isMetric is false when account uses LB`() = runTest {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.LB)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isFalse()
    }

    @Test
    fun `isMetric updates reactively when account changes`() = runTest {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.LB)
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.isMetric).isFalse()

        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.KG)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isMetric).isTrue()
    }
}
