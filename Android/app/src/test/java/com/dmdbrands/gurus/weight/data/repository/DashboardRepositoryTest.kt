package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.enums.ProgressKeyConstants
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DashboardRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var accountDao: AccountDao

    @MockK(relaxUnitFun = true)
    private lateinit var accountRepository: IAccountRepository

    private lateinit var repository: DashboardRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { accountDao.getDashboardSettings(any()) } returns flowOf(null)
        repository = DashboardRepository(accountDao, accountRepository)
    }

    // ── getVisibleMetricKeys ───────────────────────────────────────────────────

    @Test
    fun `getVisibleMetricKeys returns empty list when settings is null`() = runTest {
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(null)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getVisibleMetricKeys returns intersection of 4-metric dashboard and server metrics`() = runTest {
        val settings = buildDashboardSettings(
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            metrics = listOf(MetricKeyConstants.BMI, MetricKeyConstants.BODY_FAT),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MetricKey.BMI, MetricKey.BODY_FAT)
    }

    @Test
    fun `getVisibleMetricKeys filters server metrics not in 4-metric dashboard`() = runTest {
        // BMR is only available in DASHBOARD_12_METRICS, not DASHBOARD_4_METRICS
        val settings = buildDashboardSettings(
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            metrics = listOf(MetricKeyConstants.BMI, MetricKeyConstants.BMR),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MetricKey.BMI)
        assertThat(result).doesNotContain(MetricKey.BMR)
    }

    @Test
    fun `getVisibleMetricKeys returns metrics when dashboard type is DASHBOARD_12_METRICS`() = runTest {
        val settings = buildDashboardSettings(
            dashboardType = DashboardType.DASHBOARD_12_METRICS.value,
            metrics = listOf(MetricKeyConstants.BMI, MetricKeyConstants.BMR, MetricKeyConstants.METABOLIC_AGE),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MetricKey.BMI, MetricKey.BMR, MetricKey.METABOLIC_AGE)
    }

    @Test
    fun `getVisibleMetricKeys defaults to DASHBOARD_4_METRICS when dashboardType is unknown`() = runTest {
        // Settings with an unknown dashboardType string → should default to DASHBOARD_4_METRICS
        val settings = buildDashboardSettings(
            dashboardType = "unknown_type",
            metrics = listOf(MetricKeyConstants.BMI, MetricKeyConstants.BMR),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        // BMR not available in DASHBOARD_4_METRICS (the default)
        assertThat(result).containsExactly(MetricKey.BMI)
        assertThat(result).doesNotContain(MetricKey.BMR)
    }

    @Test
    fun `getVisibleMetricKeys filters invalid camelCase metric keys from server`() = runTest {
        val settings = buildDashboardSettings(
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            metrics = listOf(MetricKeyConstants.BMI, "invalidMetricKey"),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MetricKey.BMI)
    }

    @Test
    fun `getVisibleMetricKeys returns empty list when server metrics list is empty`() = runTest {
        val settings = buildDashboardSettings(
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            metrics = emptyList(),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getVisibleMetricKeys passes accountId to dao`() = runTest {
        repository.getVisibleMetricKeys(ACCOUNT_ID).first()

        coVerify { accountDao.getDashboardSettings(ACCOUNT_ID) }
    }

    // ── getVisibleMilestoneKeys ────────────────────────────────────────────────

    @Test
    fun `getVisibleMilestoneKeys returns empty list when settings is null`() = runTest {
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(null)

        val result = repository.getVisibleMilestoneKeys(ACCOUNT_ID).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getVisibleMilestoneKeys returns parsed milestone keys from server`() = runTest {
        val settings = buildDashboardSettings(
            milestones = listOf(ProgressKeyConstants.GOAL, ProgressKeyConstants.CURRENT_STREAK),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMilestoneKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MilestoneKey.TO_GOAL, MilestoneKey.CURRENT_STREAK)
    }

    @Test
    fun `getVisibleMilestoneKeys filters invalid milestone keys`() = runTest {
        val settings = buildDashboardSettings(
            milestones = listOf(ProgressKeyConstants.GOAL, "invalidMilestone"),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMilestoneKeys(ACCOUNT_ID).first()

        assertThat(result).containsExactly(MilestoneKey.TO_GOAL)
    }

    @Test
    fun `getVisibleMilestoneKeys returns all default milestones when all valid`() = runTest {
        val allMilestoneKeys = listOf(
            ProgressKeyConstants.GOAL,
            ProgressKeyConstants.CURRENT_STREAK,
            ProgressKeyConstants.LONGEST_STREAK,
            ProgressKeyConstants.WEEKLY_CHANGE,
            ProgressKeyConstants.MONTHLY_CHANGE,
            ProgressKeyConstants.YEARLY_CHANGE,
            ProgressKeyConstants.TOTAL_CHANGE,
        )
        val settings = buildDashboardSettings(milestones = allMilestoneKeys)
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(settings)

        val result = repository.getVisibleMilestoneKeys(ACCOUNT_ID).first()

        assertThat(result).hasSize(7)
        assertThat(result).containsAtLeast(MilestoneKey.TO_GOAL, MilestoneKey.CURRENT_STREAK)
    }

    // ── updateVisibleMetricKeys ────────────────────────────────────────────────

    @Test
    fun `updateVisibleMetricKeys converts MetricKeys to camelCase and calls accountRepository`() = runTest {
        val existingSettings = buildDashboardSettings(
            milestones = listOf(ProgressKeyConstants.GOAL),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(existingSettings)

        repository.updateVisibleMetricKeys(
            ACCOUNT_ID,
            listOf(MetricKey.BMI, MetricKey.BODY_FAT),
            DashboardType.DASHBOARD_4_METRICS,
        )

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = match { it.contains(MetricKeyConstants.BMI) && it.contains(MetricKeyConstants.BODY_FAT) },
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
            )
        }
    }

    @Test
    fun `updateVisibleMetricKeys preserves existing milestone keys from dao`() = runTest {
        val existingSettings = buildDashboardSettings(
            milestones = listOf(ProgressKeyConstants.GOAL, ProgressKeyConstants.CURRENT_STREAK),
        )
        every { accountDao.getDashboardSettings(ACCOUNT_ID) } returns flowOf(existingSettings)

        repository.updateVisibleMetricKeys(
            ACCOUNT_ID,
            listOf(MetricKey.BMI),
            DashboardType.DASHBOARD_4_METRICS,
        )

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = any(),
                dashboardMilestones = listOf(ProgressKeyConstants.GOAL, ProgressKeyConstants.CURRENT_STREAK),
                dashboardType = any(),
            )
        }
    }

    // ── updateVisibleMilestoneKeys ─────────────────────────────────────────────

    @Test
    fun `updateVisibleMilestoneKeys converts MilestoneKeys to camelCase and calls accountRepository`() = runTest {
        repository.updateVisibleMilestoneKeys(
            ACCOUNT_ID,
            listOf(MilestoneKey.TO_GOAL, MilestoneKey.CURRENT_STREAK),
        )

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = any(),
                dashboardMilestones = match {
                    it.contains(ProgressKeyConstants.GOAL) && it.contains(ProgressKeyConstants.CURRENT_STREAK)
                },
                dashboardType = any(),
            )
        }
    }

    // ── hasVisibleKeys ─────────────────────────────────────────────────────────

    @Test
    fun `hasVisibleKeys always returns true`() = runTest {
        val result = repository.hasVisibleKeys(ACCOUNT_ID)
        assertThat(result).isTrue()
    }

    // ── resetVisibleMetricKeys ─────────────────────────────────────────────────

    @Test
    fun `resetVisibleMetricKeys for DASHBOARD_4_METRICS uses default 4 metrics`() = runTest {
        repository.resetVisibleMetricKeys(ACCOUNT_ID, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = match { it.size == 4 },
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
            )
        }
    }

    @Test
    fun `resetVisibleMetricKeys for DASHBOARD_12_METRICS uses all 12 metrics`() = runTest {
        repository.resetVisibleMetricKeys(ACCOUNT_ID, DashboardType.DASHBOARD_12_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = match { it.size == 12 },
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
            )
        }
    }

    // ── resetVisibleMilestoneKeys ──────────────────────────────────────────────

    @Test
    fun `resetVisibleMilestoneKeys calls updateVisibleMilestoneKeys with all default milestones`() = runTest {
        repository.resetVisibleMilestoneKeys(ACCOUNT_ID)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = any(),
                dashboardMilestones = match { it.size == 7 },
                dashboardType = any(),
            )
        }
    }

    // ── resetVisibleKeys ───────────────────────────────────────────────────────

    @Test
    fun `resetVisibleKeys for DASHBOARD_4_METRICS uses default 4 metrics and all default milestones`() = runTest {
        repository.resetVisibleKeys(ACCOUNT_ID, DashboardType.DASHBOARD_4_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = MetricKeyConstants.DEFAULT_4_METRICS,
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
            )
        }
    }

    @Test
    fun `resetVisibleKeys for DASHBOARD_12_METRICS uses all metric keys`() = runTest {
        repository.resetVisibleKeys(ACCOUNT_ID, DashboardType.DASHBOARD_12_METRICS)

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = MetricKeyConstants.ALL_METRIC_KEYS,
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
            )
        }
    }

    @Test
    fun `updateVisibleMilestoneKeys with milestone key not in enum map uses lowercase fallback`() = runTest {
        // MilestoneKey.LONGEST_STREAK mapped to "longestStreak" in ProgressKeyConstants
        repository.updateVisibleMilestoneKeys(
            ACCOUNT_ID,
            listOf(MilestoneKey.LONGEST_STREAK),
        )

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = ACCOUNT_ID,
                dashboardMetrics = any(),
                dashboardMilestones = match { it.isNotEmpty() },
                dashboardType = any(),
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    companion object {
        private const val ACCOUNT_ID = "account1"
    }

    private fun buildDashboardSettings(
        accountId: String = ACCOUNT_ID,
        dashboardType: String = DashboardType.DASHBOARD_4_METRICS.value,
        metrics: List<String> = emptyList(),
        milestones: List<String> = emptyList(),
    ) = DashboardSettingsEntity(
        accountId = accountId,
        dashboardType = dashboardType,
        dashboardMetrics = metrics,
        dashboardMilestones = milestones,
        isSynced = true,
    )
}
