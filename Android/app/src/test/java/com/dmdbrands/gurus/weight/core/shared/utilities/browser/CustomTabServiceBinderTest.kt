package com.dmdbrands.gurus.weight.core.shared.utilities.browser

import android.content.ComponentName
import android.content.Context
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class CustomTabServiceBinderTest {

  // --- Mocks ---
  private val context: Context = mockk(relaxed = true)
  private val callback: CustomTabsCallback = mockk(relaxed = true)
  private val mockClient: CustomTabsClient = mockk(relaxed = true)
  private val mockSession: CustomTabsSession = mockk(relaxed = true)
  private val mockComponentName: ComponentName = mockk(relaxed = true)

  private lateinit var binder: CustomTabServiceBinder

  private val connectionSlot = slot<CustomTabsServiceConnection>()

  @Before
  fun setUp() {
    mockkStatic(CustomTabsClient::class)
    every {
      CustomTabsClient.bindCustomTabsService(any(), any(), capture(connectionSlot))
    } returns true

    every { mockClient.warmup(any()) } returns true
    every { mockClient.newSession(any()) } returns mockSession

    binder = CustomTabServiceBinder(context, "com.android.chrome", callback)
  }

  @After
  fun tearDown() {
    unmockkStatic(CustomTabsClient::class)
    clearAllMocks()
  }

  // -------------------------------------------------------------------------
  // session (initial state)
  // -------------------------------------------------------------------------

  @Test
  fun `session is initially null`() {
    assertThat(binder.session).isNull()
  }

  // -------------------------------------------------------------------------
  // bind
  // -------------------------------------------------------------------------

  @Test
  fun `bind calls bindCustomTabsService with correct context and package`() {
    binder.bind()

    verify(exactly = 1) {
      CustomTabsClient.bindCustomTabsService(context, "com.android.chrome", any())
    }
  }

  @Test
  fun `bind does not call bindCustomTabsService when already bound`() {
    binder.bind()
    simulateServiceConnected()

    // Second bind should be a no-op
    binder.bind()

    verify(exactly = 1) {
      CustomTabsClient.bindCustomTabsService(any(), any(), any())
    }
  }

  @Test
  fun `bind creates connection that warms up client on connect`() {
    binder.bind()
    simulateServiceConnected()

    verify(exactly = 1) { mockClient.warmup(0L) }
  }

  @Test
  fun `bind creates connection that creates session with callback on connect`() {
    binder.bind()
    simulateServiceConnected()

    verify(exactly = 1) { mockClient.newSession(callback) }
  }

  @Test
  fun `bind sets session after onCustomTabsServiceConnected`() {
    binder.bind()

    assertThat(binder.session).isNull()

    simulateServiceConnected()

    assertThat(binder.session).isEqualTo(mockSession)
  }

  @Test
  fun `onServiceDisconnected clears session`() {
    binder.bind()
    simulateServiceConnected()
    assertThat(binder.session).isNotNull()

    simulateServiceDisconnected()

    assertThat(binder.session).isNull()
  }

  @Test
  fun `onServiceDisconnected resets isBound allowing rebind`() {
    binder.bind()
    simulateServiceConnected()
    simulateServiceDisconnected()

    // Should be able to bind again after disconnect
    binder.bind()

    verify(exactly = 2) {
      CustomTabsClient.bindCustomTabsService(any(), any(), any())
    }
  }

  @Test
  fun `bind after unbind rebinds successfully`() {
    binder.bind()
    simulateServiceConnected()
    binder.unbind()

    binder.bind()

    verify(exactly = 2) {
      CustomTabsClient.bindCustomTabsService(any(), any(), any())
    }
  }

  // -------------------------------------------------------------------------
  // unbind
  // -------------------------------------------------------------------------

  @Test
  fun `unbind calls context unbindService with the connection`() {
    binder.bind()

    binder.unbind()

    verify(exactly = 1) { context.unbindService(any()) }
  }

  @Test
  fun `unbind does nothing when never bound`() {
    binder.unbind()

    verify(exactly = 0) { context.unbindService(any()) }
  }

  @Test
  fun `unbind resets isBound so bind can be called again`() {
    binder.bind()
    simulateServiceConnected()

    binder.unbind()
    binder.bind()

    verify(exactly = 2) {
      CustomTabsClient.bindCustomTabsService(any(), any(), any())
    }
  }

  @Test
  fun `unbind can be called multiple times without error`() {
    binder.bind()

    binder.unbind()
    binder.unbind()

    verify(exactly = 2) { context.unbindService(any()) }
  }

  // -------------------------------------------------------------------------
  // Full lifecycle
  // -------------------------------------------------------------------------

  @Test
  fun `full lifecycle - bind connect unbind rebind`() {
    // Initial state
    assertThat(binder.session).isNull()

    // Bind
    binder.bind()
    simulateServiceConnected()
    assertThat(binder.session).isEqualTo(mockSession)

    // Unbind
    binder.unbind()

    // Rebind
    binder.bind()
    assertThat(binder.session).isEqualTo(mockSession) // session still set from first connect
  }

  @Test
  fun `full lifecycle - bind connect disconnect rebind reconnect`() {
    binder.bind()
    simulateServiceConnected()
    assertThat(binder.session).isNotNull()

    simulateServiceDisconnected()
    assertThat(binder.session).isNull()

    binder.bind()
    simulateServiceConnected()
    assertThat(binder.session).isEqualTo(mockSession)
  }

  @Test
  fun `onCustomTabsServiceConnected with null session from newSession`() {
    every { mockClient.newSession(any()) } returns null

    binder.bind()
    simulateServiceConnected()

    assertThat(binder.session).isNull()
    verify(exactly = 1) { mockClient.warmup(0L) }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private fun simulateServiceConnected() {
    connectionSlot.captured.onCustomTabsServiceConnected(mockComponentName, mockClient)
  }

  private fun simulateServiceDisconnected() {
    connectionSlot.captured.onServiceDisconnected(mockComponentName)
  }
}
