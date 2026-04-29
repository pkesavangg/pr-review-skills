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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
  fun `setDefaultGraphSegment propagates IllegalStateException from DataStore when no active account`() {
    // The active-account lookup now happens atomically inside UserDataStore.setDefaultGraphSegment's
    // updateData lambda. The repo just forwards the exception so the ViewModel can show an error toast.
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      coEvery { setDefaultGraphSegment(any()) } throws
        IllegalStateException("No active account when persisting default graph segment")
    }

    val repository = makeRepository(userDataStore)

    try {
      runBlocking { repository.setDefaultGraphSegment(GraphSegment.WEEK) }
      fail("Expected IllegalStateException to propagate from DataStore")
    } catch (e: IllegalStateException) {
      assertTrue(
        "Error message should mention the missing active account, got: ${e.message}",
        e.message?.contains("No active account") == true,
      )
    }
  }

  @Test
  fun `setDefaultGraphSegment forwards mapped proto segment to UserDataStore`() {
    // Happy path: the repo maps GraphSegment → proto enum and delegates to UserDataStore.
    // Account resolution is now atomic inside DataStore — no separate currentAccountIdFlow read.
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      coEvery { setDefaultGraphSegment(any()) } returns Unit
    }

    val repository = makeRepository(userDataStore)

    runBlocking { repository.setDefaultGraphSegment(GraphSegment.MONTH) }

    coVerify(exactly = 1) {
      userDataStore.setDefaultGraphSegment(segment = DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH)
    }
  }

  @Test
  fun `setDefaultGraphSegment forwards correct proto segment for every GraphSegment value`() {
    // Guard against transposition bugs in the toDefaultGraphSegment() mapper — the
    // MONTH-only test misses wrong mappings for the other three branches.
    val cases = mapOf(
      GraphSegment.WEEK to DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK,
      GraphSegment.YEAR to DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR,
      GraphSegment.TOTAL to DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL,
    )
    cases.forEach { (input, expected) ->
      val userDataStore = mockk<UserDataStore>(relaxed = true) {
        coEvery { setDefaultGraphSegment(any()) } returns Unit
      }
      val repository = makeRepository(userDataStore)
      runBlocking { repository.setDefaultGraphSegment(input) }
      coVerify(exactly = 1) { userDataStore.setDefaultGraphSegment(segment = expected) }
    }
  }

  @Test
  fun `defaultGraphSegmentFlow maps every proto enum to the matching GraphSegment`() {
    // Pipeline contract: the repo applies toGraphSegment() to every value the DataStore
    // emits, including the UNSPECIFIED → MONTH fallback for fresh installs.
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flowOf(
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_UNSPECIFIED,
      )
    }

    val repository = makeRepository(userDataStore)

    val collected = runBlocking { repository.defaultGraphSegmentFlow.toList() }

    assertEquals(
      listOf(
        GraphSegment.WEEK,
        GraphSegment.MONTH,
        GraphSegment.YEAR,
        GraphSegment.TOTAL,
        GraphSegment.MONTH,
      ),
      collected,
    )
  }

  @Test
  fun `defaultGraphSegmentFlow drops consecutive duplicates`() {
    // distinctUntilChanged keeps DashboardViewModel from re-rendering on every spurious
    // proto write that didn't actually change the segment value.
    val userDataStore = mockk<UserDataStore>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flowOf(
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK,
        DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH,
      )
    }

    val repository = makeRepository(userDataStore)

    val collected = runBlocking { repository.defaultGraphSegmentFlow.toList() }

    assertEquals(
      listOf(GraphSegment.MONTH, GraphSegment.WEEK, GraphSegment.MONTH),
      collected,
    )
  }
}
