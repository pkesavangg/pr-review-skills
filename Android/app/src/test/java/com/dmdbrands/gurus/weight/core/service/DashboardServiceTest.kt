package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.enums.ProgressKeyConstants
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val dashboardRepository: IDashboardRepository = mockk()
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: DashboardService

    // --- Test Fixtures ---
    private val accountId = "acc-1"

    private val fakeMetricKeys = listOf(MetricKey.BMI, MetricKey.BODY_FAT)
    private val fakeMilestoneKeys = listOf(MilestoneKey.TO_GOAL, MilestoneKey.CURRENT_STREAK)

    private fun fakeAccountInfo(
        dashboardType: String = "dashboard_4_metrics",
        dashboardMetrics: List<String> = listOf(MetricKeyConstants.BMI, MetricKeyConstants.BODY_FAT),
        progressMetrics: List<String>? = listOf(ProgressKeyConstants.GOAL),
    ) = AccountInfo(
        id = accountId,
        email = "test@example.com",
        firstName = "Test",
        lastName = "User",
        gender = "male",
        zipcode = "12345",
        weightUnit = "LB",
        isWeightlessOn = false,
        height = 170,
        activityLevel = "normal",
        dob = "1990-01-01",
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = dashboardType,
        dashboardMetrics = dashboardMetrics,
        progressMetrics = progressMetrics,
        goalWeight = null,
        initialWeight = null,
        shouldSendEntryNotifications = false,
        shouldSendWeightInEntryNotifications = false,
    )

    @Before
    fun setUp() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = true, unAvailable = false)
        every { dashboardRepository.getVisibleMetricKeys(any()) } returns flowOf(fakeMetricKeys)
        every { dashboardRepository.getVisibleMilestoneKeys(any()) } returns flowOf(fakeMilestoneKeys)
        coEvery { dashboardRepository.updateVisibleMetricKeys(any(), any(), any()) } just Runs
        coEvery { dashboardRepository.updateVisibleMilestoneKeys(any(), any()) } just Runs
        coEvery { dashboardRepository.hasVisibleKeys(any()) } returns true
        coEvery { dashboardRepository.resetVisibleMetricKeys(any(), any()) } just Runs
        coEvery { dashboardRepository.resetVisibleMilestoneKeys(any()) } just Runs
        coEvery { accountRepository.getAccountFromAPI(any()) } returns fakeAccountInfo()
        coEvery { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) } just Runs
        coEvery { accountRepository.updateDashboardMetrics(any()) } just Runs
        coEvery { accountRepository.updateProgressMetrics(any()) } just Runs

        service = DashboardService(
            dashboardRepository,
            accountRepository,
            connectivityObserver,
            dialogQueueService,
            appNavigationService,
            appScope = TestScope(mainDispatcherRule.dispatcher),
        )
    }

    // -------------------------------------------------------------------------
    // setAccountId — state init and account switching
    // -------------------------------------------------------------------------

    @Test
    fun `setAccountId clears visibleKeys before refreshing`() = runTest {
        // Pre-populate by setting an initial account
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Switch to a new account — visibleKeys should momentarily clear
        service.setAccountId("acc-2")

        // After clearing, refreshDashboard runs for the new account
        coVerify { accountRepository.getAccountFromAPI("acc-2") }
    }

    @Test
    fun `setAccountId calls refreshDashboard with new accountId`() = runTest {
        service.setAccountId(accountId)

        coVerify { accountRepository.getAccountFromAPI(accountId) }
    }

    @Test
    fun `setAccountId stores accountId for use by null-param methods`() = runTest {
        service.setAccountId(accountId)

        // hasVisibleKeys(null) should use the stored accountId
        service.hasVisibleKeys(null)

        coVerify { dashboardRepository.hasVisibleKeys(accountId) }
    }

    // -------------------------------------------------------------------------
    // setSelectedKey / getCurrentSelectedKey — selectedKey StateFlow
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedKey updates selectedKey StateFlow`() = runTest {
        val key = DashboardKey.Metric(MetricKey.BMI)

        service.setSelectedKey(key)

        assertThat(service.selectedKey.value).isEqualTo(key)
    }

    @Test
    fun `setSelectedKey accepts null to clear selected key`() = runTest {
        service.setSelectedKey(DashboardKey.Metric(MetricKey.BMI))
        service.setSelectedKey(null)

        assertThat(service.selectedKey.value).isNull()
    }

    @Test
    fun `getCurrentSelectedKey returns null initially`() {
        assertThat(service.getCurrentSelectedKey()).isNull()
    }

    @Test
    fun `getCurrentSelectedKey returns value after setSelectedKey`() = runTest {
        val key = DashboardKey.Milestone(MilestoneKey.TO_GOAL)
        service.setSelectedKey(key)

        assertThat(service.getCurrentSelectedKey()).isEqualTo(key)
    }

    @Test
    fun `selectedKey StateFlow emits new value when key changes`() = runTest {
        val key = DashboardKey.Metric(MetricKey.BODY_FAT)

        service.selectedKey.test {
            awaitItem() // initial null
            service.setSelectedKey(key)
            assertThat(awaitItem()).isEqualTo(key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // refreshDashboard — API sync and settings persistence
    // -------------------------------------------------------------------------

    @Test
    fun `refreshDashboard fetches account from API with provided accountId`() = runTest {
        service.refreshDashboard(accountId)

        coVerify { accountRepository.getAccountFromAPI(accountId) }
    }

    @Test
    fun `refreshDashboard uses stored accountId when null passed`() = runTest {
        service.setAccountId(accountId)
        coVerify { accountRepository.getAccountFromAPI(accountId) }

        service.refreshDashboard(null)

        coVerify(atLeast = 2) { accountRepository.getAccountFromAPI(accountId) }
    }

    @Test
    fun `refreshDashboard resolves DASHBOARD_4_METRICS type correctly with lowercase value`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(dashboardType = "dashboard_4_metrics")

        service.refreshDashboard(accountId)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `refreshDashboard resolves DASHBOARD_12_METRICS type correctly with lowercase value`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(dashboardType = "dashboard_12_metrics")

        service.refreshDashboard(accountId)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `refreshDashboard resolves DASHBOARD_12_METRICS type case-insensitively`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(dashboardType = "DASHBOARD_12_METRICS")

        service.refreshDashboard(accountId)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `refreshDashboard defaults to DASHBOARD_4_METRICS for unknown dashboardType`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(dashboardType = "unknown_type")

        service.refreshDashboard(accountId)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `refreshDashboard uses server progressMetrics when provided`() = runTest {
        val serverProgress = listOf(ProgressKeyConstants.GOAL, ProgressKeyConstants.CURRENT_STREAK)
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(progressMetrics = serverProgress)

        service.refreshDashboard(accountId)

        // Should NOT fall back to DB — no milestone repo calls
        coVerify(exactly = 0) { dashboardRepository.getVisibleMilestoneKeys(any()) }
    }

    @Test
    fun `refreshDashboard falls back to DB milestone keys when progressMetrics is null`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns
            fakeAccountInfo(progressMetrics = null)
        every { dashboardRepository.getVisibleMilestoneKeys(accountId) } returns
            flowOf(listOf(MilestoneKey.TO_GOAL))

        service.refreshDashboard(accountId)

        coVerify { dashboardRepository.getVisibleMilestoneKeys(accountId) }
    }

    @Test
    fun `refreshDashboard handles API exception gracefully`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(accountId) } throws RuntimeException("API error")

        service.refreshDashboard(accountId)

        coVerify(exactly = 0) { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refreshDashboard handles missing accountId gracefully`() = runTest {
        // Neither param nor stored accountId — IllegalStateException caught internally
        service.refreshDashboard(null)

        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    // -------------------------------------------------------------------------
    // getVisibleKeys — combined Flow of metrics + milestones
    // -------------------------------------------------------------------------

    @Test
    fun `getVisibleKeys emits combined metric and milestone DashboardKeys`() = runTest {
        service.setAccountId(accountId)

        service.getVisibleKeys(accountId).test {
            val keys = awaitItem()
            val metrics = keys.filterIsInstance<DashboardKey.Metric>()
            val milestones = keys.filterIsInstance<DashboardKey.Milestone>()
            assertThat(metrics.map { it.key }).isEqualTo(fakeMetricKeys)
            assertThat(milestones.map { it.key }).isEqualTo(fakeMilestoneKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getVisibleKeys uses stored accountId when null passed`() = runTest {
        service.setAccountId(accountId)

        service.getVisibleKeys(null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { dashboardRepository.getVisibleMetricKeys(accountId) }
    }

    @Test
    fun `getVisibleKeys throws IllegalStateException when no accountId available`() = runTest {
        var threw = false
        try {
            service.getVisibleKeys(null).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertThat(threw).isTrue()
    }

    // -------------------------------------------------------------------------
    // getVisibleMetricKeys — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun `getVisibleMetricKeys returns flow from repository`() = runTest {
        service.setAccountId(accountId)

        service.getVisibleMetricKeys(accountId).test {
            assertThat(awaitItem()).isEqualTo(fakeMetricKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getVisibleMetricKeys throws when no accountId available`() = runTest {
        var threw = false
        try {
            service.getVisibleMetricKeys(null).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertThat(threw).isTrue()
    }

    // -------------------------------------------------------------------------
    // getVisibleMilestoneKeys — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun `getVisibleMilestoneKeys returns flow from repository`() = runTest {
        service.setAccountId(accountId)

        service.getVisibleMilestoneKeys(accountId).test {
            assertThat(awaitItem()).isEqualTo(fakeMilestoneKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getVisibleMilestoneKeys throws when no accountId available`() = runTest {
        var threw = false
        try {
            service.getVisibleMilestoneKeys(null).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertThat(threw).isTrue()
    }

    // -------------------------------------------------------------------------
    // updateVisibleMetricKeys — repo update + StateFlow refresh
    // -------------------------------------------------------------------------

    @Test
    fun `updateVisibleMetricKeys calls repository with correct accountId and keys`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(MetricKey.BMI, MetricKey.MUSCLE_MASS)

        service.updateVisibleMetricKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS)

        coVerify { dashboardRepository.updateVisibleMetricKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS) }
    }

    @Test
    fun `updateVisibleMetricKeys uses stored accountId when null passed`() = runTest {
        service.setAccountId(accountId)

        service.updateVisibleMetricKeys(null, emptyList(), DashboardType.DASHBOARD_4_METRICS)

        coVerify { dashboardRepository.updateVisibleMetricKeys(accountId, emptyList(), DashboardType.DASHBOARD_4_METRICS) }
    }

    // -------------------------------------------------------------------------
    // updateVisibleMilestoneKeys — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun `updateVisibleMilestoneKeys calls repository with correct accountId and keys`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(MilestoneKey.TO_GOAL)

        service.updateVisibleMilestoneKeys(accountId, keys)

        coVerify { dashboardRepository.updateVisibleMilestoneKeys(accountId, keys) }
    }

    @Test
    fun `updateVisibleMilestoneKeys uses stored accountId when null passed`() = runTest {
        service.setAccountId(accountId)

        service.updateVisibleMilestoneKeys(null, emptyList())

        coVerify { dashboardRepository.updateVisibleMilestoneKeys(accountId, emptyList()) }
    }

    // -------------------------------------------------------------------------
    // updateVisibleKeys — online/offline split, camelCase conversion
    // -------------------------------------------------------------------------

    @Test
    fun `updateVisibleKeys calls updateDashboardSettings with isSynced=true when online`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(DashboardKey.Metric(MetricKey.BMI))

        service.updateVisibleKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `updateVisibleKeys calls API methods when online`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(
            DashboardKey.Metric(MetricKey.BMI),
            DashboardKey.Milestone(MilestoneKey.TO_GOAL),
        )

        service.updateVisibleKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS)

        coVerify { accountRepository.updateDashboardMetrics(any()) }
        coVerify { accountRepository.updateProgressMetrics(any()) }
    }

    @Test
    fun `updateVisibleKeys converts MetricKey to camelCase for API`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(DashboardKey.Metric(MetricKey.BMI), DashboardKey.Metric(MetricKey.BODY_FAT))

        service.updateVisibleKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardMetrics(
                listOf(MetricKeyConstants.BMI, MetricKeyConstants.BODY_FAT),
            )
        }
    }

    @Test
    fun `updateVisibleKeys converts MilestoneKey to camelCase for API`() = runTest {
        service.setAccountId(accountId)
        val keys = listOf(DashboardKey.Milestone(MilestoneKey.TO_GOAL), DashboardKey.Milestone(MilestoneKey.PER_WEEK))

        service.updateVisibleKeys(accountId, keys, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateProgressMetrics(
                listOf(ProgressKeyConstants.GOAL, ProgressKeyConstants.WEEKLY_CHANGE),
            )
        }
    }

    @Test
    fun `updateVisibleKeys calls updateDashboardSettings with isSynced=false when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        service.setAccountId(accountId)

        service.updateVisibleKeys(accountId, emptyList(), DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = any(),
                isSynced = false,
            )
        }
    }

    @Test
    fun `updateVisibleKeys does not call API methods when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        service.setAccountId(accountId)

        service.updateVisibleKeys(accountId, emptyList(), DashboardType.DASHBOARD_4_METRICS)

        coVerify(exactly = 0) { accountRepository.updateDashboardMetrics(any()) }
        coVerify(exactly = 0) { accountRepository.updateProgressMetrics(any()) }
    }

    @Test
    fun `updateVisibleKeys handles exception gracefully`() = runTest {
        service.setAccountId(accountId)
        coEvery { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) } throws RuntimeException("DB error")

        service.updateVisibleKeys(accountId, emptyList(), DashboardType.DASHBOARD_4_METRICS)

        // Should not crash — exception caught
    }

    // -------------------------------------------------------------------------
    // hasVisibleKeys — delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun `hasVisibleKeys returns true when repository returns true`() = runTest {
        service.setAccountId(accountId)
        coEvery { dashboardRepository.hasVisibleKeys(accountId) } returns true

        val result = service.hasVisibleKeys(accountId)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasVisibleKeys returns false when repository returns false`() = runTest {
        service.setAccountId(accountId)
        coEvery { dashboardRepository.hasVisibleKeys(accountId) } returns false

        val result = service.hasVisibleKeys(accountId)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // resetVisibleKeys — defaults + online/offline sync
    // -------------------------------------------------------------------------

    @Test
    fun `resetVisibleKeys uses DEFAULT_4_METRICS for DASHBOARD_4_METRICS type when online`() = runTest {
        service.setAccountId(accountId)

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = MetricKeyConstants.DEFAULT_4_METRICS,
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `resetVisibleKeys uses ALL_METRIC_KEYS for DASHBOARD_12_METRICS type when online`() = runTest {
        service.setAccountId(accountId)

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_12_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = MetricKeyConstants.ALL_METRIC_KEYS,
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `resetVisibleKeys calls API methods when online`() = runTest {
        service.setAccountId(accountId)

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        coVerify { accountRepository.updateDashboardMetrics(MetricKeyConstants.DEFAULT_4_METRICS) }
        coVerify { accountRepository.updateProgressMetrics(any()) }
    }

    @Test
    fun `resetVisibleKeys saves with isSynced=false when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        service.setAccountId(accountId)

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = accountId,
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = any(),
                isSynced = false,
            )
        }
    }

    @Test
    fun `resetVisibleKeys does not call API methods when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        service.setAccountId(accountId)

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        coVerify(exactly = 0) { accountRepository.updateDashboardMetrics(any()) }
        coVerify(exactly = 0) { accountRepository.updateProgressMetrics(any()) }
    }

    @Test
    fun `resetVisibleKeys handles exception gracefully`() = runTest {
        service.setAccountId(accountId)
        coEvery { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) } throws RuntimeException("DB error")

        service.resetVisibleKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        // Should not crash — exception caught
    }

    // -------------------------------------------------------------------------
    // resetVisibleMetricKeys / resetVisibleMilestoneKeys — delegate to repo
    // -------------------------------------------------------------------------

    @Test
    fun `resetVisibleMetricKeys delegates to repository`() = runTest {
        service.setAccountId(accountId)

        service.resetVisibleMetricKeys(accountId, DashboardType.DASHBOARD_4_METRICS)

        coVerify { dashboardRepository.resetVisibleMetricKeys(accountId, DashboardType.DASHBOARD_4_METRICS) }
    }

    @Test
    fun `resetVisibleMilestoneKeys delegates to repository`() = runTest {
        service.setAccountId(accountId)

        service.resetVisibleMilestoneKeys(accountId)

        coVerify { dashboardRepository.resetVisibleMilestoneKeys(accountId) }
    }
}
