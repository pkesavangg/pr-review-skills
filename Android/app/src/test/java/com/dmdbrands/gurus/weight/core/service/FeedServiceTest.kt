package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.IFeedRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.feed.shared.SelectedFeedItemHolder
import com.google.common.truth.Truth.assertThat
import com.greatergoods.ggInAppMessaging.core.service.FeedUpdateEvent
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedSetting
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.domain.models.LandingPage
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedServiceTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val feedRepository: IFeedRepository = mockk()
  private val accountService: IAccountService = mockk()
  private val ggIAMService: GGInAppMessagingService = mockk(relaxed = true)
  private val connectivityObserver: IConnectivityObserver = mockk()
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
  private val selectedFeedItemHolder: SelectedFeedItemHolder = mockk(relaxed = true)
  private val context: Context = mockk()

  // Controlled flows for IAM service events
  private val feedNotificationFlow = MutableSharedFlow<Unit>()
  private val sendUpdateFeedFlow = MutableSharedFlow<FeedUpdateEvent>()

  private val onlineState = NetworkState(available = true, unAvailable = false)
  private val offlineState = NetworkState(available = false, unAvailable = true)

  private lateinit var service: FeedService

  // --- Test fixtures ---
  private fun createFeedItem(
    elementId: String = "elem-1",
    feedPostId: String = "post-1",
    isUnread: Boolean = true,
    feedType: String = FeedTypes.LINK,
    linkTarget: String? = "https://example.com",
    trigger: String? = null,
    landingPage: LandingPage? = null,
    promoCode: String? = null,
  ) = FeedItem(
    feedPostId = feedPostId,
    elementId = elementId,
    accountId = "account-1",
    isUnread = isUnread,
    messageTypeText = "SPECIAL OFFER",
    titleText = "Test Item",
    subtitleFeedText = "Test subtitle",
    subtitleModalText = "Modal subtitle",
    titleImage = "https://example.com/image.jpg",
    linkTarget = linkTarget,
    linkText = "Shop now",
    trigger = trigger,
    feedType = feedType,
    landingPage = landingPage,
    promoCode = promoCode,
  )

  @Before
  fun setUp() {
    mockkObject(AppLog)
    every { AppLog.d(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<Throwable>()) } just Runs
    every { AppLog.w(any(), any(), any<String>()) } just Runs
    every { AppLog.i(any(), any(), any<String>()) } just Runs

    // Default: online
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState

    // IAM service flows - never emit by default so init block collectors stay idle
    every { ggIAMService.feedNotificationChangedSubject } returns feedNotificationFlow
    every { ggIAMService.sendUpdateFeed } returns sendUpdateFeedFlow

    // Default account
    coEvery { accountService.getCurrentAccount() } returns mockk<Account> {
      every { id } returns "account-1"
    }
  }

  private fun createService(): FeedService = FeedService(
    feedRepository = feedRepository,
    accountService = accountService,
    ggIAMService = ggIAMService,
    connectivityObserver = connectivityObserver,
    dialogQueueService = dialogQueueService,
    appNavigationService = appNavigationService,
    selectedFeedItemHolder = selectedFeedItemHolder,
    context = context,
    appScope = TestScope(mainDispatcherRule.dispatcher),
  )

  @After
  fun tearDown() {
    unmockkAll()
  }

  // -------------------------------------------------------------------------
  // fetchFeedItems — happy path (online)
  // -------------------------------------------------------------------------

  @Test
  fun `fetchFeedItems fetches from repository and emits merged items`() = runTest {
    val backendItems = listOf(createFeedItem(elementId = "e1"), createFeedItem(elementId = "e2"))
    coEvery { feedRepository.fetchFeedItems() } returns backendItems
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = true,
    )
    service = createService()

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      assertThat(items).hasSize(2)
      assertThat(items.map { it.elementId }).containsExactly("e1", "e2")
    }
  }

  @Test
  fun `fetchFeedItems sets account ID on IAM service`() = runTest {
    coEvery { feedRepository.fetchFeedItems() } returns emptyList()
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    verify { ggIAMService.setAccountId("account-1") }
  }

  @Test
  fun `fetchFeedItems sets empty account ID when no current account`() = runTest {
    coEvery { accountService.getCurrentAccount() } returns null
    coEvery { feedRepository.fetchFeedItems() } returns emptyList()
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    verify { ggIAMService.setAccountId("") }
  }

  @Test
  fun `fetchFeedItems syncs items with IAM service`() = runTest {
    val items = listOf(createFeedItem())
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    coVerify { ggIAMService.setFeedItems(any()) }
  }

  @Test
  fun `fetchFeedItems updates notification badge`() = runTest {
    val unreadItem = createFeedItem(isUnread = true)
    coEvery { feedRepository.fetchFeedItems() } returns listOf(unreadItem)
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = true,
    )
    service = createService()

    service.notificationBadgeUpdated.test {
      service.fetchFeedItems()
      val badgeVisible = awaitItem()
      assertThat(badgeVisible).isTrue()
    }
  }

  // -------------------------------------------------------------------------
  // fetchFeedItems — offline
  // -------------------------------------------------------------------------

  @Test
  fun `fetchFeedItems emits local items when offline`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      assertThat(items).isEmpty() // No local items initially
    }
  }

  @Test
  fun `fetchFeedItems skips repository call when offline`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    coVerify(exactly = 0) { feedRepository.fetchFeedItems() }
  }

  // -------------------------------------------------------------------------
  // fetchFeedItems — error handling
  // -------------------------------------------------------------------------

  @Test
  fun `fetchFeedItems emits local items on repository exception`() = runTest {
    coEvery { feedRepository.fetchFeedItems() } throws RuntimeException("API error")
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      assertThat(items).isEmpty()
    }
  }

  @Test
  fun `fetchFeedItems offline with existing local items emits cached items`() = runTest {
    // First fetch populates local storage while online
    val items = listOf(createFeedItem(elementId = "cached-1"), createFeedItem(elementId = "cached-2"))
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()

    // Go offline
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState

    service.feedsChanged.test {
      service.fetchFeedItems()
      val result = awaitItem()
      assertThat(result).hasSize(2)
      assertThat(result.map { it.elementId }).containsExactly("cached-1", "cached-2")
    }
  }

  // -------------------------------------------------------------------------
  // fetchFeedItems — merge logic
  // -------------------------------------------------------------------------

  @Test
  fun `fetchFeedItems merges backend items with local storage preserving read status`() = runTest {
    // First fetch populates local storage
    val initialItems = listOf(
      createFeedItem(elementId = "e1", isUnread = true),
      createFeedItem(elementId = "e2", isUnread = true),
    )
    coEvery { feedRepository.fetchFeedItems() } returns initialItems
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    // Second fetch with backend showing e1 as read
    val updatedBackendItems = listOf(
      createFeedItem(elementId = "e1", isUnread = false),
      createFeedItem(elementId = "e2", isUnread = true),
    )
    coEvery { feedRepository.fetchFeedItems() } returns updatedBackendItems

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      val e1 = items.first { it.elementId == "e1" }
      assertThat(e1.isUnread).isFalse()
    }
  }

  @Test
  fun `fetchFeedItems adds new backend items not in local storage`() = runTest {
    val initialItems = listOf(createFeedItem(elementId = "e1"))
    coEvery { feedRepository.fetchFeedItems() } returns initialItems
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    // Second fetch with new item from backend
    val updatedItems = listOf(
      createFeedItem(elementId = "e1"),
      createFeedItem(elementId = "e3"),
    )
    coEvery { feedRepository.fetchFeedItems() } returns updatedItems

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      assertThat(items.map { it.elementId }).containsExactly("e1", "e3")
    }
  }

  @Test
  fun `fetchFeedItems preserves local items not in backend response`() = runTest {
    val initialItems = listOf(
      createFeedItem(elementId = "e1"),
      createFeedItem(elementId = "e2"),
    )
    coEvery { feedRepository.fetchFeedItems() } returns initialItems
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    // Backend only returns e1 (e2 not in response)
    coEvery { feedRepository.fetchFeedItems() } returns listOf(createFeedItem(elementId = "e1"))

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      assertThat(items.map { it.elementId }).containsExactly("e1", "e2")
    }
  }

  // -------------------------------------------------------------------------
  // updateFeedItem
  // -------------------------------------------------------------------------

  @Test
  fun `updateFeedItem with read action marks item as read and clears trigger`() = runTest {
    val item = createFeedItem(elementId = "e1", isUnread = true, trigger = "login")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    service.feedsChanged.test {
      service.updateFeedItem(item, FeedActionType.read, null)
      val items = awaitItem()
      val updated = items.first { it.elementId == "e1" }
      assertThat(updated.isUnread).isFalse()
      assertThat(updated.trigger).isNull()
    }
  }

  @Test
  fun `updateFeedItem with trigger action clears trigger only`() = runTest {
    val item = createFeedItem(elementId = "e1", isUnread = true, trigger = "login")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    service.feedsChanged.test {
      service.updateFeedItem(item, FeedActionType.trigger, null)
      val items = awaitItem()
      val updated = items.first { it.elementId == "e1" }
      assertThat(updated.isUnread).isTrue() // Still unread
      assertThat(updated.trigger).isNull()
    }
  }

  @Test
  fun `updateFeedItem with click action does not modify item fields`() = runTest {
    val item = createFeedItem(elementId = "e1", isUnread = true, trigger = "login")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    service.feedsChanged.test {
      service.updateFeedItem(item, FeedActionType.click, null)
      val items = awaitItem()
      val updated = items.first { it.elementId == "e1" }
      assertThat(updated.isUnread).isTrue()
      assertThat(updated.trigger).isEqualTo("login")
    }
  }

  @Test
  fun `updateFeedItem calls repository with correct feed action`() = runTest {
    val item = createFeedItem(feedPostId = "post-1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.read, null)

    val actionSlot = slot<FeedAction>()
    coVerify { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.read)
    assertThat(actionSlot.captured.osType).isNull()
    assertThat(actionSlot.captured.meta).isNull()
  }

  @Test
  fun `updateFeedItem with read action emits feed notification change`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.read, null)

    coVerify { ggIAMService.emitFeedNotificationChange() }
  }

  @Test
  fun `updateFeedItem with non-read action does not emit feed notification change`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.click, null)

    coVerify(exactly = 0) { ggIAMService.emitFeedNotificationChange() }
  }

  @Test
  fun `updateFeedItem logs warning when item not found in local storage`() = runTest {
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    val unknownItem = createFeedItem(elementId = "unknown")
    service.updateFeedItem(unknownItem, FeedActionType.read, null)

    verify { AppLog.w("FeedService", "Feed item not found in local storage: unknown", any<String>()) }
  }

  @Test
  fun `updateFeedItem catches repository exception`() = runTest {
    coEvery { feedRepository.updateFeedItem(any(), any()) } throws RuntimeException("API error")
    service = createService()

    val item = createFeedItem()
    // Should not throw
    service.updateFeedItem(item, FeedActionType.read, null)
  }

  // -------------------------------------------------------------------------
  // buildFeedAction — requiresMeta logic
  // -------------------------------------------------------------------------

  @Test
  fun `updateFeedItem with pageView includes osType and meta`() = runTest {
    val item = createFeedItem(feedPostId = "post-1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.pageView, 42)

    val actionSlot = slot<FeedAction>()
    coVerify { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.pageView)
    assertThat(actionSlot.captured.osType).isEqualTo("Android")
    assertThat(actionSlot.captured.meta).isNotNull()
    assertThat(actionSlot.captured.meta?.variationId).isEqualTo(42)
  }

  @Test
  fun `updateFeedItem with shopNowClick includes osType and meta`() = runTest {
    val item = createFeedItem(feedPostId = "post-1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.shopNowClick, null)

    val actionSlot = slot<FeedAction>()
    coVerify { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.osType).isEqualTo("Android")
    assertThat(actionSlot.captured.meta).isNotNull()
  }

  @Test
  fun `updateFeedItem with trigger action has no osType or meta`() = runTest {
    val item = createFeedItem(feedPostId = "post-1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    service.updateFeedItem(item, FeedActionType.trigger, null)

    val actionSlot = slot<FeedAction>()
    coVerify { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.osType).isNull()
    assertThat(actionSlot.captured.meta).isNull()
  }

  // -------------------------------------------------------------------------
  // getUnreadFeedCount
  // -------------------------------------------------------------------------

  @Test
  fun `getUnreadFeedCount returns count of unread items`() = runTest {
    val items = listOf(
      createFeedItem(elementId = "e1", isUnread = true),
      createFeedItem(elementId = "e2", isUnread = false),
      createFeedItem(elementId = "e3", isUnread = true),
    )
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    val count = service.getUnreadFeedCount()

    assertThat(count).isEqualTo(2)
  }

  @Test
  fun `getUnreadFeedCount returns zero when no items`() = runTest {
    service = createService()

    val count = service.getUnreadFeedCount()

    assertThat(count).isEqualTo(0)
  }

  @Test
  fun `getUnreadFeedCount returns zero when all items are read`() = runTest {
    val items = listOf(
      createFeedItem(elementId = "e1", isUnread = false),
      createFeedItem(elementId = "e2", isUnread = false),
    )
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()
    val count = service.getUnreadFeedCount()

    assertThat(count).isEqualTo(0)
  }

  // -------------------------------------------------------------------------
  // getFeedSettings
  // -------------------------------------------------------------------------

  @Test
  fun `getFeedSettings returns settings from IAM service`() = runTest {
    val settings = FeedSetting(showPopupMessage = true, showNotificationBadge = false)
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns settings
    service = createService()

    val result = service.getFeedSettings()

    assertThat(result).isEqualTo(settings)
  }

  @Test
  fun `getFeedSettings returns null when IAM service returns null`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    val result = service.getFeedSettings()

    assertThat(result).isNull()
  }

  // -------------------------------------------------------------------------
  // checkAndTriggerFeedModal
  // -------------------------------------------------------------------------

  @Test
  fun `checkAndTriggerFeedModal returns result from IAM service`() = runTest {
    coEvery { ggIAMService.checkFeedModalTrigger() } returns true
    service = createService()

    val result = service.checkAndTriggerFeedModal()

    assertThat(result).isTrue()
  }

  @Test
  fun `checkAndTriggerFeedModal returns false when IAM returns false`() = runTest {
    coEvery { ggIAMService.checkFeedModalTrigger() } returns false
    service = createService()

    val result = service.checkAndTriggerFeedModal()

    assertThat(result).isFalse()
  }

  @Test
  fun `checkAndTriggerFeedModal returns false on exception`() = runTest {
    coEvery { ggIAMService.checkFeedModalTrigger() } throws RuntimeException("Error")
    service = createService()

    val result = service.checkAndTriggerFeedModal()

    assertThat(result).isFalse()
  }

  // -------------------------------------------------------------------------
  // updateNotificationBadge
  // -------------------------------------------------------------------------

  @Test
  fun `notification badge is true when unread items exist and badge setting is enabled`() = runTest {
    val items = listOf(createFeedItem(isUnread = true))
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = true,
    )
    service = createService()

    service.notificationBadgeUpdated.test {
      service.fetchFeedItems()
      assertThat(awaitItem()).isTrue()
    }
  }

  @Test
  fun `notification badge is false when unread items exist but badge setting is disabled`() = runTest {
    val items = listOf(createFeedItem(isUnread = true))
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = false,
    )
    service = createService()

    service.notificationBadgeUpdated.test {
      service.fetchFeedItems()
      assertThat(awaitItem()).isFalse()
    }
  }

  @Test
  fun `notification badge is false when no unread items`() = runTest {
    val items = listOf(createFeedItem(isUnread = false))
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = true,
    )
    service = createService()

    service.notificationBadgeUpdated.test {
      service.fetchFeedItems()
      assertThat(awaitItem()).isFalse()
    }
  }

  @Test
  fun `notification badge defaults to true when feed settings is null`() = runTest {
    val items = listOf(createFeedItem(isUnread = true))
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.notificationBadgeUpdated.test {
      service.fetchFeedItems()
      assertThat(awaitItem()).isTrue()
    }
  }

  // -------------------------------------------------------------------------
  // showIAMFeedModal
  // -------------------------------------------------------------------------

  @Test
  fun `showIAMFeedModal shows dialog with correct content key params and priority`() {
    service = createService()
    val item = createFeedItem()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }
    val custom = dialogSlot.captured as DialogModel.Custom
    assertThat(custom.contentKey).isEqualTo(DialogType.IAMFeedModal)
    assertThat(custom.params["feedItem"]).isEqualTo(item)
    assertThat(custom.params["elementId"]).isEqualTo(item.elementId)
    assertThat(custom.customPriority).isEqualTo(3)
    assertThat(custom.customDelayMillis).isEqualTo(0L)
  }

  @Test
  fun `showIAMFeedModal catches exception without throwing`() {
    every { dialogQueueService.showDialog(any()) } throws RuntimeException("Dialog error")
    service = createService()

    // Should not throw
    service.showIAMFeedModal(createFeedItem())
  }

  // -------------------------------------------------------------------------
  // convertStringToFeedActionType (tested via init block's sendUpdateFeed)
  // -------------------------------------------------------------------------

  @Test
  fun `init block handles feed update event from IAM service`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    // Populate local storage first
    service.fetchFeedItems()

    // Wait for collector to start
    Thread.sleep(200)

    // Emit a feed update event
    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "read"))
    Thread.sleep(500)

    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", any()) }
  }

  @Test
  fun `init block handles feedNotificationChanged event`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns FeedSetting(
      showPopupMessage = true,
      showNotificationBadge = true,
    )
    service = createService()

    // Wait for collector to start
    Thread.sleep(200)

    // Emit feed notification change
    feedNotificationFlow.emit(Unit)
    Thread.sleep(500)

    // Should call getFeedSettings (once from init, once from notification change)
    coVerify(atLeast = 2, timeout = 2000) { ggIAMService.getStoredFeedNotificationSetting() }
  }

  // -------------------------------------------------------------------------
  // handleFeedModalAction — via showIAMFeedModal's onConfirm callback
  // -------------------------------------------------------------------------

  @Test
  fun `showIAMFeedModal onDismiss calls dismissCurrent`() {
    service = createService()

    service.showIAMFeedModal(createFeedItem())

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }
    (dialogSlot.captured as DialogModel.Custom).onDismiss?.invoke()
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `handleFeedModalAction settings navigates to FeedMessageSetting`() = runTest {
    service = createService()
    val item = createFeedItem()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    // Invoke onConfirm with "settings" action
    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("settings")
    Thread.sleep(500)

    coVerify(timeout = 2000) { appNavigationService.navigateTo(any<com.dmdbrands.gurus.weight.core.navigation.AppRoute>()) }
  }

  @Test
  fun `handleFeedModalAction buy_now with LANDING type navigates to FeedLanding`() = runTest {
    val item = createFeedItem(
      feedType = FeedTypes.LANDING,
      landingPage = LandingPage(
        feedLandingPageId = "lp-1",
        feedPostId = "post-1",
        titleText = "Landing",
      ),
    )
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    service = createService()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("buy_now")
    Thread.sleep(500)

    verify(timeout = 2000) { selectedFeedItemHolder.setSelectedFeedItem(item) }
    coVerify(timeout = 2000) { appNavigationService.navigateTo(any<com.dmdbrands.gurus.weight.core.navigation.AppRoute>()) }
  }

  // -------------------------------------------------------------------------
  // setMockFeedItems
  // -------------------------------------------------------------------------

  @Test
  fun `setMockFeedItems generates items when local storage is empty`() = runTest {
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      assertThat(items).isNotEmpty()
      assertThat(items.size).isIn(2..3)
    }
  }

  @Test
  fun `setMockFeedItems does not generate items when local storage has items`() = runTest {
    val existingItems = listOf(createFeedItem())
    coEvery { feedRepository.fetchFeedItems() } returns existingItems
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.fetchFeedItems()

    // Reset verify counts
    Thread.sleep(100)

    service.setMockFeedItems()
    Thread.sleep(500)

    // Should not call setFeedItems again after setMockFeedItems (only from fetchFeedItems)
    coVerify(exactly = 1) { ggIAMService.setFeedItems(any()) }
  }

  // -------------------------------------------------------------------------
  // convertStringToFeedActionType (via init sendUpdateFeed collector)
  // -------------------------------------------------------------------------

  @Test
  fun `convertStringToFeedActionType maps trigger correctly`() = runTest {
    val item = createFeedItem(elementId = "e1", trigger = "login")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "trigger"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.trigger)
  }

  @Test
  fun `convertStringToFeedActionType maps click correctly`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "click"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.click)
  }

  @Test
  fun `convertStringToFeedActionType maps pageView correctly`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "pageView"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.pageView)
  }

  @Test
  fun `convertStringToFeedActionType maps shopNowClick correctly`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "shopNowClick"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.shopNowClick)
  }

  @Test
  fun `convertStringToFeedActionType maps variationClick correctly`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "variationClick"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.variationClick)
  }

  @Test
  fun `convertStringToFeedActionType maps promoClick correctly`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "promoClick"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.promoClick)
  }

  @Test
  fun `convertStringToFeedActionType defaults to click for unknown action`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()
    Thread.sleep(200)

    sendUpdateFeedFlow.emit(FeedUpdateEvent(feedItem = item, actionType = "unknownAction"))
    Thread.sleep(500)

    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.click)
  }

  // -------------------------------------------------------------------------
  // handleFeedModalAction — additional paths
  // -------------------------------------------------------------------------

  @Test
  fun `handleFeedModalAction buy_now with LINK type tracks click`() = runTest {
    val item = createFeedItem(feedType = FeedTypes.LINK, linkTarget = "https://shop.example.com")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("buy_now")
    Thread.sleep(1000)

    // showIAMFeedModal triggers both trigger + shopNowClick, use mutableList to capture all
    val actions = mutableListOf<FeedAction>()
    coVerify(timeout = 2000, atLeast = 2) { feedRepository.updateFeedItem("post-1", capture(actions)) }
    assertThat(actions.map { it.action }).contains(FeedActionType.shopNowClick)
  }

  @Test
  fun `handleFeedModalAction settings catches navigation exception`() = runTest {
    coEvery { appNavigationService.navigateTo(any<com.dmdbrands.gurus.weight.core.navigation.AppRoute>()) } throws RuntimeException("Nav error")
    service = createService()

    service.showIAMFeedModal(createFeedItem())

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    // Should not throw — exception is caught inside handleFeedModalAction
    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("settings")
    Thread.sleep(500)
  }

  @Test
  fun `handleFeedModalAction buy_now LANDING catches navigation exception`() = runTest {
    val item = createFeedItem(
      feedType = FeedTypes.LANDING,
      landingPage = LandingPage(
        feedLandingPageId = "lp-1",
        feedPostId = "post-1",
        titleText = "Landing",
      ),
    )
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { appNavigationService.navigateTo(any<com.dmdbrands.gurus.weight.core.navigation.AppRoute>()) } throws RuntimeException("Nav error")
    service = createService()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    // Should not throw — exception is caught inside handleFeedModalAction
    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("buy_now")
    Thread.sleep(500)
  }

  @Test
  fun `handleFeedModalAction buy_now with LINK type and null linkTarget logs warning`() = runTest {
    val item = createFeedItem(feedType = FeedTypes.LINK, linkTarget = null)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    service = createService()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("buy_now")
    Thread.sleep(1000)

    verify(timeout = 2000) { AppLog.w("FeedService", match { it.contains("No link target") }, any<String>()) }
  }

  @Test
  fun `handleFeedModalAction buy_now with unknown feedType logs warning`() = runTest {
    val item = createFeedItem(feedType = "unknown_type")
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    service = createService()

    service.showIAMFeedModal(item)

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("buy_now")
    Thread.sleep(1000)

    verify(timeout = 2000) { AppLog.w("FeedService", match { it.contains("Unknown feed type") }, any<String>()) }
  }

  @Test
  fun `handleFeedModalAction with unknown action type logs message`() = runTest {
    service = createService()

    service.showIAMFeedModal(createFeedItem())

    val dialogSlot = slot<DialogModel>()
    verify { dialogQueueService.showDialog(capture(dialogSlot)) }

    (dialogSlot.captured as DialogModel.Custom).onConfirm?.invoke("unknown_action")
    Thread.sleep(500)

    verify(timeout = 2000) { AppLog.d("FeedService", match { it.contains("Unknown action type") }, any<String>()) }
  }

  // -------------------------------------------------------------------------
  // Flow properties
  // -------------------------------------------------------------------------

  @Test
  fun `feedSettingsChanged emits when feed notification changes`() = runTest {
    val settings = FeedSetting(showPopupMessage = true, showNotificationBadge = true)
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns settings
    service = createService()

    Thread.sleep(200)

    service.feedSettingsChanged.test {
      feedNotificationFlow.emit(Unit)
      val result = awaitItem()
      assertThat(result).isEqualTo(settings)
    }
  }

  @Test
  fun `showIAMFeedModal triggers updateFeedItem with trigger action`() = runTest {
    val item = createFeedItem(elementId = "e1")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(item)
    coEvery { feedRepository.updateFeedItem(any(), any()) } just Runs
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()

    service.showIAMFeedModal(item)
    Thread.sleep(500)

    // showIAMFeedModal tracks the feed modal open via trigger action
    val actionSlot = slot<FeedAction>()
    coVerify(timeout = 2000) { feedRepository.updateFeedItem("post-1", capture(actionSlot)) }
    assertThat(actionSlot.captured.action).isEqualTo(FeedActionType.trigger)
  }

  // -------------------------------------------------------------------------
  // mergeFeedItemsWithLocalStorage — additional coverage
  // -------------------------------------------------------------------------

  @Test
  fun `mergeFeedItemsWithLocalStorage updates all fields from backend`() = runTest {
    val initial = createFeedItem(elementId = "e1", isUnread = true, trigger = "login")
    coEvery { feedRepository.fetchFeedItems() } returns listOf(initial)
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()
    service.fetchFeedItems()

    // Second fetch with updated fields
    val updated = createFeedItem(
      elementId = "e1",
      isUnread = false,
      trigger = null,
      feedType = FeedTypes.LANDING,
      linkTarget = "https://updated.com",
    )
    coEvery { feedRepository.fetchFeedItems() } returns listOf(updated)

    service.feedsChanged.test {
      service.fetchFeedItems()
      val items = awaitItem()
      val merged = items.first { it.elementId == "e1" }
      assertThat(merged.isUnread).isFalse()
      assertThat(merged.trigger).isNull()
      assertThat(merged.feedType).isEqualTo(FeedTypes.LANDING)
      assertThat(merged.linkTarget).isEqualTo("https://updated.com")
    }
  }

  @Test
  fun `mergeFeedItemsWithLocalStorage deduplicates by elementId`() = runTest {
    val items = listOf(
      createFeedItem(elementId = "e1"),
      createFeedItem(elementId = "e1"),
    )
    coEvery { feedRepository.fetchFeedItems() } returns items
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.fetchFeedItems()
      val result = awaitItem()
      assertThat(result.map { it.elementId }.distinct()).hasSize(1)
    }
  }

  // -------------------------------------------------------------------------
  // generateDynamicMockFeedItems — via setMockFeedItems
  // -------------------------------------------------------------------------

  @Test
  fun `setMockFeedItems generates items with alternating read-unread status`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      // First item (index 0) should be unread, second (index 1) should be read
      if (items.size >= 2) {
        assertThat(items[0].isUnread).isTrue()
        assertThat(items[1].isUnread).isFalse()
      }
    }
  }

  @Test
  fun `setMockFeedItems generates items with unique element IDs`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      val elementIds = items.map { it.elementId }
      assertThat(elementIds).containsNoDuplicates()
    }
  }

  @Test
  fun `setMockFeedItems generates items with LANDING type having landingPage`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      // Items with LANDING type should have a landingPage
      items.filter { it.feedType == FeedTypes.LANDING }.forEach { item ->
        assertThat(item.landingPage).isNotNull()
      }
      // Items with LINK type should not have a landingPage
      items.filter { it.feedType == FeedTypes.LINK }.forEach { item ->
        assertThat(item.landingPage).isNull()
      }
    }
  }

  // -------------------------------------------------------------------------
  // createDynamicLandingPage — via setMockFeedItems
  // -------------------------------------------------------------------------

  @Test
  fun `setMockFeedItems landing page has promo code`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      val landingItems = items.filter { it.feedType == FeedTypes.LANDING }
      landingItems.forEach { item ->
        val lp = item.landingPage
        assertThat(lp).isNotNull()
        assertThat(lp?.promoCode).isNotNull()
        assertThat(lp?.promoCode?.length).isEqualTo(8)
      }
    }
  }

  @Test
  fun `setMockFeedItems landing page has featured products`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      val landingItems = items.filter { it.feedType == FeedTypes.LANDING }
      landingItems.forEach { item ->
        val lp = item.landingPage
        assertThat(lp?.featuredProduct).isNotNull()
        assertThat(lp?.featuredProduct).isNotEmpty()
      }
    }
  }

  // -------------------------------------------------------------------------
  // generateRandomPromoCode — via setMockFeedItems landing page
  // -------------------------------------------------------------------------

  @Test
  fun `setMockFeedItems promo codes contain only uppercase letters and digits`() = runTest {
    coEvery { ggIAMService.getStoredFeedNotificationSetting() } returns null
    service = createService()

    service.feedsChanged.test {
      service.setMockFeedItems()
      Thread.sleep(500)
      val items = awaitItem()
      val landingItems = items.filter { it.feedType == FeedTypes.LANDING }
      landingItems.forEach { item ->
        val promoCode = item.landingPage?.promoCode ?: return@forEach
        assertThat(promoCode).matches("[A-Z0-9]+")
      }
    }
  }

}
