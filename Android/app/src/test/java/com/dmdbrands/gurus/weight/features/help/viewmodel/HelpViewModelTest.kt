package com.dmdbrands.gurus.weight.features.help.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.features.help.model.HelpIntent
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HelpViewModelTest {

    companion object {
        private const val TEST_URL = "https://example.com/help"
        private const val ERROR_MESSAGE = "Something went wrong"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var dialogUtility: IDialogUtility

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var customTabManager: ICustomTabManager
    private lateinit var viewModel: HelpViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        customTabManager = mockk(relaxed = true)
        viewModel = HelpViewModel(
            dialogUtility = dialogUtility,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ShowModelNumberHelpPopup
    // -------------------------------------------------------------------------

    @Test
    fun `ShowModelNumberHelpPopup calls dialogUtility showModelNumberHelpDialog`() {
        viewModel.handleIntent(HelpIntent.ShowModelNumberHelpPopup)
        verify { dialogUtility.showModelNumberHelpDialog(any()) }
    }

    @Test
    fun `ShowModelNumberHelpPopup clears loading and error in state`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))
        viewModel.handleIntent(HelpIntent.ShowModelNumberHelpPopup)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HelpIntent.OnBack)
        advanceUntilIdle()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnBack clears loading and error in state`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))
        viewModel.handleIntent(HelpIntent.OnBack)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenDebugMenu
    // -------------------------------------------------------------------------

    @Test
    fun `OpenDebugMenu navigates to DebugMenu route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HelpIntent.OpenDebugMenu)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.DebugMenu) }
    }

    @Test
    fun `OpenDebugMenu clears loading and error in state`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))
        viewModel.handleIntent(HelpIntent.OpenDebugMenu)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenUrl
    // -------------------------------------------------------------------------

    @Test
    fun `OpenUrl opens url in custom tab`() {
        viewModel.handleIntent(HelpIntent.OpenUrl(TEST_URL))
        verify { customTabManager.openChromeTab(TEST_URL) }
    }

    @Test
    fun `OpenUrl clears loading and error in state`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))
        viewModel.handleIntent(HelpIntent.OpenUrl(TEST_URL))
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message in state`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))
        val state = viewModel.state.value
        assertThat(state.error).isEqualTo(ERROR_MESSAGE)
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `Error intent replaces previous error message`() {
        viewModel.handleIntent(HelpIntent.Error("first error"))
        viewModel.handleIntent(HelpIntent.Error("second error"))
        assertThat(viewModel.state.value.error).isEqualTo("second error")
    }

    // -------------------------------------------------------------------------
    // Public method — onOpenDebugMenu
    // -------------------------------------------------------------------------

    @Test
    fun `onOpenDebugMenu navigates to DebugMenu route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.onOpenDebugMenu()
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.DebugMenu) }
    }

    // -------------------------------------------------------------------------
    // onError — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent via handleIntent sets error and does not crash`() {
        viewModel.handleIntent(HelpIntent.Error(ERROR_MESSAGE))

        val state = viewModel.state.value
        assertThat(state.error).isEqualTo(ERROR_MESSAGE)
    }

    @Test
    fun `Error intent logs error message without side effects`() {
        // onError only calls AppLog.e, no dialog or navigation
        viewModel.handleIntent(HelpIntent.Error("Test error for logging"))

        // Verify no dialog or navigation was triggered
        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
        verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    }

    @Test
    fun `Error with empty message is handled`() {
        viewModel.handleIntent(HelpIntent.Error(""))

        assertThat(viewModel.state.value.error).isEmpty()
    }

    // -------------------------------------------------------------------------
    // OpenDebugMenu — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `OpenDebugMenu does not crash when navigation throws`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { navigationService.navigateTo(any()) } throws RuntimeException("nav error")

        viewModel.handleIntent(HelpIntent.OpenDebugMenu)
        advanceUntilIdle()

        // Should not propagate exception
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.DebugMenu) }
    }
}
