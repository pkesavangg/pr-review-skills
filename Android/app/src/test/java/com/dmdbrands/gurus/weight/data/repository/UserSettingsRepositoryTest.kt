package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IUserSettingsAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.proto.DefaultGraphSegment
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UserSettingsRepositoryTest {

  private fun makeRepository(userDataStore: UserDataStore): UserSettingsRepository =
    UserSettingsRepository(
      userSettingsAPI = mockk<IUserSettingsAPI>(relaxed = true),
      accountDao = mockk<AccountDao>(relaxed = true),
      userDataStore = userDataStore,
    )

  @Test
  fun `setDefaultGraphSegment throws IllegalStateException when no active account`() {
    // The repo must surface a programmer-visible failure rather than silently no-op'ing
    // when there is no signed-in account — the previous `?: return` swallowed this case
    // and produced a misleading "successfully updated" log line.
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      every { currentAccountIdFlow } returns flowOf(null)
    }

    val repository = makeRepository(userDataStore)

    try {
      runBlocking { repository.setDefaultGraphSegment(GraphSegment.WEEK) }
      fail("Expected IllegalStateException when no active account exists")
    } catch (e: IllegalStateException) {
      assertTrue(
        "Error message should mention the missing active account, got: ${e.message}",
        e.message?.contains("No active account") == true,
      )
    }
  }

  @Test
  fun `setDefaultGraphSegment forwards to UserDataStore with the active account ID`() {
    // Happy path: the repo resolves the active account and persists the proto enum
    // through UserDataStore.setDefaultGraphSegment.
    val activeAccountId = "account-42"
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      every { currentAccountIdFlow } returns flowOf(activeAccountId)
      coEvery { setDefaultGraphSegment(any(), any()) } returns Unit
    }

    val repository = makeRepository(userDataStore)

    runBlocking { repository.setDefaultGraphSegment(GraphSegment.MONTH) }

    coVerify(exactly = 1) {
      userDataStore.setDefaultGraphSegment(
        accountId = activeAccountId,
        segment = DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH,
      )
    }
  }
}
