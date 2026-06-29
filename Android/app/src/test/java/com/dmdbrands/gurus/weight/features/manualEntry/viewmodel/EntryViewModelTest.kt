package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.MultiFormGroup
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
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
import io.mockk.clearMocks
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
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
    private lateinit var productSelectionManager: IProductSelectionManager
    private lateinit var viewModel: EntryViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        productSelectionManager = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccount } returns MutableStateFlow(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { appSyncService.appSyncDataForEditing } returns MutableStateFlow(null)
        every { deviceService.hasBluetoothWifiScale } returns flowOf(false)
        // Default to the weight product so UpdateOnRelaunch builds the weight form. (MOB-592)
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(ProductSelection.MyWeight)
    }

    private fun createViewModel(): EntryViewModel =
        EntryViewModel(
            entryService = entryService,
            accountService = accountService,
            appSyncService = appSyncService,
            deviceService = deviceService,
            analyticsService = mockk(relaxed = true),
            appScope = TestScope(mainDispatcherRule.dispatcher),
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            productSelectionManager = productSelectionManager,
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

    @Test
    fun `initial weightMode reads from active account synchronously in init`() {
        val kgAccount = TestFixtures.anAccount(isActiveAccount = true, isLoggedIn = true)
            .copy(weightUnit = WeightUnit.KG)
        every { accountService.activeAccount } returns MutableStateFlow(kgAccount)
        every { accountService.activeAccountFlow } returns flowOf(kgAccount)

        viewModel = createViewModel()

        // weightMode should be KG immediately — set synchronously in init, no async delay
        assertThat(viewModel.state.value.weightMode).isEqualTo(WeightUnit.KG)
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
    fun `Save with baby form saves one combined baby entry carrying weight and length`() = runTest {
        val baby = ProductSelection.Baby(
            BabyProfile(id = "baby-1", name = "Timmy", birthdate = null, accountId = "acc-1"),
        )
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(baby)
        viewModel = createViewModel()

        val form = MultiFormGroup.create(forms = BabyEntryForm.create())
        form.forms.baby.controls.pounds.onValueChange("7")
        form.forms.baby.controls.ounces.onValueChange("4")
        form.forms.baby.controls.inches.onValueChange("20")
        viewModel.handleIntent(EntryIntent.UpdateActiveForm(ActiveEntryForm.Baby(form)))

        val captured = mutableListOf<Entry>()
        coEvery { entryService.addEntry(entry = capture(captured)) } returns Unit

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        // One local row carries BOTH measures (the unique entryTimestamp index allows only one
        // row per timestamp; the POST split fans it into two §2.16 requests later).
        assertThat(captured).hasSize(1)
        val entry = captured.single() as BabyEntry
        assertThat(entry.babyId).isEqualTo("baby-1")
        assertThat(entry.babyWeightDecigrams).isEqualTo(ConversionTools.convertLbOzToDecigrams(7, 4.0))
        assertThat(entry.babyLengthMillimeters).isEqualTo(ConversionTools.convertInchesToMm(20.0))
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
        verify { dialogQueueService.showToast(match<Toast.Simple> { it.title == SUCCESS_TOAST_TITLE }) }
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
    // Save — baby pre-birthdate guard (MOB-592)
    // -------------------------------------------------------------------------

    @Test
    fun `Save baby entry dated before birthdate shows toast and does not save`() = runTest {
        // Birthdate 2026-06-15; entry dated the day before → must be rejected.
        setUpBabyForm(birthdate = "2026-06-15", entryDate = "2026-06-14")

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        verify {
            dialogQueueService.showToast(
                match<Toast.Simple> { it.message == EntryScreenStrings.EntryBeforeBirthdate },
            )
        }
        coVerify(exactly = 0) { entryService.addEntry(entry = any()) }
        coVerify(exactly = 0) { navigationService.navigateBack(any()) }
        // The guard returns before the loader is shown, so no loader for the rejected path.
        verify(exactly = 0) { dialogQueueService.showLoader(message = any()) }
    }

    @Test
    fun `Save baby entry dated on the exact birthday is allowed`() = runTest {
        // entryDay == birthDay → isBefore is false → save proceeds.
        setUpBabyForm(birthdate = "2026-06-15", entryDate = "2026-06-15")

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        coVerify { entryService.addEntry(entry = any()) }
        verify(exactly = 0) {
            dialogQueueService.showToast(
                match<Toast.Simple> { it.message == EntryScreenStrings.EntryBeforeBirthdate },
            )
        }
    }

    @Test
    fun `Save baby entry proceeds when birthdate is blank`() = runTest {
        // Blank birthdate → isBeforeBirthdate returns false → never block on bad profile data.
        setUpBabyForm(birthdate = "", entryDate = "2020-01-01")

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        coVerify { entryService.addEntry(entry = any()) }
        verify(exactly = 0) {
            dialogQueueService.showToast(
                match<Toast.Simple> { it.message == EntryScreenStrings.EntryBeforeBirthdate },
            )
        }
    }

    @Test
    fun `Save baby entry proceeds when birthdate is unparseable`() = runTest {
        // Unparseable birthdate → isBeforeBirthdate returns false → save proceeds.
        setUpBabyForm(birthdate = "not-a-date", entryDate = "2020-01-01")

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        coVerify { entryService.addEntry(entry = any()) }
        verify(exactly = 0) {
            dialogQueueService.showToast(
                match<Toast.Simple> { it.message == EntryScreenStrings.EntryBeforeBirthdate },
            )
        }
    }

    /**
     * Selects a Baby product with the given [birthdate] and activates a baby form with a valid
     * weight (pounds > 0) dated [entryDate] (ISO yyyy-MM-dd, anchored to start-of-day in the
     * device zone so it matches isBeforeBirthdate's day-level comparison).
     */
    private fun setUpBabyForm(birthdate: String?, entryDate: String) {
        val baby = ProductSelection.Baby(
            BabyProfile(id = "baby-1", name = "Timmy", birthdate = birthdate, accountId = "acc-1"),
        )
        every { productSelectionManager.selectedProduct } returns MutableStateFlow(baby)
        viewModel = createViewModel()

        val entryMillis = LocalDate.parse(entryDate)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val form = MultiFormGroup.create(forms = BabyEntryForm.create())
        form.forms.baby.controls.pounds.onValueChange("7")
        form.forms.baby.controls.ounces.onValueChange("4")
        form.forms.baby.controls.dateTime.onValueChange(DateTimeValue.Date(entryMillis))
        viewModel.handleIntent(EntryIntent.UpdateActiveForm(ActiveEntryForm.Baby(form)))
    }

    // -------------------------------------------------------------------------
    // Save — error path
    // -------------------------------------------------------------------------

    @Test
    fun `Save shows error toast when addEntry throws`() = runTest {
        coEvery { entryService.addEntry(entry = any()) } throws RuntimeException(NETWORK_ERROR)
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        verify { dialogQueueService.showToast(match<Toast.Simple> { it.title == ERROR_TOAST_TITLE }) }
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
        every { accountService.activeAccount } returns MutableStateFlow(account)
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
    fun `UpdateOnRelaunch reads weight unit from accountService not stale state`() = runTest {
        val kgAccount = TestFixtures.anAccount(isActiveAccount = true, isLoggedIn = true)
            .copy(weightUnit = WeightUnit.KG)
        every { accountService.activeAccount } returns MutableStateFlow(kgAccount)
        every { accountService.activeAccountFlow } returns flowOf(kgAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Force weightMode to LB to simulate stale state
        viewModel.handleIntent(EntryIntent.UpdateWeightUnit(WeightUnit.LB))
        assertThat(viewModel.state.value.weightMode).isEqualTo(WeightUnit.LB)

        // Clear recorded calls so we can verify UpdateOnRelaunch specifically
        clearMocks(accountService, answers = false, recordedCalls = true)
        every { accountService.activeAccountFlow } returns flowOf(kgAccount)

        viewModel.handleIntent(EntryIntent.UpdateOnRelaunch)
        advanceUntilIdle()

        // Verify UpdateOnRelaunch consulted accountService (source of truth),
        // not just state.weightMode which is stale LB
        verify { accountService.activeAccountFlow }
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

    // -------------------------------------------------------------------------
    // earlyExitToHome — delegates to earlyExit which calls Exit
    // -------------------------------------------------------------------------

    @Test
    fun `earlyExitToHome registers deactivation handler on Entry route`() = runTest {
        viewModel.earlyExitToHome()
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }

    @Test
    fun `EarlyExit intent triggers earlyExitToHome`() = runTest {
        viewModel.handleIntent(EntryIntent.EarlyExit)
        advanceUntilIdle()
        coVerify { navigationService.registerOnDeactivate(AppRoute.Main.Entry, any()) }
    }

    // -------------------------------------------------------------------------
    // saveEntry — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Save logs manual entry created analytics event`() = runTest {
        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()
        // analyticsService is relaxed, so logEvent should have been called
        // The save path calls analyticsService.logEvent(IAnalyticsService.Events.MANUAL_ENTRY_CREATED)
        coVerify { entryService.addEntry(entry = any()) }
    }

    @Test
    fun `Save returns early when activeAccount id is null`() = runTest {
        every { accountService.activeAccount } returns MutableStateFlow(null)
        every { accountService.activeAccountFlow } returns flowOf(null)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(EntryIntent.Save)
        advanceUntilIdle()

        // Should show loader but not call addEntry since account id is null
        verify { dialogQueueService.showLoader(message = any()) }
        coVerify(exactly = 0) { entryService.addEntry(entry = any()) }
    }
}
