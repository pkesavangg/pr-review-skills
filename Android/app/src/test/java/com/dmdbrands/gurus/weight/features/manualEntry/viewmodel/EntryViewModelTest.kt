package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    companion object {
        private const val SUCCESS_TOAST_TITLE = "Success!"
        private const val ERROR_TOAST_TITLE = "Error saving new entry!"
        private const val NETWORK_ERROR = "Network error"
        private const val TEST_HEIGHT = 1700
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var entryService: IEntryService

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxUnitFun = true)
    lateinit var appSyncService: IAppSyncService

    @MockK(relaxed = true)
    lateinit var deviceService: IDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: EntryViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { appSyncService.appSyncDataForEditing } returns MutableStateFlow(null)
        every { deviceService.hasBluetoothWifiScale } returns flowOf(false)
    }

    private fun createViewModel(): EntryViewModel =
        EntryViewModel(
            entryService = entryService,
            accountService = accountService,
            appSyncService = appSyncService,
            deviceService = deviceService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.weightMode).isEqualTo(WeightUnit.LB)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isMetricFieldsExpandedInitially).isFalse()
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces form in state`() {
        val newForm = MultiFormGroup.create(forms = EntryForm.create())
        viewModel.handleIntent(EntryIntent.UpdateForm(newForm))
        assertThat(viewModel.state.value.form).isEqualTo(newForm)
    }

    // -------------------------------------------------------------------------
    // UpdateWeightUnit
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateWeightUnit sets weightMode to KG`() {
        viewModel.handleIntent(EntryIntent.UpdateWeightUnit(WeightUnit.KG))
        assertThat(viewModel.state.value.weightMode).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // UpdateDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateDashboardType updates dashboardType`() {
        viewModel.handleIntent(EntryIntent.UpdateDashboardType(DashboardType.DASHBOARD_12_METRICS))
        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // UpdateMetricFieldsExpandedStatus
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMetricFieldsExpandedStatus sets isMetricFieldsExpandedInitially`() {
        viewModel.handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(true))
        assertThat(viewModel.state.value.isMetricFieldsExpandedInitially).isTrue()
    }

    // -------------------------------------------------------------------------
    // Save — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `Save calls entryService addEntry`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        coVerify { entryService.addEntry(entry = any()) }
    }

    @Test
    fun `Save shows loader and dismisses it after success`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Save shows success toast on success`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        verify { dialogQueueService.showToast(match<Toast> { it.title == SUCCESS_TOAST_TITLE }) }
    }

    @Test
    fun `Save navigates back to Home on success`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        coVerify { navigationService.navigateBack(AppRoute.Home) }
    }

    @Test
    fun `Save clears appSync data on success`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        coVerify { appSyncService.setAppSyncDataForEditing(null) }
    }

    @Test
    fun `Save calls deactivate on success`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        coVerify { navigationService.unregisterOnDeactivate(AppRoute.Main.Entry) }
    }

    // -------------------------------------------------------------------------
    // Save — error path
    // -------------------------------------------------------------------------

    @Test
    fun `Save shows error toast when addEntry throws`() = runTest {
        coEvery { entryService.addEntry(entry = any()) } throws RuntimeException(NETWORK_ERROR)
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        verify { dialogQueueService.showToast(match<Toast> { it.title == ERROR_TOAST_TITLE }) }
    }

    @Test
    fun `Save dismisses loader when addEntry throws`() = runTest {
        coEvery { entryService.addEntry(entry = any()) } throws RuntimeException("fail")
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Save does not navigate back when addEntry throws`() = runTest {
        coEvery { entryService.addEntry(entry = any()) } throws RuntimeException("fail")
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        coVerify(exactly = 0) { navigationService.navigateBack(any()) }
    }

    // -------------------------------------------------------------------------
    // EarlyExit
    // -------------------------------------------------------------------------

    @Test
    fun `EarlyExit registers deactivation handler`() = runTest {
        viewModel.handleIntent(EntryIntent.EarlyExit)
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }

    // -------------------------------------------------------------------------
    // initDeactivate
    // -------------------------------------------------------------------------

    @Test
    fun `initDeactivate registers deactivation handler`() = runTest {
        viewModel.initDeactivate { }
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }

    // -------------------------------------------------------------------------
    // deactivate
    // -------------------------------------------------------------------------

    @Test
    fun `deactivate unregisters handler and clears appSync data`() = runTest {
        viewModel.deactivate()
        advanceUntilIdle()
        coVerify { navigationService.unregisterOnDeactivate(AppRoute.Main.Entry) }
        coVerify { appSyncService.setAppSyncDataForEditing(null) }
    }

    @Test
    fun `deactivate sets isMetricFieldsExpandedInitially to false`() = runTest {
        viewModel.handleIntent(EntryIntent.UpdateMetricFieldsExpandedStatus(true))
        assertThat(viewModel.state.value.isMetricFieldsExpandedInitially).isTrue()

        viewModel.deactivate()
        advanceUntilIdle()
        assertThat(viewModel.state.value.isMetricFieldsExpandedInitially).isFalse()
    }

    // -------------------------------------------------------------------------
    // Flow subscriptions — weight unit
    // -------------------------------------------------------------------------

    @Test
    fun `subscribes to activeAccountFlow and updates weightMode`() = runTest {
        val account = TestFixtures.anAccount(isActiveAccount = true, isLoggedIn = true)
            .copy(weightUnit = WeightUnit.KG)
        every { accountService.activeAccountFlow } returns flowOf(account)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.weightMode).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // Flow subscriptions — device scale → dashboard type
    // -------------------------------------------------------------------------

    @Test
    fun `hasBluetoothWifiScale true sets DASHBOARD_12_METRICS`() = runTest {
        every { deviceService.hasBluetoothWifiScale } returns flowOf(true)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `hasBluetoothWifiScale false sets DASHBOARD_4_METRICS`() = runTest {
        every { deviceService.hasBluetoothWifiScale } returns flowOf(false)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // AppSync data loading
    // -------------------------------------------------------------------------

    @Test
    fun `appSync data pre-fills form when scaleEntry emitted`() = runTest {
        val scaleEntry = TestFixtures.weightEntry
        every { appSyncService.appSyncDataForEditing } returns MutableStateFlow(scaleEntry)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetricFieldsExpandedInitially).isTrue()
    }

    // -------------------------------------------------------------------------
    // UpdateOnRelaunch
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateOnRelaunch refreshes form when no appSync data`() = runTest {
        val initialForm = viewModel.state.value.form
        viewModel.handleIntent(EntryIntent.UpdateOnRelaunch)
        advanceUntilIdle()
        // Form should be recreated (new instance)
        assertThat(viewModel.state.value.form).isNotEqualTo(initialForm)
    }

    @Test
    fun `UpdateOnRelaunch does not replace form when appSync data exists`() = runTest {
        val scaleEntry = TestFixtures.weightEntry
        every { appSyncService.appSyncDataForEditing } returns MutableStateFlow(scaleEntry)

        viewModel = createViewModel()
        advanceUntilIdle()

        val formBeforeRelaunch = viewModel.state.value.form
        viewModel.handleIntent(EntryIntent.UpdateOnRelaunch)
        advanceUntilIdle()

        assertThat(viewModel.state.value.form).isEqualTo(formBeforeRelaunch)
    }

    // -------------------------------------------------------------------------
    // LoadAppSyncData
    // -------------------------------------------------------------------------

    @Test
    fun `LoadAppSyncData creates form with scaleEntry data`() = runTest {
        val scaleEntry = TestFixtures.weightEntry
        viewModel.handleIntent(EntryIntent.LoadAppSyncData(scaleEntry, height = TEST_HEIGHT))
        advanceUntilIdle()
        // The form should be updated with the scale entry weight
        val weight = viewModel.state.value.form.forms.weightDateTime.controls.weight.value
        assertThat(weight).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // earlyExit and Exit public methods
    // -------------------------------------------------------------------------

    @Test
    fun `earlyExit calls Exit which registers deactivate handler`() = runTest {
        viewModel.earlyExit()
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }

    @Test
    fun `Exit registers deactivation handler`() = runTest {
        viewModel.Exit()
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }
}
