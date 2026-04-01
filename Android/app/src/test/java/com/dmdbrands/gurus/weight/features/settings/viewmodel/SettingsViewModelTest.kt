package com.dmdbrands.gurus.weight.features.settings.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.settings.manager.IDataSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.INotificationSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IProfileSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IScaleSettingsManager
import com.dmdbrands.gurus.weight.features.settings.manager.IUnitSettingsManager
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
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
class SettingsViewModelTest {

    companion object {
        private const val DEFAULT_THEME = "System Settings"
        private const val DEFAULT_MAC = "All"
        private const val ERROR_NETWORK = "Network failure"
        private const val ERROR_GENERIC = "error"
        private const val THEME_DARK = "Dark"
        private const val TEST_MAC_ADDRESS = "AA:BB:CC"
        private const val NOTIFICATION_OFF = "Off"
        private const val NOTIFICATION_ON = "On"
        private const val NOTIFICATION_ON_WEIGHT = "On w/ Weight"
        private const val UNREAD_FEED_COUNT = 5
        private const val UNREAD_FEED_COUNT_UPDATED = 7
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var profileSettingsManager: IProfileSettingsManager

    @MockK(relaxed = true)
    lateinit var unitSettingsManager: IUnitSettingsManager

    @MockK(relaxed = true)
    lateinit var notificationSettingsManager: INotificationSettingsManager

    @MockK(relaxed = true)
    lateinit var scaleSettingsManager: IScaleSettingsManager

    @MockK(relaxed = true)
    lateinit var dataSettingsManager: IDataSettingsManager

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var customTabManager: ICustomTabManager
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        customTabManager = mockk(relaxed = true)
        viewModel = SettingsViewModel(
            profileSettingsManager = profileSettingsManager,
            unitSettingsManager = unitSettingsManager,
            notificationSettingsManager = notificationSettingsManager,
            scaleSettingsManager = scaleSettingsManager,
            dataSettingsManager = dataSettingsManager,
            crashReportingService = mockk(relaxed = true),
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.account).isNull()
        assertThat(state.hasMultipleAccounts).isFalse()
        assertThat(state.currentThemeMode).isEqualTo(DEFAULT_THEME)
        assertThat(state.selectedMacAddress).isEqualTo(DEFAULT_MAC)
        assertThat(state.enableTestingFeatures).isFalse()
        assertThat(state.unreadFeedCount).isEqualTo(0)
        assertThat(state.showUnreadFeedIndication).isFalse()
        assertThat(state.isExportEnabled).isFalse()
        assertThat(state.hasKids).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — Manager Delegation
    // -------------------------------------------------------------------------

    @Test
    fun `init delegates to profileSettingsManager observeUserProfile`() {
        verify { profileSettingsManager.observeUserProfile(any(), any()) }
    }

    @Test
    fun `init delegates to dataSettingsManager loadCurrentThemeMode`() {
        verify { dataSettingsManager.loadCurrentThemeMode(any(), any()) }
    }

    @Test
    fun `init delegates to scaleSettingsManager loadMacAddressSettings`() {
        verify { scaleSettingsManager.loadMacAddressSettings(any(), any()) }
    }

    @Test
    fun `init delegates to notificationSettingsManager initFeedNotificationListener`() {
        verify { notificationSettingsManager.initFeedNotificationListener(any(), any()) }
    }

    @Test
    fun `init delegates to dataSettingsManager observeExportEnabled`() {
        verify { dataSettingsManager.observeExportEnabled(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `LoadSettings sets isLoading to true`() {
        viewModel.handleIntent(SettingsIntent.LoadSettings)
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetError sets errorMessage and clears isLoading`() {
        viewModel.handleIntent(SettingsIntent.LoadSettings) // isLoading = true
        viewModel.handleIntent(SettingsIntent.SetError(ERROR_NETWORK))
        assertThat(viewModel.state.value.errorMessage).isEqualTo(ERROR_NETWORK)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `ClearError nullifies errorMessage`() {
        viewModel.handleIntent(SettingsIntent.SetError(ERROR_GENERIC))
        viewModel.handleIntent(SettingsIntent.ClearError)
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `UpdateAccount sets account and hasMultipleAccounts`() {
        viewModel.handleIntent(SettingsIntent.UpdateAccount(TestFixtures.activeAccount, true))
        assertThat(viewModel.state.value.account).isEqualTo(TestFixtures.activeAccount)
        assertThat(viewModel.state.value.hasMultipleAccounts).isTrue()
    }

    @Test
    fun `UpdateThemeMode updates currentThemeMode`() {
        viewModel.handleIntent(SettingsIntent.UpdateThemeMode(THEME_DARK))
        assertThat(viewModel.state.value.currentThemeMode).isEqualTo(THEME_DARK)
    }

    @Test
    fun `UpdateSelectedMacAddress updates selectedMacAddress`() {
        viewModel.handleIntent(SettingsIntent.UpdateSelectedMacAddress(TEST_MAC_ADDRESS))
        assertThat(viewModel.state.value.selectedMacAddress).isEqualTo(TEST_MAC_ADDRESS)
    }

    @Test
    fun `UpdateTestingFeatures toggles enableTestingFeatures`() {
        viewModel.handleIntent(SettingsIntent.UpdateTestingFeatures(true))
        assertThat(viewModel.state.value.enableTestingFeatures).isTrue()
    }

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() {
        viewModel.handleIntent(SettingsIntent.SetUnreadFeedCount(UNREAD_FEED_COUNT))
        assertThat(viewModel.state.value.unreadFeedCount).isEqualTo(UNREAD_FEED_COUNT)
    }

    @Test
    fun `SetUnreadFeedCount with multiple calls reflects latest value`() {
        viewModel.handleIntent(SettingsIntent.SetUnreadFeedCount(UNREAD_FEED_COUNT))
        viewModel.handleIntent(SettingsIntent.SetUnreadFeedCount(UNREAD_FEED_COUNT_UPDATED))
        assertThat(viewModel.state.value.unreadFeedCount).isEqualTo(UNREAD_FEED_COUNT_UPDATED)
    }

    @Test
    fun `SetShowUnreadFeedIndication updates flag`() {
        viewModel.handleIntent(SettingsIntent.SetShowUnreadFeedIndication(true))
        assertThat(viewModel.state.value.showUnreadFeedIndication).isTrue()
    }

    @Test
    fun `SetExportEnabled updates isExportEnabled`() {
        viewModel.handleIntent(SettingsIntent.SetExportEnabled(true))
        assertThat(viewModel.state.value.isExportEnabled).isTrue()
    }

    @Test
    fun `SetHasKids updates hasKids`() {
        viewModel.handleIntent(SettingsIntent.SetHasKids(true))
        assertThat(viewModel.state.value.hasKids).isTrue()
    }

    @Test
    fun `SetHasKids defaults to false`() {
        assertThat(viewModel.state.value.hasKids).isFalse()
    }

    // -------------------------------------------------------------------------
    // Computed — currentNotificationStatus
    // -------------------------------------------------------------------------

    @Test
    fun `currentNotificationStatus is Off when account is null`() {
        assertThat(viewModel.state.value.currentNotificationStatus).isEqualTo(NOTIFICATION_OFF)
    }

    @Test
    fun `currentNotificationStatus is Off when notifications disabled`() {
        val account = TestFixtures.activeAccount.copy(shouldSendEntryNotifications = false)
        viewModel.handleIntent(SettingsIntent.UpdateAccount(account))
        assertThat(viewModel.state.value.currentNotificationStatus).isEqualTo(NOTIFICATION_OFF)
    }

    @Test
    fun `currentNotificationStatus is On when entry notifications enabled`() {
        val account = TestFixtures.activeAccount.copy(
            shouldSendEntryNotifications = true,
            shouldSendWeightInEntryNotifications = false,
        )
        viewModel.handleIntent(SettingsIntent.UpdateAccount(account))
        assertThat(viewModel.state.value.currentNotificationStatus).isEqualTo(NOTIFICATION_ON)
    }

    @Test
    fun `currentNotificationStatus is On w Weight when both enabled`() {
        val account = TestFixtures.activeAccount.copy(
            shouldSendEntryNotifications = true,
            shouldSendWeightInEntryNotifications = true,
        )
        viewModel.handleIntent(SettingsIntent.UpdateAccount(account))
        assertThat(viewModel.state.value.currentNotificationStatus).isEqualTo(NOTIFICATION_ON_WEIGHT)
    }

    // -------------------------------------------------------------------------
    // Navigation Intents
    // -------------------------------------------------------------------------

    @Test
    fun `OpenAddScales navigates to AddEditScales`() = runTest {
      viewModel.handleIntent(SettingsIntent.OpenMyDevices)
        advanceUntilIdle()
      coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyDevices) }
    }

    @Test
    fun `OpenHelp navigates to HelpScreen`() = runTest {
        viewModel.handleIntent(SettingsIntent.OpenHelp)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.HelpScreen) }
    }

    @Test
    fun `SwitchAccount navigates to MyAccounts`() = runTest {
        viewModel.handleIntent(SettingsIntent.SwitchAccount)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyAccounts) }
    }

    @Test
    fun `onSwitchAccountClick navigates to MyAccounts`() = runTest {
        viewModel.onSwitchAccountClick()
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyAccounts) }
    }

    // -------------------------------------------------------------------------
    // URL Opening Intents
    // -------------------------------------------------------------------------

    @Test
    fun `OpenPrivacyPolicy opens in-app browser`() {
        viewModel.handleIntent(SettingsIntent.OpenPrivacyPolicy)
        verify { customTabManager.openChromeTab(any()) }
    }

    @Test
    fun `OpenTermsOfService opens in-app browser`() {
        viewModel.handleIntent(SettingsIntent.OpenTermsOfService)
        verify { customTabManager.openChromeTab(any()) }
    }

    @Test
    fun `OpenGreaterGoodsWebsite opens in-app browser`() {
        viewModel.handleIntent(SettingsIntent.OpenGreaterGoodsWebsite)
        verify { customTabManager.openChromeTab(any()) }
    }

    // -------------------------------------------------------------------------
    // Manager Delegation Intents
    // -------------------------------------------------------------------------

    @Test
    fun `Logout delegates to dataSettingsManager onLogOutClick`() {
        viewModel.handleIntent(SettingsIntent.Logout)
        verify { dataSettingsManager.onLogOutClick(any(), any(), isLogoutAll = false) }
    }

    @Test
    fun `LogoutAllAccounts delegates to dataSettingsManager with isLogoutAll true`() {
        viewModel.handleIntent(SettingsIntent.LogoutAllAccounts)
        verify { dataSettingsManager.onLogOutClick(any(), any(), isLogoutAll = true) }
    }

    @Test
    fun `ExportData delegates to dataSettingsManager onExportDataClick`() {
        viewModel.handleIntent(SettingsIntent.ExportData)
        verify { dataSettingsManager.onExportDataClick(any()) }
    }

    @Test
    fun `DeleteAccount delegates to dataSettingsManager onDeleteAccount`() {
        viewModel.handleIntent(SettingsIntent.DeleteAccount)
        verify { dataSettingsManager.onDeleteAccount(any(), any()) }
    }

    @Test
    fun `ConfirmDeleteAccount delegates to dataSettingsManager onConfirmDeleteAccount`() {
        viewModel.handleIntent(SettingsIntent.ConfirmDeleteAccount)
        verify { dataSettingsManager.onConfirmDeleteAccount(any()) }
    }

    @Test
    fun `ShowActivityLevelModal delegates to profileSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowActivityLevelModal)
        verify { profileSettingsManager.onActivityLevelClick(any(), any()) }
    }

    @Test
    fun `ShowUnitTypeModal delegates to unitSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowUnitTypeModal)
        verify { unitSettingsManager.onUnitTypeClick(any(), any()) }
    }

    @Test
    fun `ShowNotificationsModal delegates to notificationSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowNotificationsModal)
        verify { notificationSettingsManager.onNotificationsClick(any(), any()) }
    }

    @Test
    fun `ShowWeightlessModal delegates to profileSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowWeightlessModal)
        verify { profileSettingsManager.onShowWeightlessModal(any(), any()) }
    }

    @Test
    fun `goalSettingModal delegates to profileSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.goalSettingModal)
        verify { profileSettingsManager.onGoalSettingClick(any()) }
    }

    @Test
    fun `ShowAppearanceModal delegates to dataSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowAppearanceModal)
        verify { dataSettingsManager.onAppearanceClick(any(), any(), any()) }
    }

    @Test
    fun `ToggleStreak delegates to profileSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ToggleStreak(true))
        verify { profileSettingsManager.onStreakUpdate(any(), any(), isStreakOn = true) }
    }

    @Test
    fun `ShowMacAddressFilterModal delegates to scaleSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.ShowMacAddressFilterModal)
        verify { scaleSettingsManager.onMacAddressFilterClick(any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    @Test
    fun `onAccountSwitchInfoDismiss delegates to profileSettingsManager`() {
        viewModel.onAccountSwitchInfoDismiss()
        verify { profileSettingsManager.dismissAccountSwitchInfoModal() }
    }

    @Test
    fun `getWeightlessDisplayText delegates to profileSettingsManager`() {
        viewModel.getWeightlessDisplayText()
        verify { profileSettingsManager.getWeightlessDisplayText(any()) }
    }

    // -------------------------------------------------------------------------
    // provideInitialState — verified through state defaults
    // -------------------------------------------------------------------------

    @Test
    fun `provideInitialState returns SettingsState with default values`() {
        // provideInitialState is called during construction; verify its output
        val state = viewModel.state.value
        assertThat(state).isNotNull()
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.account).isNull()
    }

    // -------------------------------------------------------------------------
    // currentState and dispatchIntent — exercised via Logout and LogoutAllAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `Logout passes currentState provider to dataSettingsManager`() {
        // Logout delegates with stateProvider = ::currentState
        viewModel.handleIntent(SettingsIntent.Logout)
        verify {
            dataSettingsManager.onLogOutClick(
                scope = any(),
                stateProvider = any(),
                isLogoutAll = false,
            )
        }
    }

    @Test
    fun `LogoutAllAccounts passes currentState provider to dataSettingsManager`() {
        viewModel.handleIntent(SettingsIntent.LogoutAllAccounts)
        verify {
            dataSettingsManager.onLogOutClick(
                scope = any(),
                stateProvider = any(),
                isLogoutAll = true,
            )
        }
    }

    @Test
    fun `handleIntent dispatches intents to reducer correctly`() {
        // dispatchIntent is used as ::dispatchIntent callback for managers
        // Verify that state can be updated through the dispatch mechanism
        viewModel.handleIntent(SettingsIntent.UpdateThemeMode(THEME_DARK))
        assertThat(viewModel.state.value.currentThemeMode).isEqualTo(THEME_DARK)
    }

    @Test
    fun `init delegates to profileSettingsManager showAccountSwitchInfoModal`() {
        verify { profileSettingsManager.showAccountSwitchInfoModal(any()) }
    }
}
