package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.proto.GoalAlertProto
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalAlertDataStoreTest {

    private lateinit var fakeDataStore: FakeDataStore<GoalAlertProto>
    private lateinit var goalAlertDataStore: GoalAlertDataStore

    private val testAccountId = "acc-1"
    private val testAccountId2 = "acc-2"

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Exception>()) } returns Unit

        mockkStatic("com.dmdbrands.gurus.weight.data.storage.datastore.GoalAlertDataStoreKt")
        val mockContext = mockk<Context>(relaxed = true)
        fakeDataStore = FakeDataStore(GoalAlertProto.getDefaultInstance())
        every { mockContext.goalAlertDataStore } returns fakeDataStore
        goalAlertDataStore = GoalAlertDataStore(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // hasShownAlert
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownAlert returns false for unknown account`() = runTest {
        assertThat(goalAlertDataStore.hasShownAlert(testAccountId)).isFalse()
    }

    @Test
    fun `hasShownAlert returns true after setting alert shown`() = runTest {
        goalAlertDataStore.setAlertShown(testAccountId, true)
        assertThat(goalAlertDataStore.hasShownAlert(testAccountId)).isTrue()
    }

    @Test
    fun `hasShownAlert returns false after resetting alert`() = runTest {
        goalAlertDataStore.setAlertShown(testAccountId, true)
        goalAlertDataStore.setAlertShown(testAccountId, false)
        assertThat(goalAlertDataStore.hasShownAlert(testAccountId)).isFalse()
    }

    // -------------------------------------------------------------------------
    // setAlertShown
    // -------------------------------------------------------------------------

    @Test
    fun `setAlertShown persists per account`() = runTest {
        goalAlertDataStore.setAlertShown(testAccountId, true)
        goalAlertDataStore.setAlertShown(testAccountId2, false)

        assertThat(goalAlertDataStore.hasShownAlert(testAccountId)).isTrue()
        assertThat(goalAlertDataStore.hasShownAlert(testAccountId2)).isFalse()
    }

    // -------------------------------------------------------------------------
    // getGoalCardValue
    // -------------------------------------------------------------------------

    @Test
    fun `getGoalCardValue returns null when not set`() = runTest {
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isNull()
    }

    @Test
    fun `getGoalCardValue returns true string when set to true`() = runTest {
        goalAlertDataStore.setGoalCardValue(testAccountId, "true")
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isEqualTo("true")
    }

    @Test
    fun `getGoalCardValue returns null when set to non-true value`() = runTest {
        goalAlertDataStore.setGoalCardValue(testAccountId, "false")
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isNull()
    }

    // -------------------------------------------------------------------------
    // setGoalCardValue
    // -------------------------------------------------------------------------

    @Test
    fun `setGoalCardValue persists per account`() = runTest {
        goalAlertDataStore.setGoalCardValue(testAccountId, "true")
        goalAlertDataStore.setGoalCardValue(testAccountId2, "false")

        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isEqualTo("true")
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId2)).isNull()
    }

    @Test
    fun `setGoalCardValue overwrites previous value`() = runTest {
        goalAlertDataStore.setGoalCardValue(testAccountId, "true")
        goalAlertDataStore.setGoalCardValue(testAccountId, "false")
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isNull()
    }

    // -------------------------------------------------------------------------
    // clearData
    // -------------------------------------------------------------------------

    @Test
    fun `clearData removes all alert and goal card states`() = runTest {
        goalAlertDataStore.setAlertShown(testAccountId, true)
        goalAlertDataStore.setGoalCardValue(testAccountId, "true")

        goalAlertDataStore.clearData()

        assertThat(goalAlertDataStore.hasShownAlert(testAccountId)).isFalse()
        assertThat(goalAlertDataStore.getGoalCardValue(testAccountId)).isNull()
    }

    // -------------------------------------------------------------------------
    // getData
    // -------------------------------------------------------------------------

    @Test
    fun `getData returns default instance initially`() = runTest {
        val data = goalAlertDataStore.getData()
        assertThat(data.accountAlertsMap).isEmpty()
        assertThat(data.accountGoalCardStatusMap).isEmpty()
    }

    // -------------------------------------------------------------------------
    // dataFlow
    // -------------------------------------------------------------------------

    @Test
    fun `dataFlow emits updated proto after setAlertShown`() = runTest {
        goalAlertDataStore.setAlertShown(testAccountId, true)

        goalAlertDataStore.dataFlow.test {
            val item = awaitItem()
            assertThat(item.accountAlertsMap[testAccountId]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dataFlow emits updated proto after setGoalCardValue`() = runTest {
        goalAlertDataStore.setGoalCardValue(testAccountId, "true")

        goalAlertDataStore.dataFlow.test {
            val item = awaitItem()
            assertThat(item.accountGoalCardStatusMap[testAccountId]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
