package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
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
    // DeleteEntry — optimistic delete + undo (MOB-598)
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteEntry deletes and shows Reading deleted toast with Undo`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        coVerify { entryService.deleteEntry(entry) }
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        val toast = toasts.first() as Toast.Simple
        assertThat(toast.message).isEqualTo("Reading deleted.")
        assertThat(toast.action?.text).isEqualTo("Undo")
    }

    @Test
    fun `DeleteEntry also deletes from Health Connect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        coVerify { healthConnectService.deleteEntry(entry) }
    }

    @Test
    fun `DeleteEntry still shows deleted toast when Health Connect throws`() = runTest {
        coEvery { healthConnectService.deleteEntry(any()) } throws RuntimeException("HC error")
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        assertThat((toasts.first() as Toast.Simple).message).isEqualTo("Reading deleted.")
    }

    @Test
    fun `DeleteEntry shows Couldnt delete toast when deleteEntry fails`() = runTest {
        coEvery { entryService.deleteEntry(any()) } throws RuntimeException("db error")
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        val toast = toasts.first() as Toast.Simple
        assertThat(toast.title).isEqualTo("Couldn't delete!")
        assertThat(toast.message).isEqualTo("Try again")
    }

    @Test
    fun `Undo restores the entry and shows Reading restored toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val entry = TestFixtures.weightEntry
        viewModel.handleIntent(HistoryDetailIntent.DeleteEntry(entry))
        advanceUntilIdle()

        val deleted = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(deleted)) }
        (deleted.first() as Toast.Simple).action?.action?.invoke()
        advanceUntilIdle()

        coVerify { entryService.restoreEntry(entry) }
        val all = mutableListOf<Toast>()
        verify(atLeast = 2) { dialogQueueService.showToast(capture(all)) }
        assertThat((all.last() as Toast.Simple).message).isEqualTo("Reading restored.")
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
