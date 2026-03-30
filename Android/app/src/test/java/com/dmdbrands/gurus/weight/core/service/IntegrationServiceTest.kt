package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationServiceTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val connectivityObserver: IConnectivityObserver = mockk()
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
  private val accountService: IAccountService = mockk()
  private val integrationRepository: IIntegrationRepository = mockk()
  private val healthConnectRepository: IHealthConnectRepository = mockk()

  private val checkIntegrationsFlow = MutableStateFlow(false)

  private lateinit var service: IntegrationService

  // --- Test Fixtures ---
  private val fakeAccount = Account(
    id = "acc-1",
    firstName = "John",
    lastName = "Doe",
    dob = "1990-01-01",
    email = "john@example.com",
    gender = "male",
    isActiveAccount = true,
    isLoggedIn = true,
    isExpired = false,
    zipcode = "12345",
    weightUnit = WeightUnit.LB,
    height = 1750,
    activityLevel = "normal",
    isFitbitOn = false,
    isFitbitValid = false,
    isMFPOn = false,
    isMFPValid = false,
    isHealthConnectOn = false,
  )

  private val fitbitConnectedAccount = fakeAccount.copy(
    isFitbitOn = true,
    isFitbitValid = true,
  )

  private val mfpConnectedAccount = fakeAccount.copy(
    isMFPOn = true,
    isMFPValid = true,
  )

  private val healthConnectOnAccount = fakeAccount.copy(
    isHealthConnectOn = true,
  )

  private val fitbitInactiveAccount = fakeAccount.copy(
    isFitbitOn = true,
    isFitbitValid = false,
  )

  private val mfpInactiveAccount = fakeAccount.copy(
    isMFPOn = true,
    isMFPValid = false,
  )

  private val bothInactiveAccount = fakeAccount.copy(
    isFitbitOn = true,
    isFitbitValid = false,
    isMFPOn = true,
    isMFPValid = false,
  )

  private val fakeHealthConnectData = mockk<HealthConnectData> {
    every { integrated } returns true
  }

  private val fakeHealthConnectDataNotIntegrated = mockk<HealthConnectData> {
    every { integrated } returns false
  }

  @BeforeEach
  fun setUp() {
    stubNetworkAvailable()
    every { accountService.checkIntegrations } returns checkIntegrationsFlow
    service = createService()
  }

  @AfterEach
  fun tearDown() {
    clearAllMocks()
  }

  private fun createService() = IntegrationService(
    connectivityObserver = connectivityObserver,
    dialogQueueService = dialogQueueService,
    appNavigationService = appNavigationService,
    accountService = accountService,
    integrationRepository = integrationRepository,
    healthConnectRepository = healthConnectRepository,
    appScope = CoroutineScope(mainDispatcherRule.dispatcher),
  )

  // --- Shared Helpers ---
  private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
  private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()

  private fun stubGetCurrentAccount(account: Account? = fakeAccount) {
    coEvery { accountService.getCurrentAccount() } returns account
  }

  private fun stubRemoveIntegration() {
    coEvery { integrationRepository.removeIntegration(any(), any()) } just Runs
  }

  private fun stubRefreshAccount() {
    coEvery { accountService.refreshAccount() } just Runs
  }

  private fun stubUpdateLocalAccount() {
    coEvery { integrationRepository.updateLocalAccount() } just Runs
  }

  // -------------------------------------------------------------------------
  // integrationState
  // -------------------------------------------------------------------------

  @Test
  fun `integrationState emits initial Fitbit integration item`() = runTest {
    service.integrationState.test {
      val item = awaitItem()
      assertThat(item.provider).isEqualTo(IntegrationProvider.Fitbit)
      assertThat(item.name).isEqualTo(IntegrationStrings.FitbitProvider)
      assertThat(item.isConnected).isFalse()
      assertThat(item.isValid).isFalse()
      assertThat(item.iconRes).isEqualTo(AppIcons.Integrations.Fitbit)
      assertThat(item.requiresOAuth).isTrue()
      cancelAndIgnoreRemainingEvents()
    }
  }

  // -------------------------------------------------------------------------
  // getIntegrationsWithStatus
  // -------------------------------------------------------------------------

  @Test
  fun `getIntegrationsWithStatus returns all three integrations when account exists`() = runTest {
    stubGetCurrentAccount(fitbitConnectedAccount.copy(isMFPOn = true, isMFPValid = true))
    coEvery { healthConnectRepository.getAccountByID("acc-1") } returns fakeHealthConnectData

    service.getIntegrationsWithStatus().test {
      val items = awaitItem()
      assertThat(items).hasSize(3)

      // Fitbit
      assertThat(items[0].provider).isEqualTo(IntegrationProvider.Fitbit)
      assertThat(items[0].isConnected).isTrue()
      assertThat(items[0].isValid).isTrue()
      assertThat(items[0].requiresOAuth).isTrue()

      // MFP
      assertThat(items[1].provider).isEqualTo(IntegrationProvider.MyFitnessPal)
      assertThat(items[1].isConnected).isTrue()
      assertThat(items[1].isValid).isTrue()
      assertThat(items[1].requiresOAuth).isTrue()

      // Health Connect
      assertThat(items[2].provider).isEqualTo(IntegrationProvider.HealthConnect)
      assertThat(items[2].isConnected).isTrue()
      assertThat(items[2].isValid).isTrue()
      assertThat(items[2].requiresOAuth).isFalse()
      assertThat(items[2].platformRequirement).isEqualTo("Android 13+")

      awaitComplete()
    }
  }

  @Test
  fun `getIntegrationsWithStatus emits empty list when no current account`() = runTest {
    stubGetCurrentAccount(null)

    service.getIntegrationsWithStatus().test {
      val items = awaitItem()
      assertThat(items).isEmpty()
      awaitComplete()
    }
  }

  @Test
  fun `getIntegrationsWithStatus shows HealthConnect disconnected when not integrated`() = runTest {
    stubGetCurrentAccount(fakeAccount)
    coEvery { healthConnectRepository.getAccountByID("acc-1") } returns fakeHealthConnectDataNotIntegrated

    service.getIntegrationsWithStatus().test {
      val items = awaitItem()
      assertThat(items[2].provider).isEqualTo(IntegrationProvider.HealthConnect)
      assertThat(items[2].isConnected).isFalse()
      awaitComplete()
    }
  }

  @Test
  fun `getIntegrationsWithStatus shows HealthConnect disconnected when data is null`() = runTest {
    stubGetCurrentAccount(fakeAccount)
    coEvery { healthConnectRepository.getAccountByID("acc-1") } returns null

    service.getIntegrationsWithStatus().test {
      val items = awaitItem()
      assertThat(items[2].isConnected).isFalse()
      awaitComplete()
    }
  }

  @Test
  fun `getIntegrationsWithStatus completes without emission on exception`() = runTest {
    coEvery { accountService.getCurrentAccount() } throws RuntimeException("error")

    service.getIntegrationsWithStatus().test {
      awaitComplete()
    }
  }

  // -------------------------------------------------------------------------
  // connectIntegration
  // -------------------------------------------------------------------------

  @Test
  fun `connectIntegration returns OAuth URL for Fitbit`() = runTest {
    val result = service.connectIntegration(IntegrationProvider.Fitbit, "acc-1")

    assertThat(result).isNotNull()
    assertThat(result).contains("fitbit.com/oauth2/authorize")
    assertThat(result).contains("v3-acc-1")
  }

  @Test
  fun `connectIntegration returns OAuth URL for MyFitnessPal`() = runTest {
    val result = service.connectIntegration(IntegrationProvider.MyFitnessPal, "acc-1")

    assertThat(result).isNotNull()
    assertThat(result).contains("myfitnesspal.com")
    assertThat(result).contains("v3-acc-1")
  }

  @Test
  fun `connectIntegration returns null for HealthConnect`() = runTest {
    val result = service.connectIntegration(IntegrationProvider.HealthConnect, "acc-1")

    assertThat(result).isNull()
  }

  // -------------------------------------------------------------------------
  // disconnectIntegration
  // -------------------------------------------------------------------------

  @Test
  fun `disconnectIntegration removes Fitbit and refreshes account`() = runTest {
    stubRemoveIntegration()
    stubRefreshAccount()
    stubUpdateLocalAccount()

    service.disconnectIntegration(IntegrationProvider.Fitbit)

    coVerify { integrationRepository.removeIntegration("fitbit", mapOf("suggestion" to "fitbit")) }
    coVerify { accountService.refreshAccount() }
    coVerify { integrationRepository.updateLocalAccount() }
    verify { dialogQueueService.showLoader(IntegrationStrings.loading) }
    verify { dialogQueueService.dismissLoader() }
    verify {
      dialogQueueService.showToast(
        withArg<Toast> {
          assertThat(it.message).isEqualTo(IntegrationStrings.DisconnectSuccess)
        },
      )
    }
  }

  @Test
  fun `disconnectIntegration removes MFP with correct API string`() = runTest {
    stubRemoveIntegration()
    stubRefreshAccount()
    stubUpdateLocalAccount()

    service.disconnectIntegration(IntegrationProvider.MyFitnessPal)

    coVerify { integrationRepository.removeIntegration("mfp", mapOf("suggestion" to "mfp")) }
  }

  @Test
  fun `disconnectIntegration removes HealthConnect with correct API string`() = runTest {
    stubRemoveIntegration()
    stubRefreshAccount()
    stubUpdateLocalAccount()

    service.disconnectIntegration(IntegrationProvider.HealthConnect)

    coVerify {
      integrationRepository.removeIntegration("healthconnect", mapOf("suggestion" to "healthconnect"))
    }
  }

  @Test
  fun `disconnectIntegration shows network error toast and throws when offline`() = runTest {
    stubNetworkUnavailable()

    assertFailsWith<Exception> { service.disconnectIntegration(IntegrationProvider.Fitbit) }
    verify {
      dialogQueueService.showToast(withArg<Toast> {
        assertThat(it.message).isNotEmpty()
      })
    }
    coVerify(exactly = 0) { integrationRepository.removeIntegration(any(), any()) }
  }

  @Test
  fun `disconnectIntegration dismisses loader and rethrows on API error`() = runTest {
    coEvery { integrationRepository.removeIntegration(any(), any()) } throws RuntimeException("API error")

    assertFailsWith<RuntimeException> { service.disconnectIntegration(IntegrationProvider.Fitbit) }
    verify { dialogQueueService.dismissLoader() }
  }

  // -------------------------------------------------------------------------
  // getIntegrationStatus
  // -------------------------------------------------------------------------

  @Test
  fun `getIntegrationStatus returns Fitbit connected and valid`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitConnectedAccount)

    val status = service.getIntegrationStatus(IntegrationProvider.Fitbit)

    assertThat(status.first).isTrue()
    assertThat(status.second).isTrue()
  }

  @Test
  fun `getIntegrationStatus returns Fitbit disconnected`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fakeAccount)

    val status = service.getIntegrationStatus(IntegrationProvider.Fitbit)

    assertThat(status.first).isFalse()
    assertThat(status.second).isFalse()
  }

  @Test
  fun `getIntegrationStatus returns MFP connected and valid`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(mfpConnectedAccount)

    val status = service.getIntegrationStatus(IntegrationProvider.MyFitnessPal)

    assertThat(status.first).isTrue()
    assertThat(status.second).isTrue()
  }

  @Test
  fun `getIntegrationStatus returns HealthConnect on`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(healthConnectOnAccount)

    val status = service.getIntegrationStatus(IntegrationProvider.HealthConnect)

    assertThat(status.first).isTrue()
    assertThat(status.second).isTrue()
  }

  @Test
  fun `getIntegrationStatus returns HealthConnect off but valid`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fakeAccount) // isHealthConnectOn = false

    val status = service.getIntegrationStatus(IntegrationProvider.HealthConnect)

    assertThat(status.first).isFalse()
    assertThat(status.second).isTrue()
  }

  @Test
  fun `getIntegrationStatus returns false pair when no account`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(null)

    val status = service.getIntegrationStatus(IntegrationProvider.Fitbit)

    assertThat(status.first).isFalse()
    assertThat(status.second).isFalse()
  }

  @Test
  fun `getIntegrationStatus returns false pair on exception`() = runTest {
    coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("error")

    val status = service.getIntegrationStatus(IntegrationProvider.Fitbit)

    assertThat(status.first).isFalse()
    assertThat(status.second).isFalse()
  }

  // -------------------------------------------------------------------------
  // getOAuthUrl
  // -------------------------------------------------------------------------

  @Test
  fun `getOAuthUrl returns Fitbit OAuth URL with account ID`() {
    val url = service.getOAuthUrl(IntegrationProvider.Fitbit, "test-id")

    assertThat(url).isNotNull()
    assertThat(url).contains("fitbit.com/oauth2/authorize")
    assertThat(url).contains("v3-test-id")
  }

  @Test
  fun `getOAuthUrl returns null for non-OAuth provider`() {
    val url = service.getOAuthUrl(IntegrationProvider.HealthConnect, "acc-1")

    assertThat(url).isNull()
  }

  // -------------------------------------------------------------------------
  // checkForInactiveIntegrations
  // -------------------------------------------------------------------------

  @Test
  fun `checkForInactiveIntegrations returns empty when all valid`() = runTest {
    stubGetCurrentAccount(fitbitConnectedAccount)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).isEmpty()
  }

  @Test
  fun `checkForInactiveIntegrations detects inactive Fitbit`() = runTest {
    stubGetCurrentAccount(fitbitInactiveAccount)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).containsExactly(IntegrationProvider.Fitbit)
  }

  @Test
  fun `checkForInactiveIntegrations detects inactive MFP`() = runTest {
    stubGetCurrentAccount(mfpInactiveAccount)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).containsExactly(IntegrationProvider.MyFitnessPal)
  }

  @Test
  fun `checkForInactiveIntegrations detects both inactive`() = runTest {
    stubGetCurrentAccount(bothInactiveAccount)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).containsExactly(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal)
  }

  @Test
  fun `checkForInactiveIntegrations returns empty when integrations off`() = runTest {
    stubGetCurrentAccount(fakeAccount)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).isEmpty()
  }

  @Test
  fun `checkForInactiveIntegrations returns empty when no account`() = runTest {
    stubGetCurrentAccount(null)

    val result = service.checkForInactiveIntegrations()

    assertThat(result).isEmpty()
  }

  @Test
  fun `checkForInactiveIntegrations returns empty on exception`() = runTest {
    coEvery { accountService.getCurrentAccount() } throws RuntimeException("error")

    val result = service.checkForInactiveIntegrations()

    assertThat(result).isEmpty()
  }

  // -------------------------------------------------------------------------
  // getInvalidIntegrationNames
  // -------------------------------------------------------------------------

  @Test
  fun `getInvalidIntegrationNames returns empty string for empty list`() {
    val result = service.getInvalidIntegrationNames(emptyList())

    assertThat(result).isEmpty()
  }

  @Test
  fun `getInvalidIntegrationNames returns single provider name`() {
    val result = service.getInvalidIntegrationNames(listOf(IntegrationProvider.Fitbit))

    assertThat(result).isEqualTo("Fitbit")
  }

  @Test
  fun `getInvalidIntegrationNames returns two names joined with and`() {
    val result = service.getInvalidIntegrationNames(
      listOf(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal),
    )

    assertThat(result).isEqualTo("Fitbit and My Fitness Pal")
  }

  @Test
  fun `getInvalidIntegrationNames returns three names with comma and and`() {
    val result = service.getInvalidIntegrationNames(
      listOf(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal, IntegrationProvider.HealthConnect),
    )

    assertThat(result).isEqualTo("Fitbit, My Fitness Pal and Health Connect")
  }

  // -------------------------------------------------------------------------
  // init block — checkIntegrations flow
  // -------------------------------------------------------------------------

  @Test
  fun `init does not check integrations when checkIntegrations emits false`() = runTest {
    advanceUntilIdle()

    coVerify(exactly = 0) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `init checks integrations when checkIntegrations emits true and no inactive found`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitConnectedAccount)

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    coVerify { integrationRepository.updateLocalAccount() }
    coVerify { accountService.getCurrentAccount() }
    verify(exactly = 0) { dialogQueueService.enqueue(any()) }
  }

  @Test
  fun `init shows reintegrate alert when inactive integrations found`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitInactiveAccount)

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    verify {
      dialogQueueService.enqueue(withArg<DialogModel.Confirm> { dialog ->
        assertThat(dialog.title).isEqualTo(IntegrationStrings.reintegrateAlertTitle)
        assertThat(dialog.confirmText).isEqualTo(IntegrationStrings.removeIntegration("Fitbit"))
        assertThat(dialog.cancelText).isEqualTo(IntegrationStrings.openIntegrations)
      })
    }
  }

  @Test
  fun `init shows reintegrate alert with plural text for multiple inactive providers`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(bothInactiveAccount)

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    verify {
      dialogQueueService.enqueue(withArg<DialogModel.Confirm> { dialog ->
        assertThat(dialog.confirmText).isEqualTo(IntegrationStrings.removeAllIntegrations)
        assertThat(dialog.message).contains(IntegrationStrings.pluralityThese)
        assertThat(dialog.message).contains("Fitbit and My Fitness Pal")
      })
    }
  }

  @Test
  fun `init handles exception in checkIntegrations processing`() = runTest {
    coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("error")

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    // Should not crash — exception is caught
    verify(exactly = 0) { dialogQueueService.enqueue(any()) }
  }

  // -------------------------------------------------------------------------
  // showReintegrateAlert — dialog callbacks
  // -------------------------------------------------------------------------

  @Test
  fun `showReintegrateAlert onConfirm removes inactive integrations and refreshes`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitInactiveAccount)
    stubRemoveIntegration()
    stubRefreshAccount()

    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify { integrationRepository.removeIntegration("fitbit", mapOf("suggestion" to "fitbit")) }
    coVerify { accountService.refreshAccount() }
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `showReintegrateAlert onConfirm removes all inactive providers`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(bothInactiveAccount)
    stubRemoveIntegration()
    stubRefreshAccount()

    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify { integrationRepository.removeIntegration("fitbit", mapOf("suggestion" to "fitbit")) }
    coVerify { integrationRepository.removeIntegration("mfp", mapOf("suggestion" to "mfp")) }
    coVerify { accountService.refreshAccount() }
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `showReintegrateAlert onConfirm continues removing others when one fails`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(bothInactiveAccount)
    coEvery { integrationRepository.removeIntegration("fitbit", any()) } throws RuntimeException("fail")
    coEvery { integrationRepository.removeIntegration("mfp", any()) } just Runs
    stubRefreshAccount()

    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify { integrationRepository.removeIntegration("fitbit", any()) }
    coVerify { integrationRepository.removeIntegration("mfp", mapOf("suggestion" to "mfp")) }
    coVerify { accountService.refreshAccount() }
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `showReintegrateAlert onConfirm dismisses dialog even when refreshAccount fails`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitInactiveAccount)
    stubRemoveIntegration()
    coEvery { accountService.refreshAccount() } throws RuntimeException("fail")

    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `showReintegrateAlert onCancel navigates to integration list`() = runTest {
    stubUpdateLocalAccount()
    stubGetCurrentAccount(fitbitInactiveAccount)

    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

    checkIntegrationsFlow.value = true
    advanceUntilIdle()

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onCancel?.invoke()
    advanceUntilIdle()

    coVerify { appNavigationService.navigateTo(AppRoute.Integration.IntegrationList) }
    verify { dialogQueueService.dismissCurrent() }
  }
}
