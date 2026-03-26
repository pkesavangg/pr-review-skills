package com.dmdbrands.gurus.weight.features.history.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
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

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: HistoryViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    private fun stubDefaultFlows() {
        every { entryService.monthlyAverage } returns MutableStateFlow(emptyList())
        every { entryService.isUpdating } returns MutableStateFlow(false)
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
    fun `isUpdating flow sets loading state`() = runTest {
        val isUpdatingFlow = MutableStateFlow(false)
        every { entryService.isUpdating } returns isUpdatingFlow

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
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
    fun `Refresh calls entryService syncOperations`() = runTest {
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
    fun `Export confirm callback calls exportService`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { exportService.exportCsvWithPrompt() }
    }

    @Test
    fun `Export confirm shows loader and dismisses it`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(HistoryIntent.Export)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Export confirm dismisses loader on exception`() = runTest {
        coEvery { exportService.exportCsvWithPrompt() } throws RuntimeException("export fail")
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
    fun `OnConnectScale navigates to AddEditScales`() = runTest {
        viewModel.handleIntent(HistoryIntent.OnConnectScale)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.AddEditScales) }
    }

    // -------------------------------------------------------------------------
    // monthlyAverage subscription
    // -------------------------------------------------------------------------

    @Test
    fun `monthlyAverage flow updates history items`() = runTest {
        val items = listOf(mockk<com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth>(relaxed = true))
        every { entryService.monthlyAverage } returns MutableStateFlow(items)

        viewModel = HistoryViewModel(
            entryService = entryService,
            exportService = exportService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
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
