package com.dmdbrands.gurus.weight.features.settings.viewmodel

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SettingsReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * This serves as the project's primary smoke test verifying the test
 * infrastructure (Truth, JUnit 6) is wired up correctly.
 */
class SettingsReducerTest {

    private lateinit var reducer: SettingsReducer

    private val fakeAccount = Account(
        id = "test-id",
        firstName = "Test",
        lastName = "User",
        dob = "1990-01-01",
        email = "test@test.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 170,
        activityLevel = "moderate",
    )

    @BeforeEach
    fun setUp() {
        reducer = SettingsReducer()
    }

    @Test
    fun `SetError sets errorMessage and clears isLoading`() {
        val state = SettingsState(isLoading = true, errorMessage = null)

        val result = reducer.reduce(state, SettingsIntent.SetError("Network failure"))

        assertThat(result?.errorMessage).isEqualTo("Network failure")
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `ClearError nullifies errorMessage while preserving other fields`() {
        val state = SettingsState(errorMessage = "Some error", isLoading = false)

        val result = reducer.reduce(state, SettingsIntent.ClearError)

        assertThat(result?.errorMessage).isNull()
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `LoadSettings sets isLoading to true`() {
        val state = SettingsState(isLoading = false)

        val result = reducer.reduce(state, SettingsIntent.LoadSettings)

        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `UpdateThemeMode updates currentThemeMode`() {
        val state = SettingsState()

        val result = reducer.reduce(state, SettingsIntent.UpdateThemeMode("Dark"))

        assertThat(result?.currentThemeMode).isEqualTo("Dark")
    }

    @Test
    fun `UpdateSelectedMacAddress updates selectedMacAddress`() {
        val state = SettingsState()

        val result = reducer.reduce(state, SettingsIntent.UpdateSelectedMacAddress("AA:BB:CC:DD:EE:FF"))

        assertThat(result?.selectedMacAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `UpdateTestingFeatures toggles enableTestingFeatures`() {
        val state = SettingsState(enableTestingFeatures = false)

        val result = reducer.reduce(state, SettingsIntent.UpdateTestingFeatures(enabled = true))

        assertThat(result?.enableTestingFeatures).isTrue()
    }

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() {
        val state = SettingsState(unreadFeedCount = 0)

        val result = reducer.reduce(state, SettingsIntent.SetUnreadFeedCount(count = 5))

        assertThat(result?.unreadFeedCount).isEqualTo(5)
    }

    @Test
    fun `SetShowUnreadFeedIndication updates showUnreadFeedIndication`() {
        val state = SettingsState(showUnreadFeedIndication = false)

        val result = reducer.reduce(state, SettingsIntent.SetShowUnreadFeedIndication(show = true))

        assertThat(result?.showUnreadFeedIndication).isTrue()
    }

    @Test
    fun `SetExportEnabled updates isExportEnabled`() {
        val state = SettingsState(isExportEnabled = false)

        val result = reducer.reduce(state, SettingsIntent.SetExportEnabled(enabled = true))

        assertThat(result?.isExportEnabled).isTrue()
    }

    @Test
    fun `SetIsBabyProduct updates isBabyProduct`() {
        val state = SettingsState(isBabyProduct = false)

        val result = reducer.reduce(state, SettingsIntent.SetIsBabyProduct(isBabyProduct = true))

        assertThat(result?.isBabyProduct).isTrue()
    }

    @Test
    fun `SetHasWeightScale updates hasWeightScale`() {
        val state = SettingsState(hasWeightScale = false)

        val result = reducer.reduce(state, SettingsIntent.SetHasWeightScale(hasWeightScale = true))

        assertThat(result?.hasWeightScale).isTrue()
    }
    @Test
    fun `SetBabyWeightUnit updates babyWeightUnit`() {
        val state = SettingsState(babyWeightUnit = WeightUnit.LB_OZ)

        val result = reducer.reduce(state, SettingsIntent.SetBabyWeightUnit(WeightUnit.KG))

        assertThat(result?.babyWeightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `default babyWeightUnit is LB_OZ`() {
        // Canonical baby scale unit. Migrated by [BabyWeightUnitMapper] when the
        // proto field is UNSPECIFIED for legacy accounts.
        assertThat(SettingsState().babyWeightUnit).isEqualTo(WeightUnit.LB_OZ)
    }
    
    @Test
    fun `SetHasBabyScaleDevice updates hasBabyScaleDevice`() {
        val state = SettingsState(hasBabyScaleDevice = false)

        val result = reducer.reduce(state, SettingsIntent.SetHasBabyScaleDevice(hasBabyScaleDevice = true))

        assertThat(result?.hasBabyScaleDevice).isTrue()
    }

    @Test
    fun `navigation-only intents return null — state is unchanged by reducer`() {
        val state = SettingsState()

        // Navigation/side-effect intents must not alter state in the reducer
        assertThat(reducer.reduce(state, SettingsIntent.Logout)).isNull()
      assertThat(reducer.reduce(state, SettingsIntent.OpenMyDevices)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.SwitchAccount)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.OpenPrivacyPolicy)).isNull()
    }

    @Test
    fun `default SettingsState has expected initial values`() {
        val state = SettingsState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.account).isNull()
        assertThat(state.hasMultipleAccounts).isFalse()
        assertThat(state.currentThemeMode).isEqualTo("System Settings")
        assertThat(state.selectedMacAddress).isEqualTo("All")
        assertThat(state.enableTestingFeatures).isFalse()
        assertThat(state.unreadFeedCount).isEqualTo(0)
        assertThat(state.showUnreadFeedIndication).isFalse()
        assertThat(state.isExportEnabled).isFalse()
        assertThat(state.isBabyProduct).isFalse()
        assertThat(state.hasWeightScale).isFalse()
        assertThat(state.hasBabyScaleDevice).isFalse()
    }

    @Test
    fun `currentNotificationStatus is Off when account is null`() {
        val state = SettingsState(account = null)

        assertThat(state.currentNotificationStatus).isEqualTo("Off")
    }

    @Test
    fun `UpdateAccount sets account and hasMultipleAccounts`() {
        val state = SettingsState()

        val result = reducer.reduce(state, SettingsIntent.UpdateAccount(fakeAccount, hasMultipleAccounts = true))

        assertThat(result?.account).isEqualTo(fakeAccount)
        assertThat(result?.hasMultipleAccounts).isTrue()
    }

    @Test
    fun `UpdateAccount with null account clears account`() {
        val state = SettingsState(account = fakeAccount, hasMultipleAccounts = true)

        val result = reducer.reduce(state, SettingsIntent.UpdateAccount(null, hasMultipleAccounts = false))

        assertThat(result?.account).isNull()
        assertThat(result?.hasMultipleAccounts).isFalse()
    }

    @Test
    fun `currentNotificationStatus is On when shouldSendEntryNotifications is true`() {
        val account = fakeAccount.copy(shouldSendEntryNotifications = true, shouldSendWeightInEntryNotifications = false)
        val state = SettingsState(account = account)

        assertThat(state.currentNotificationStatus).isEqualTo("On")
    }

    @Test
    fun `currentNotificationStatus is On w Weight when both notification flags are true`() {
        val account = fakeAccount.copy(shouldSendEntryNotifications = true, shouldSendWeightInEntryNotifications = true)
        val state = SettingsState(account = account)

        assertThat(state.currentNotificationStatus).isEqualTo("On w/ Weight")
    }

    @Test
    fun `currentNotificationStatus is Off when entry is false but weight is true`() {
        val account = fakeAccount.copy(shouldSendEntryNotifications = false, shouldSendWeightInEntryNotifications = true)
        val state = SettingsState(account = account)

        assertThat(state.currentNotificationStatus).isEqualTo("Off")
    }

    @Test
    fun `currentNotificationStatus is Off when notification flags are null`() {
        val account = fakeAccount.copy(shouldSendEntryNotifications = null, shouldSendWeightInEntryNotifications = null)
        val state = SettingsState(account = account)

        assertThat(state.currentNotificationStatus).isEqualTo("Off")
    }

    @Test
    fun `side-effect and modal intents return null`() {
        val state = SettingsState()

        assertThat(reducer.reduce(state, SettingsIntent.ExportData)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.LogoutAllAccounts)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.DeleteAccount)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.ConfirmDeleteAccount)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.OpenTermsOfService)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.OpenHelp)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.ShowNotificationsModal)).isNull()
    }
}
