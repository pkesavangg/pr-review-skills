package com.dmdbrands.gurus.weight.features.common.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [BaseViewModel].
 *
 * BaseViewModel is abstract, so we use a concrete test subclass to verify
 * its injected dependencies and utility methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var customTabManager: ICustomTabManager

    private lateinit var viewModel: ConcreteBaseViewModel

    /** Concrete test subclass of BaseViewModel for testing. */
    class ConcreteBaseViewModel : BaseViewModel() {
        // Expose protected/lateinit fields for test assertions
        val exposedNavigationService get() = navigationService
        val exposedDialogQueueService get() = dialogQueueService
        val exposedCustomTabManager get() = customTabManager
    }

    @BeforeEach
    fun setUp() {
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        customTabManager = mockk(relaxed = true)

        viewModel = ConcreteBaseViewModel().initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )
    }

    // -------------------------------------------------------------------------
    // Dependency injection via initTestDependencies
    // -------------------------------------------------------------------------

    @Test
    fun `navigationService is injected correctly`() {
        assertThat(viewModel.exposedNavigationService).isEqualTo(navigationService)
    }

    @Test
    fun `dialogQueueService is injected correctly`() {
        assertThat(viewModel.exposedDialogQueueService).isEqualTo(dialogQueueService)
    }

    @Test
    fun `customTabManager is injected correctly`() {
        assertThat(viewModel.exposedCustomTabManager).isEqualTo(customTabManager)
    }

    // -------------------------------------------------------------------------
    // openInAppBrowser
    // -------------------------------------------------------------------------

    @Test
    fun `openInAppBrowser delegates to customTabManager openChromeTab`() {
        val url = "https://www.example.com"

        viewModel.openInAppBrowser(url)

        verify { customTabManager.openChromeTab(url) }
    }

    @Test
    fun `openInAppBrowser passes exact URL to customTabManager`() {
        val url = "https://support.weightgurus.com/help"

        viewModel.openInAppBrowser(url)

        verify { customTabManager.openChromeTab("https://support.weightgurus.com/help") }
    }

    @Test
    fun `openInAppBrowser with empty URL does not crash`() {
        viewModel.openInAppBrowser("")

        verify { customTabManager.openChromeTab("") }
    }

    // -------------------------------------------------------------------------
    // initTestDependencies with defaults
    // -------------------------------------------------------------------------

    @Test
    fun `initTestDependencies with no args uses relaxed mocks`() {
        val vm = ConcreteBaseViewModel().initTestDependencies()

        // Should not throw — fields are initialized with relaxed mocks
        assertThat(vm.exposedNavigationService).isNotNull()
        assertThat(vm.exposedDialogQueueService).isNotNull()
        assertThat(vm.exposedCustomTabManager).isNotNull()
    }

    @Test
    fun `initTestDependencies returns the same viewmodel instance`() {
        val original = ConcreteBaseViewModel()
        val result = original.initTestDependencies()

        assertThat(result).isSameInstanceAs(original)
    }

    // -------------------------------------------------------------------------
    // Multiple ViewModels can share dependencies
    // -------------------------------------------------------------------------

    @Test
    fun `multiple viewmodels can use same navigation service mock`() {
        val sharedNav = mockk<IAppNavigationService>(relaxed = true)
        val vm1 = ConcreteBaseViewModel().initTestDependencies(navigationService = sharedNav)
        val vm2 = ConcreteBaseViewModel().initTestDependencies(navigationService = sharedNav)

        assertThat(vm1.exposedNavigationService).isSameInstanceAs(vm2.exposedNavigationService)
    }
}
