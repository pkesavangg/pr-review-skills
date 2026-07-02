package com.greatergoods.notification

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test

/**
 * Firebase Cloud Messaging tests for [NotificationService]: `fetchFCMToken` and
 * `subscribeToTopic`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceFirebaseTest : NotificationServiceTestBase() {

    @Test
    fun `fetchFCMToken invokes onSuccess with token when task succeeds`() {
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns true
            every { result } returns "test_fcm_token_123"
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedToken: String? = null
        var capturedError: Exception? = null

        service.fetchFCMToken(
            onSuccess = { capturedToken = it },
            onError = { capturedError = it },
        )

        assertThat(capturedToken).isEqualTo("test_fcm_token_123")
        assertThat(capturedError).isNull()
    }

    @Test
    fun `fetchFCMToken invokes onError when task fails`() {
        val expectedException = RuntimeException("FCM token fetch failed")
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns false
            every { exception } returns expectedException
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedToken: String? = null
        var capturedError: Exception? = null

        service.fetchFCMToken(
            onSuccess = { capturedToken = it },
            onError = { capturedError = it },
        )

        assertThat(capturedToken).isNull()
        assertThat(capturedError).isEqualTo(expectedException)
    }

    @Test
    fun `fetchFCMToken invokes onError with null exception when task fails without exception`() {
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns false
            every { exception } returns null
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedError: Exception? = RuntimeException("should be replaced")

        service.fetchFCMToken(
            onSuccess = { },
            onError = { capturedError = it },
        )

        assertThat(capturedError).isNull()
    }

    @Test
    fun `subscribeToTopic invokes onSuccess when task succeeds`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns true
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("test_topic") } returns mockTask

        var successCalled = false
        var capturedError: Exception? = null

        service.subscribeToTopic(
            topic = "test_topic",
            onSuccess = { successCalled = true },
            onError = { capturedError = it },
        )

        assertThat(successCalled).isTrue()
        assertThat(capturedError).isNull()
    }

    @Test
    fun `subscribeToTopic invokes onError when task fails`() {
        val expectedException = RuntimeException("Subscribe failed")
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns false
            every { exception } returns expectedException
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("fail_topic") } returns mockTask

        var successCalled = false
        var capturedError: Exception? = null

        service.subscribeToTopic(
            topic = "fail_topic",
            onSuccess = { successCalled = true },
            onError = { capturedError = it },
        )

        assertThat(successCalled).isFalse()
        assertThat(capturedError).isEqualTo(expectedException)
    }

    @Test
    fun `subscribeToTopic invokes onError with null when task fails without exception`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns false
            every { exception } returns null
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("topic") } returns mockTask

        var capturedError: Exception? = RuntimeException("should be replaced")

        service.subscribeToTopic(
            topic = "topic",
            onSuccess = { },
            onError = { capturedError = it },
        )

        assertThat(capturedError).isNull()
    }

    @Test
    fun `subscribeToTopic with different topics uses correct topic name`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns true
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("special/topic") } returns mockTask

        var successCalled = false
        service.subscribeToTopic(
            topic = "special/topic",
            onSuccess = { successCalled = true },
            onError = { },
        )

        assertThat(successCalled).isTrue()
        verify { firebaseMessaging.subscribeToTopic("special/topic") }
    }
}
