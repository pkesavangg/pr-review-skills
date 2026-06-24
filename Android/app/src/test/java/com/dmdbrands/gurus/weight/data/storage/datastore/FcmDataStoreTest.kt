package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
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
class FcmDataStoreTest {

    private lateinit var fakeDataStore: FakeDataStore<FcmToken>
    private lateinit var fcmDataStore: FcmDataStore

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Exception>()) } returns Unit

        mockkStatic("com.dmdbrands.gurus.weight.data.storage.datastore.FcmDataStoreKt")
        val mockContext = mockk<Context>(relaxed = true)
        fakeDataStore = FakeDataStore(FcmToken.getDefaultInstance())
        every { mockContext.fcmTokenStore } returns fakeDataStore
        fcmDataStore = FcmDataStore(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // tokenFlow
    // -------------------------------------------------------------------------

    @Test
    fun `tokenFlow emits empty string initially`() = runTest {
        fcmDataStore.tokenFlow.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tokenFlow emits token after setToken`() = runTest {
        fcmDataStore.setToken("test-fcm-token")

        fcmDataStore.tokenFlow.test {
            assertThat(awaitItem()).isEqualTo("test-fcm-token")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setToken
    // -------------------------------------------------------------------------

    @Test
    fun `setToken stores the token`() = runTest {
        fcmDataStore.setToken("new-token")

        val data = fcmDataStore.getData()
        assertThat(data.token).isEqualTo("new-token")
    }

    @Test
    fun `setToken overwrites previous token`() = runTest {
        fcmDataStore.setToken("first-token")
        fcmDataStore.setToken("second-token")

        val data = fcmDataStore.getData()
        assertThat(data.token).isEqualTo("second-token")
    }

    @Test
    fun `setToken with empty string clears the token`() = runTest {
        fcmDataStore.setToken("some-token")
        fcmDataStore.setToken("")

        val data = fcmDataStore.getData()
        assertThat(data.token).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getData
    // -------------------------------------------------------------------------

    @Test
    fun `getData returns default instance initially`() = runTest {
        val data = fcmDataStore.getData()
        assertThat(data.token).isEmpty()
    }

    // -------------------------------------------------------------------------
    // dataFlow
    // -------------------------------------------------------------------------

    @Test
    fun `dataFlow emits full FcmToken proto`() = runTest {
        fcmDataStore.setToken("my-token")

        fcmDataStore.dataFlow.test {
            val item = awaitItem()
            assertThat(item.token).isEqualTo("my-token")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // clearData
    // -------------------------------------------------------------------------

    @Test
    fun `clearData resets to default instance`() = runTest {
        fcmDataStore.setToken("will-be-cleared")

        fcmDataStore.clearData()

        val data = fcmDataStore.getData()
        assertThat(data.token).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getDefaultInstance
    // -------------------------------------------------------------------------

    @Test
    fun `getDefaultInstance returns FcmToken default`() = runTest {
        fcmDataStore.clearData()

        val data = fcmDataStore.getData()
        assertThat(data).isEqualTo(FcmToken.getDefaultInstance())
    }
}
