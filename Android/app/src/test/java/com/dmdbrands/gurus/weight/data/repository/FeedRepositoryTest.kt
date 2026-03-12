package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IFeedAPI
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.FeedAction
import com.dmdbrands.gurus.weight.domain.repository.FeedActionMeta
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.google.common.truth.Truth.assertThat
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FeedRepositoryTest {

  @MockK(relaxUnitFun = true)
  lateinit var feedAPI: IFeedAPI

  @MockK(relaxUnitFun = true)
  lateinit var accountService: IAccountService

  private lateinit var repository: FeedRepository

  private val mockAccount = mockk<Account>(relaxed = true)
  private val feedItem1 = mockk<FeedItem>()
  private val feedItem2 = mockk<FeedItem>()

  @Before
  fun setUp() {
    MockKAnnotations.init(this)
    every { mockAccount.id } returns "acc-123"
    repository = FeedRepository(feedAPI, accountService)
  }

  // fetchFeedItems tests

  @Test
  fun `fetchFeedItems returns list from API when account exists`() = runTest {
    val expected = listOf(feedItem1, feedItem2)
    coEvery { accountService.getCurrentAccount() } returns mockAccount
    coEvery { feedAPI.fetchFeedItems("acc-123") } returns expected

    val result = repository.fetchFeedItems()

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `fetchFeedItems passes correct accountId to API`() = runTest {
    coEvery { accountService.getCurrentAccount() } returns mockAccount
    coEvery { feedAPI.fetchFeedItems("acc-123") } returns emptyList()

    repository.fetchFeedItems()

    coVerify { feedAPI.fetchFeedItems("acc-123") }
  }

  @Test
  fun `fetchFeedItems uses empty accountId when account is null`() = runTest {
    val expected = listOf(feedItem1)
    coEvery { accountService.getCurrentAccount() } returns null
    coEvery { feedAPI.fetchFeedItems("") } returns expected

    val result = repository.fetchFeedItems()

    assertThat(result).isEqualTo(expected)
    coVerify { feedAPI.fetchFeedItems("") }
  }

  @Test
  fun `fetchFeedItems returns empty list on IOException`() = runTest {
    coEvery { accountService.getCurrentAccount() } throws IOException("Network error")

    val result = repository.fetchFeedItems()

    assertThat(result).isEmpty()
  }

  @Test
  fun `fetchFeedItems returns empty list when API throws RuntimeException`() = runTest {
    coEvery { accountService.getCurrentAccount() } returns mockAccount
    coEvery { feedAPI.fetchFeedItems(any()) } throws RuntimeException("Server error")

    val result = repository.fetchFeedItems()

    assertThat(result).isEmpty()
  }

  @Test
  fun `fetchFeedItems returns empty list when API throws IllegalStateException`() = runTest {
    coEvery { accountService.getCurrentAccount() } returns mockAccount
    coEvery { feedAPI.fetchFeedItems(any()) } throws IllegalStateException("API error")

    val result = repository.fetchFeedItems()

    assertThat(result).isEmpty()
  }

  // updateFeedItem tests

  @Test
  fun `updateFeedItem calls API with correct parameters`() = runTest {
    val feedAction = FeedAction(action = FeedActionType.click, osType = "android", meta = null)
    coEvery { accountService.getCurrentAccount() } returns mockAccount

    repository.updateFeedItem("post-1", feedAction)

    coVerify { feedAPI.updateFeedItem("post-1", feedAction, "acc-123") }
  }

  @Test
  fun `updateFeedItem uses empty accountId when account is null`() = runTest {
    val feedAction = FeedAction(action = FeedActionType.read, osType = null, meta = null)
    coEvery { accountService.getCurrentAccount() } returns null

    repository.updateFeedItem("post-1", feedAction)

    coVerify { feedAPI.updateFeedItem("post-1", feedAction, "") }
  }

  @Test
  fun `updateFeedItem swallows IOException silently`() = runTest {
    val feedAction = FeedAction(action = FeedActionType.trigger, osType = "android", meta = null)
    coEvery { accountService.getCurrentAccount() } returns mockAccount
    coEvery { feedAPI.updateFeedItem(any(), any(), any()) } throws IOException("Network error")

    repository.updateFeedItem("post-1", feedAction)
  }

  @Test
  fun `updateFeedItem swallows exception from accountService silently`() = runTest {
    val feedAction = FeedAction(action = FeedActionType.pageView, osType = null, meta = null)
    coEvery { accountService.getCurrentAccount() } throws RuntimeException("Service error")

    repository.updateFeedItem("post-1", feedAction)
  }

  @Test
  fun `updateFeedItem passes feedAction with meta to API`() = runTest {
    val meta = FeedActionMeta(variationId = 42)
    val feedAction = FeedAction(action = FeedActionType.variationClick, osType = "android", meta = meta)
    coEvery { accountService.getCurrentAccount() } returns mockAccount

    repository.updateFeedItem("post-2", feedAction)

    coVerify { feedAPI.updateFeedItem("post-2", feedAction, "acc-123") }
  }
}
