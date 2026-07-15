package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntrySource
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Suppress("LargeClass")
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
            appScope = TestScope(mainDispatcherRule.dispatcher),
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
    fun `init loads history detail and sets items`() = runTest(mainDispatcherRule.scheduler) {
        val entries = listOf(TestFixtures.weightEntry, TestFixtures.bodyFatEntry)
        viewModel = createViewModel(entries = entries)
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(2)
        assertThat(viewModel.state.value.month).isEqualTo(TEST_MONTH)
    }

    @Test
    fun `init filters only ScaleEntry instances`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `Refresh calls entryService syncOperations`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        coVerify { entryService.syncOperations() }
    }

    @Test
    fun `Refresh sets loading true then false`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `Refresh sets error message when syncOperations throws`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { entryService.syncOperations() } throws RuntimeException("Sync failed")
        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isEqualTo(ERROR_MESSAGE)
    }

    @Test
    fun `Refresh sets loading to false when syncOperations throws`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { entryService.syncOperations() } throws RuntimeException("Sync failed")
        viewModel.handleIntent(HistoryDetailIntent.Refresh)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — SetHistoryItems
    // -------------------------------------------------------------------------

    @Test
    fun `SetHistoryItems updates month and items`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `SetItemsOpened updates opened item ids`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `SetError updates error message`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetError("Something went wrong"))

        assertThat(viewModel.state.value.errorMessage).isEqualTo("Something went wrong")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `ClearError removes error message`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `SetRefreshing true sets isLoading to true`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.SetRefreshing(true))

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetRefreshing false sets isLoading to false`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `month parameter is accessible on viewModel`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel(month = "2024-06")
        advanceUntilIdle()

        assertThat(viewModel.month).isEqualTo("2024-06")
    }

    // -------------------------------------------------------------------------
    // loadHistoryDetail — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `loadHistoryDetail calls historyService getDetail with correct month`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel(month = "2024-03")
        advanceUntilIdle()

        verify { entryReadService.getDetail(any(), eq("2024-03")) }
    }

    @Test
    fun `loadHistoryDetail clears items and sets no error when entries are empty`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns flowOf(HistoryDetail.Weight(emptyList()))

        viewModel = createViewModelRaw()
        advanceUntilIdle()

        // Empty is a valid state (e.g. the last entry was just deleted), not an error (MOB-1462).
        assertThat(viewModel.state.value.historyItems).isEmpty()
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `loadHistoryDetail filters only ScaleEntry instances from results`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `loadHistoryDetail sets error on exception`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.getDetail(any(), any()) } throws RuntimeException("test error")

        viewModel = HistoryDetailViewModel(
            accountService = accountService,
            entryService = entryService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
            appScope = TestScope(mainDispatcherRule.dispatcher),
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
    fun `loadHistoryDetail sets month in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel(month = "2024-07")
        advanceUntilIdle()

        assertThat(viewModel.state.value.month).isEqualTo("2024-07")
    }

    // -------------------------------------------------------------------------
    // DeleteEntry — confirm alert + deferred delete with Undo
    // -------------------------------------------------------------------------

    /** Captures the enqueued delete-confirmation alert and invokes its confirm action. */
    private fun confirmDeleteDialog() {
        val dialog = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialog)) }
        (dialog.captured as DialogModel.Confirm).onConfirm?.invoke()
    }

    /** Captures the "Reading deleted · Undo" toast and taps Undo. */
    private fun tapUndo() {
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        (toasts.last { it is Toast.Simple } as Toast.Simple).action?.action?.invoke()
    }

    @Test
    fun `DeleteEntry shows the confirm alert and does not delete until confirmed`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(TestFixtures.weightEntry))

        // A "Delete this record?" alert is enqueued (Figma 29833-120461); nothing deleted yet.
        val dialog = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialog)) }
        val confirm = dialog.captured as DialogModel.Confirm
        assertThat(confirm.title).isEqualTo("Delete this record?")
        assertThat(confirm.message).isEqualTo("This reading will be removed from your history. This can't be undone.")
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
    }

    @Test
    fun `DeleteEntry cancelled does not delete`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(TestFixtures.weightEntry))
        val dialog = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialog)) }
        (dialog.captured as DialogModel.Confirm).onCancel?.invoke()
        advanceUntilIdle()

        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
    }

    @Test
    fun `Confirming delete hides the row and shows Undo toast without deleting yet`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()

        // Row hidden via the persisted flag; toast with Undo shown; NOT deleted until the window.
        coVerify { entryService.setPendingDelete(entry, true) }
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        val toast = toasts.last { it is Toast.Simple } as Toast.Simple
        assertThat(toast.message).isEqualTo("Reading deleted.")
        assertThat(toast.action?.text).isEqualTo("Undo")
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
    }

    @Test
    fun `Delete commits after the undo window elapses`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        advanceUntilIdle() // past the undo window → commit

        coVerify { entryService.deleteEntry(entry) }
        coVerify { healthConnectService.deleteEntry(entry) }
    }

    @Test
    fun `Undo cancels the delete and un-hides the row without deleting or restoring`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        tapUndo()
        advanceUntilIdle() // window would have elapsed, but the commit was cancelled

        // Undo clears the persisted flag (un-hides) and nothing is deleted or re-created.
        coVerify { entryService.setPendingDelete(entry, false) }
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
        // A "Reading restored successfully." confirmation toast is surfaced (Figma 29833-120448).
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        val restored = toasts.last { it is Toast.Simple } as Toast.Simple
        assertThat(restored.message).isEqualTo("Reading restored successfully.")
        assertThat(restored.icon).isNotNull()
        assertThat(restored.isError).isFalse()
    }

    @Test
    fun `Confirming delete commits a baby entry via entryService deleteEntry`() = runTest(mainDispatcherRule.scheduler) {
        // Baby (and BP) history rows share the same generic DeleteEntry intent — the service maps
        // them to the unified operationType=delete. (§2.16)
        viewModel = createViewModel()
        advanceUntilIdle()
        val babyEntry = aBabyEntry(id = 9L, weightDecigrams = 30000)

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(babyEntry))
        confirmDeleteDialog()
        advanceUntilIdle()

        coVerify { entryService.deleteEntry(babyEntry) }
    }

    @Test
    fun `Confirming delete commits a BP entry via entryService deleteEntry`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val bpEntry = aBpEntry(EntrySource.MANUAL.value)

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(bpEntry))
        confirmDeleteDialog()
        advanceUntilIdle()

        coVerify { entryService.deleteEntry(bpEntry) }
    }

    @Test
    fun `Commit still deletes when Health Connect throws`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { healthConnectService.deleteEntry(any()) } throws RuntimeException("HC error")
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        advanceUntilIdle()

        coVerify { entryService.deleteEntry(entry) }
    }

    @Test
    fun `Delete commit failure un-hides the row and shows Couldnt delete toast`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { entryService.deleteEntry(any()) } throws RuntimeException("db error")
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        advanceUntilIdle()

        // Failed commit re-shows the row (clears the flag) and surfaces the error (the last toast).
        coVerify { entryService.setPendingDelete(entry, false) }
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        val toast = toasts.last { it is Toast.Simple } as Toast.Simple
        // Error variant (Figma 29833-120449): red message + trash icon + a TRY AGAIN retry action.
        assertThat(toast.message).isEqualTo("Couldn't delete!")
        assertThat(toast.isError).isTrue()
        assertThat(toast.icon).isNotNull()
        assertThat(toast.action?.text).isEqualTo("Try again")
    }

    @Test
    fun `Try again on the delete-failed toast re-attempts the commit`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { entryService.deleteEntry(any()) } throws RuntimeException("db error")
        viewModel = createViewModel()
        advanceUntilIdle()
        val entry = TestFixtures.weightEntry

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        advanceUntilIdle() // first commit fails → error toast with TRY AGAIN

        // Tap TRY AGAIN on the error toast → re-hides the row and re-attempts the delete.
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        (toasts.last { it is Toast.Simple } as Toast.Simple).action?.action?.invoke()
        advanceUntilIdle()

        coVerify(atLeast = 2) { entryService.deleteEntry(entry) }
    }

    @Test
    fun `Deleting the last entry navigates back to History`() = runTest(mainDispatcherRule.scheduler) {
        val entry = TestFixtures.weightEntry
        viewModel = createViewModel(entries = listOf(entry))
        advanceUntilIdle() // initial load → one entry shown

        // After the delete-hide re-query, this detail has no entries left.
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns
            flowOf(HistoryDetail.Weight(emptyList()))

        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        confirmDeleteDialog()
        advanceUntilIdle()

        // Nothing left to show here → the ViewModel returns to the History screen (MOB-1173).
        coVerify { navigationService.navigateBack(topLevel = null) }
    }

    // -------------------------------------------------------------------------
    // Baby edit (operationType = edit, §2.16)
    // -------------------------------------------------------------------------

    @Test
    fun `SaveBabyEdit edits in place via editBabyEntry, not delete plus re-create`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val original = aBabyEntry(id = 7L, weightDecigrams = 30000)
        viewModel.handleIntent(
            HistoryDetailIntent.SaveBabyEdit(
                entry = original,
                weightDecigrams = 31000,
                lengthMillimeters = null,
                note = "after feed",
                timestamp = original.entry.entryTimestamp,
            ),
        )
        advanceUntilIdle()

        val slot = slot<BabyEntry>()
        coVerify { entryService.editBabyEntry(capture(slot)) }
        // The buggy old path (delete + re-create) must NOT run.
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
        coVerify(exactly = 0) { entryService.addEntry(any<Entry>()) }

        val saved = slot.captured
        assertThat(saved.entry.id).isEqualTo(original.entry.id) // same row id
        assertThat(saved.babyWeightDecigrams).isEqualTo(31000) // new value applied
        assertThat(saved.entryType).isEqualTo(BabyEntryType.WEIGHT.value)
        assertThat(saved.entryNote).isEqualTo("after feed")
    }

    @Test
    fun `SaveBabyEdit with changed date deletes original and creates a new reading`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { entryService.addBabyEntry(any()) } returns 1L
        val original = aBabyEntry(id = 7L, weightDecigrams = 30000, timestamp = "2024-01-01T08:00:00.000Z")
        viewModel.handleIntent(
            HistoryDetailIntent.SaveBabyEdit(
                entry = original,
                weightDecigrams = 30000,
                lengthMillimeters = null,
                note = "moved",
                timestamp = "2024-02-01T08:00:00.000Z", // different day → server entryId changes
            ),
        )
        advanceUntilIdle()

        // Date changed → delete the old (old entryId) + create a fresh row at the new timestamp,
        // so the old server reading isn't orphaned. No in-place edit on this path.
        coVerify { entryService.deleteEntry(original) }
        val added = slot<BabyEntry>()
        coVerify { entryService.addBabyEntry(capture(added)) }
        coVerify(exactly = 0) { entryService.editBabyEntry(any()) }
        assertThat(added.captured.entry.id).isEqualTo(0L) // new local id (autogen)
        assertThat(added.captured.entry.entryTimestamp).isEqualTo("2024-02-01T08:00:00.000Z")
        // Date changed → the entry moved off this month screen, so return to History. (MOB-1173)
        coVerify { navigationService.navigateBack(any()) }
    }

    @Test
    fun `SaveBabyEdit edits in place when only sub-minute precision differs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Original carries seconds/millis; the date picker re-emits the same minute as :00.000.
        val original = aBabyEntry(id = 7L, weightDecigrams = 30000, timestamp = "2024-01-01T08:00:30.123Z")
        viewModel.handleIntent(
            HistoryDetailIntent.SaveBabyEdit(
                entry = original,
                weightDecigrams = 31000,
                lengthMillimeters = null,
                note = "fixed weight",
                timestamp = "2024-01-01T08:00:00.000Z", // same minute, dropped sub-minute precision
            ),
        )
        advanceUntilIdle()

        // Same minute → in-place edit (operationType=edit), NOT delete+recreate.
        val slot = slot<BabyEntry>()
        coVerify { entryService.editBabyEntry(capture(slot)) }
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
        coVerify(exactly = 0) { entryService.addBabyEntry(any()) }
        // Keeps the ORIGINAL timestamp so the server entryId is unchanged.
        assertThat(slot.captured.entry.entryTimestamp).isEqualTo("2024-01-01T08:00:30.123Z")
        assertThat(slot.captured.babyWeightDecigrams).isEqualTo(31000)
        // Sub-minute-only change is NOT a date change → stay on the screen (no navigation).
        coVerify(exactly = 0) { navigationService.navigateBack(any()) }
    }

    private fun aBabyEntry(
        id: Long = 7L,
        babyId: String = "baby-1",
        weightDecigrams: Int? = 30000,
        timestamp: String = "2024-01-01T08:00:00.000Z",
    ): BabyEntry = BabyEntry(
        entry = EntryEntity(
            id = id,
            accountId = "test-account-id",
            entryTimestamp = timestamp,
            operationType = "create",
            deviceType = "baby",
            deviceId = "baby-1",
        ),
        babyEntry = BabyEntryEntity(
            id = id,
            babyId = babyId,
            babyWeightDecigrams = weightDecigrams,
            entryType = BabyEntryType.WEIGHT.value,
        ),
    )

    // -------------------------------------------------------------------------
    // isMetric state
    // -------------------------------------------------------------------------

    @Test
    fun `isMetric is true when account uses KG`() = runTest(mainDispatcherRule.scheduler) {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.KG)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isTrue()
    }

    @Test
    fun `isMetric is false when account uses LB`() = runTest(mainDispatcherRule.scheduler) {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.LB)
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isFalse()
    }

    @Test
    fun `isMetric updates reactively when account changes`() = runTest(mainDispatcherRule.scheduler) {
        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.LB)
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.isMetric).isFalse()

        activeAccountFlow.value = TestFixtures.anAccount(weightUnit = WeightUnit.KG)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isMetric).isTrue()
    }

    // -------------------------------------------------------------------------
    // loadDetail — empty entries and other product types
    // -------------------------------------------------------------------------

    @Test
    fun `loadDetail with empty weight entries clears items without error`() = runTest {
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns
            flowOf(HistoryDetail.Weight(emptyList()))
        viewModel = createViewModelRaw()
        advanceUntilIdle()

        // Empty result → clear the list, not an error (MOB-1462).
        assertThat(viewModel.state.value.historyItems).isEmpty()
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `deleting the last entry navigates back reactively`() = runTest {
        // getDetail is reactive: one entry first, then an empty emission after the last delete.
        val detailFlow = MutableStateFlow<HistoryDetail>(
            HistoryDetail.Weight(listOf(TestFixtures.weightEntry)),
        )
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns detailFlow
        viewModel = createViewModelRaw()
        advanceUntilIdle()
        assertThat(viewModel.state.value.historyItems).hasSize(1)

        // Last entry removed → the detail flow re-emits empty; the ViewModel returns to History
        // (no error). (MOB-1173)
        detailFlow.value = HistoryDetail.Weight(emptyList())
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(topLevel = null) }
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `loadDetail with blood pressure entries populates items`() = runTest {
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns
            flowOf(HistoryDetail.BloodPressure(listOf(TestFixtures.bpmEntry)))
        viewModel = createViewModelRaw()
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(1)
    }

    @Test
    fun `loadDetail with baby entries populates items`() = runTest {
        val babyEntry = mockk<com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry>(relaxed = true)
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } returns
            flowOf(HistoryDetail.Baby(listOf(babyEntry)))
        viewModel = createViewModelRaw()
        advanceUntilIdle()

        assertThat(viewModel.state.value.historyItems).hasSize(1)
    }

    @Test
    fun `loadDetail surfaces error when service throws`() = runTest {
        every { entryReadService.getDetail(any(), eq(TEST_MONTH)) } throws RuntimeException("boom")
        viewModel = createViewModelRaw()
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }


    // -------------------------------------------------------------------------
    // Weight edit routing by source (MOB-1173)
    // -------------------------------------------------------------------------

    /** Copies [TestFixtures.weightEntry] with the given measurement [source]. */
    private fun weightEntryWithSource(source: String?): ScaleEntry {
        val base = TestFixtures.weightEntry
        return base.copy(
            scale = base.scale.copy(scaleEntry = base.scale.scaleEntry.copy(source = source)),
        )
    }

    @Test
    fun `EditWeightEntry on a manual reading opens the full weight edit sheet`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val manual = weightEntryWithSource(EntrySource.MANUAL.value)

        viewModel.handleIntent(HistoryDetailIntent.EditWeightEntry(manual))
        advanceUntilIdle()

        // Manual → full value+note editor (weightEditEntry).
        assertThat(viewModel.state.value.weightEditEntry).isEqualTo(manual)
    }

    @Test
    fun `EditWeightEntry on a device-synced reading opens the same sheet (values disabled)`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val device = weightEntryWithSource(EntrySource.BLUETOOTH.value)

        viewModel.handleIntent(HistoryDetailIntent.EditWeightEntry(device))
        advanceUntilIdle()

        // Same weight edit sheet opens; the sheet disables everything but the note for device readings.
        assertThat(viewModel.state.value.weightEditEntry).isEqualTo(device)
    }

    @Test
    fun `EditWeightEntry on an unknown-source reading opens the same sheet (values disabled)`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val legacy = weightEntryWithSource(null)

        viewModel.handleIntent(HistoryDetailIntent.EditWeightEntry(legacy))
        advanceUntilIdle()

        // Null/legacy source is treated as non-manual (safe default: note-only, values disabled).
        assertThat(viewModel.state.value.weightEditEntry).isEqualTo(legacy)
    }

    @Test
    fun `SaveWeightEdit on a manual reading edits it in place via editEntry`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val original = weightEntryWithSource(EntrySource.MANUAL.value)
        val updated = weightEntryWithSource(EntrySource.MANUAL.value)

        viewModel.handleIntent(HistoryDetailIntent.SaveWeightEdit(original = original, updated = updated))
        advanceUntilIdle()

        // Manual: edit in place via the unified edit API — never delete/recreate.
        coVerify { entryService.editEntry(updated) }
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
        coVerify(exactly = 0) { entryService.addEntry(entry = any()) }
        assertThat(viewModel.state.value.weightEditEntry).isNull()
        // Date unchanged → stay on the current month screen (no navigation).
        coVerify(exactly = 0) { navigationService.navigateBack(any()) }
    }

    @Test
    fun `SaveWeightEdit that changes the date navigates back to History`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val original = weightEntryWithSource(EntrySource.MANUAL.value)
        // Same entry moved to a different day.
        val updated = original.copy(
            entry = original.entry.copy(entryTimestamp = "2024-02-15T09:30:00.000Z"),
        )

        viewModel.handleIntent(HistoryDetailIntent.SaveWeightEdit(original = original, updated = updated))
        advanceUntilIdle()

        // Date changed → the entry may leave this month screen, so return to History. (MOB-1173)
        coVerify { entryService.editEntry(updated) }
        coVerify { navigationService.navigateBack(any()) }
    }

    @Test
    fun `SaveWeightEdit on a device-synced reading only updates the note`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val original = weightEntryWithSource(EntrySource.BLUETOOTH.value)
        val updated = original.copy(
            scale = original.scale.copy(scaleEntry = original.scale.scaleEntry.copy(note = "new note")),
        )

        viewModel.handleIntent(HistoryDetailIntent.SaveWeightEdit(original = original, updated = updated))
        advanceUntilIdle()

        // Device-synced: values are immutable → persist the note in place, never edit values.
        coVerify { entryService.updateNote(original, "new note") }
        coVerify(exactly = 0) { entryService.editEntry(any()) }
        coVerify(exactly = 0) { entryService.deleteEntry(any()) }
        assertThat(viewModel.state.value.weightEditEntry).isNull()
    }

    // -------------------------------------------------------------------------
    // BP edit (MOB-1173)
    // -------------------------------------------------------------------------

    private fun aBpEntry(source: String?, timestamp: String = "2024-01-01T08:00:00.000Z"): BpmEntry = BpmEntry(
        entry = EntryEntity(
            id = 5L,
            accountId = "test-account-id",
            entryTimestamp = timestamp,
            operationType = "create",
            deviceType = "bpm",
            deviceId = "d",
        ),
        bpmEntry = BpmEntryEntity(
            id = 5L,
            systolic = 120,
            diastolic = 80,
            pulse = 70,
            meanArterial = "93",
            note = null,
            source = source,
        ),
    )

    @Test
    fun `EditBpEntry opens the BP edit sheet for any source`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val device = aBpEntry(EntrySource.BLUETOOTH.value)

        viewModel.handleIntent(HistoryDetailIntent.EditBpEntry(device))
        advanceUntilIdle()

        // Same sheet opens regardless of source; the sheet disables values for device readings.
        assertThat(viewModel.state.value.bpEditEntry).isEqualTo(device)
    }

    @Test
    fun `SaveBpEdit edits in place via editEntry and stays when the date is unchanged`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val original = aBpEntry(EntrySource.MANUAL.value)
        val updated = original.copy(bpmEntry = original.bpmEntry.copy(systolic = 118))

        viewModel.handleIntent(HistoryDetailIntent.SaveBpEdit(original = original, updated = updated))
        advanceUntilIdle()

        coVerify { entryService.editEntry(updated) }
        assertThat(viewModel.state.value.bpEditEntry).isNull()
        // Date unchanged → stay on the screen.
        coVerify(exactly = 0) { navigationService.navigateBack(any()) }
    }

    @Test
    fun `SaveBpEdit that changes the date navigates back to History`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val original = aBpEntry(EntrySource.MANUAL.value, timestamp = "2024-01-01T08:00:00.000Z")
        val updated = original.copy(entry = original.entry.copy(entryTimestamp = "2024-03-10T09:30:00.000Z"))

        viewModel.handleIntent(HistoryDetailIntent.SaveBpEdit(original = original, updated = updated))
        advanceUntilIdle()

        coVerify { entryService.editEntry(updated) }
        coVerify { navigationService.navigateBack(any()) }
    }

    private fun createViewModelRaw(month: String = TEST_MONTH): HistoryDetailViewModel =
        HistoryDetailViewModel(
            accountService = accountService,
            entryService = entryService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
            appScope = TestScope(mainDispatcherRule.dispatcher),
            month = month,
            productType = com.dmdbrands.gurus.weight.domain.enums.ProductType.MY_WEIGHT,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
        )
}
