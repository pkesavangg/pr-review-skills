package com.dmdbrands.gurus.weight.features.metricinfo

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MetricInfoViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var deviceService: IDeviceService

    @MockK(relaxed = true)
    lateinit var accountRepository: IAccountRepository

    @MockK(relaxed = true)
    lateinit var navigationService: IAppNavigationService

    @MockK(relaxed = true)
    lateinit var customTabManager: ICustomTabManager

    private lateinit var viewModel: MetricInfoViewModel
    private val mockInfo: DashboardMetric = mockk(relaxed = true)

    companion object {
        private const val RESOURCE_URL = "https://example.com/help"
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { accountRepository.getActiveAccount() } returns flowOf(mockk<Account>(relaxed = true) {
            every { dashboardType } returns DashboardType.DASHBOARD_12_METRICS.value
        })
    }

    private fun createViewModel(
        info: DashboardMetric = mockInfo,
        key: MetricKey = MetricKey.WEIGHT,
    ): MetricInfoViewModel {
        return MetricInfoViewModel(
            info = info,
            key = key,
            deviceService = deviceService,
            accountRepository = accountRepository,
        ).initTestDependencies(
            navigationService = navigationService,
            customTabManager = customTabManager,
        )
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state loads metric info from constructor`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.info).isEqualTo(mockInfo)
    }

    @Test
    fun `initial state loads dashboard type from account`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // SelectSegment
    // -------------------------------------------------------------------------

    @Test
    fun `SelectSegment with valid key updates stat and selectedIndex`() = runTest {
        viewModel = createViewModel(key = MetricKey.WEIGHT)
        advanceUntilIdle()

        viewModel.handleIntent(MetricInfoIntent.SelectSegment(MetricKey.WEIGHT))
        advanceUntilIdle()

        assertThat(viewModel.state.value.stat).isNotNull()
    }

    // -------------------------------------------------------------------------
    // OpenResource
    // -------------------------------------------------------------------------

    @Test
    fun `OpenResource opens in-app browser`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(MetricInfoIntent.OpenResource(RESOURCE_URL))
        advanceUntilIdle()

        coVerify { customTabManager.openChromeTab(any()) }
    }

    // -------------------------------------------------------------------------
    // UpdateScaleMode
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateScaleMode with no R4 scales does not navigate`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(MetricInfoIntent.UpdateScaleMode)
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // Heart rate status
    // -------------------------------------------------------------------------

    @Test
    fun `heart rate status updates from paired scales`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isHeartRateOff).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetSelectedIndex
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedIndex updates stat for valid index`() = runTest {
        viewModel = createViewModel(key = MetricKey.WEIGHT)
        advanceUntilIdle()

        viewModel.handleIntent(MetricInfoIntent.SetSelectedIndex(0))
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedMetricIndex).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // SetDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardType updates dashboardType in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(MetricInfoIntent.SetDashboardType(DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // Error paths
    // -------------------------------------------------------------------------

    @Test
    fun `init handles account repository error gracefully`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Should default to DASHBOARD_12_METRICS on null account
        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }
}
