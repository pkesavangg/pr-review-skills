package com.dmdbrands.gurus.weight.core.shared.utilities.browser

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
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
class CustomTabViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var customTabManager: ICustomTabManager

    private val chromeStateFlow = MutableStateFlow<ChromeTabState?>(null)
    private lateinit var viewModel: CustomTabViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { customTabManager.subscribeChromeState() } returns chromeStateFlow
    }

    private fun createViewModel(): CustomTabViewModel =
        CustomTabViewModel(customTabManager)

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial chromeTabState is Idle`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.chromeTabState.value).isEqualTo(ChromeTabState.Idle)
    }

    // -------------------------------------------------------------------------
    // Chrome State Subscription
    // -------------------------------------------------------------------------

    @Test
    fun `chromeTabState updates to TabShown when manager emits TabShown`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        chromeStateFlow.value = ChromeTabState.TabShown
        advanceUntilIdle()

        assertThat(viewModel.chromeTabState.value).isEqualTo(ChromeTabState.TabShown)
    }

    @Test
    fun `chromeTabState updates to TabHidden when manager emits TabHidden`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        chromeStateFlow.value = ChromeTabState.TabHidden
        advanceUntilIdle()

        assertThat(viewModel.chromeTabState.value).isEqualTo(ChromeTabState.TabHidden)
    }

    @Test
    fun `chromeTabState ignores null emissions from manager`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        chromeStateFlow.value = ChromeTabState.TabShown
        advanceUntilIdle()
        assertThat(viewModel.chromeTabState.value).isEqualTo(ChromeTabState.TabShown)

        chromeStateFlow.value = null
        advanceUntilIdle()

        // State should remain TabShown since null is filtered
        assertThat(viewModel.chromeTabState.value).isEqualTo(ChromeTabState.TabShown)
    }

    // -------------------------------------------------------------------------
    // launchTab
    // -------------------------------------------------------------------------

    @Test
    fun `launchTab calls customTabManager openChromeTab`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchTab("https://example.com")
        advanceUntilIdle()

        verify { customTabManager.openChromeTab("https://example.com") }
    }

    @Test
    fun `launchTab with different URL passes correct URL`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchTab("https://weightgurus.com")
        advanceUntilIdle()

        verify { customTabManager.openChromeTab("https://weightgurus.com") }
    }

    // -------------------------------------------------------------------------
    // onCleared
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared does not unbind the shared customTabManager`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Trigger onCleared by clearing the ViewModel
        // We use reflection to call onCleared since it's protected
        val onClearedMethod = viewModel.javaClass.superclass
            ?.getDeclaredMethod("onCleared")
        onClearedMethod?.isAccessible = true
        onClearedMethod?.invoke(viewModel)

        // CustomTabManager is a lifecycle-owned singleton; the ViewModel must NOT
        // unbind it on clear (unbinding is owned by the host Activity lifecycle).
        verify(exactly = 0) { customTabManager.unbind() }
    }
}
