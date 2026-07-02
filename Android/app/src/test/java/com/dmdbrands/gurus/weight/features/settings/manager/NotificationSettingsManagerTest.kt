package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.domain.services.INotificationService
import com.dmdbrands.gurus.weight.features.common.components.RadioGroupModalConfig
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Covers NotificationSettingsManager — the feed-notification listener wiring and
 * the notifications radio dialog flow. Both targets exercise private helpers via
 * their public entry points: initFeedNotificationListener launches three collect
 * blocks, and onNotificationsClick enqueues a radio dialog whose captured
 * onConfirm callback drives onNotificationUpdate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsManagerTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  private val feedService: IFeedService = mockk(relaxed = true)
  private val notificationService: INotificationService = mockk(relaxed = true)
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)

  private val manager = NotificationSettingsManager(
    feedService = feedService,
    notificationService = notificationService,
    dialogQueueService = dialogQueueService,
  )

  private val account = TestFixtures.anAccount()

  private fun feedSetting(showBadge: Boolean) = FeedSetting(
    showPopupMessage = true,
    showNotificationBadge = showBadge,
  )

  /** Opens the notifications dialog and returns its captured onConfirm callback. */
  @Suppress("UNCHECKED_CAST")
  private fun kotlinx.coroutines.test.TestScope.openDialog(
    state: SettingsState,
  ): (String?) -> Unit {
    val dialogSlot = slot<DialogModel>()
    manager.onNotificationsClick(scope = this, stateProvider = { state })
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val custom = dialogSlot.captured as DialogModel.Custom
    return custom.params["onConfirm"] as (String?) -> Unit
  }

  // ---------------------------------------------------------------------------
  // initFeedNotificationListener
  // ---------------------------------------------------------------------------

  @Test
  fun `init listener dispatches unread count and badge indication`() = runTest {
    every { feedService.feedsChanged } returns flowOf(emptyList())
    every { feedService.notificationBadgeUpdated } returns flowOf(true)
    coEvery { feedService.getUnreadFeedCount() } returns 3
    coEvery { feedService.getFeedSettings() } returns feedSetting(showBadge = true)

    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)
    manager.initFeedNotificationListener(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetUnreadFeedCount(3)) }
    verify { dispatch(SettingsIntent.SetShowUnreadFeedIndication(true)) }
  }

  @Test
  fun `init listener hides indication when count is zero`() = runTest {
    every { feedService.feedsChanged } returns flowOf(emptyList())
    every { feedService.notificationBadgeUpdated } returns flowOf(false)
    coEvery { feedService.getUnreadFeedCount() } returns 0
    coEvery { feedService.getFeedSettings() } returns null

    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)
    manager.initFeedNotificationListener(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetUnreadFeedCount(0)) }
    verify { dispatch(SettingsIntent.SetShowUnreadFeedIndication(false)) }
  }

  @Test
  fun `init listener swallows errors from the feed flows`() = runTest {
    every { feedService.feedsChanged } returns flow { throw RuntimeException("feed boom") }
    every { feedService.notificationBadgeUpdated } returns flow { throw RuntimeException("badge boom") }
    coEvery { feedService.getUnreadFeedCount() } throws RuntimeException("count boom")

    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)
    manager.initFeedNotificationListener(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    // All three launched blocks catch their exceptions, so nothing is dispatched.
    verify(exactly = 0) { dispatch(any()) }
  }

  // ---------------------------------------------------------------------------
  // onNotificationsClick / onNotificationUpdate
  // ---------------------------------------------------------------------------

  private fun stateWith(
    entry: Boolean?,
    weight: Boolean?,
  ) = SettingsState(
    account = account.copy(
      shouldSendEntryNotifications = entry,
      shouldSendWeightInEntryNotifications = weight,
    ),
  )

  @Test
  fun `confirming On updates settings and shows success toast`() = runTest {
    coEvery { notificationService.updateNotificationSettings(any()) } returns account

    val onConfirm = openDialog(stateWith(entry = false, weight = false))
    onConfirm("On")
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify {
      notificationService.updateNotificationSettings(
        match { it.shouldSendEntryNotifications && !it.shouldSendWeightInEntryNotifications },
      )
    }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
    verify { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `confirming with weight updates settings with weight flag`() = runTest {
    coEvery { notificationService.updateNotificationSettings(any()) } returns account

    val onConfirm = openDialog(stateWith(entry = false, weight = false))
    onConfirm("w/ weight")
    advanceUntilIdle()

    coVerify {
      notificationService.updateNotificationSettings(
        match { it.shouldSendEntryNotifications && it.shouldSendWeightInEntryNotifications },
      )
    }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  @Test
  fun `confirming Off disables both notification flags`() = runTest {
    coEvery { notificationService.updateNotificationSettings(any()) } returns account

    val onConfirm = openDialog(stateWith(entry = true, weight = true))
    onConfirm("Off")
    advanceUntilIdle()

    coVerify {
      notificationService.updateNotificationSettings(
        match { !it.shouldSendEntryNotifications && !it.shouldSendWeightInEntryNotifications },
      )
    }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  @Test
  fun `null account from update skips toast but still dismisses loader`() = runTest {
    coEvery { notificationService.updateNotificationSettings(any()) } returns null

    val onConfirm = openDialog(stateWith(entry = false, weight = false))
    onConfirm("On")
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast.Simple>()) }
    verify { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `update failure still dismisses loader in finally`() = runTest {
    coEvery { notificationService.updateNotificationSettings(any()) } throws RuntimeException("network")

    val onConfirm = openDialog(stateWith(entry = false, weight = false))
    onConfirm("On")
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast.Simple>()) }
    verify { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `null selection from confirm does not trigger an update`() = runTest {
    val onConfirm = openDialog(stateWith(entry = false, weight = false))
    onConfirm(null)
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { notificationService.updateNotificationSettings(any()) }
  }

  // ---------------------------------------------------------------------------
  // selectedItem computed value on the dialog config
  // ---------------------------------------------------------------------------

  @Suppress("UNCHECKED_CAST")
  private fun kotlinx.coroutines.test.TestScope.capturedSelectedItem(
    state: SettingsState,
  ): String? {
    val dialogSlot = slot<DialogModel>()
    manager.onNotificationsClick(scope = this, stateProvider = { state })
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val config = (dialogSlot.captured as DialogModel.Custom)
      .params["config"] as RadioGroupModalConfig<String>
    return config.selectedItem
  }

  @Test
  fun `selected item is w slash weight when both flags are on`() = runTest {
    assertThat(capturedSelectedItem(stateWith(entry = true, weight = true)))
      .isEqualTo("w/ weight")
  }

  @Test
  fun `selected item is On when only entry flag is on`() = runTest {
    assertThat(capturedSelectedItem(stateWith(entry = true, weight = false)))
      .isEqualTo("On")
  }

  @Test
  fun `selected item is Off when flags are null`() = runTest {
    assertThat(capturedSelectedItem(stateWith(entry = null, weight = null)))
      .isEqualTo("Off")
  }
}
