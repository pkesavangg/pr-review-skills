package com.greatergoods.notification

import com.example.notification.NotificationHandler
import com.google.firebase.messaging.FirebaseMessaging
import com.greatergoods.notification.model.BuilderConfig
import com.greatergoods.notification.model.ChannelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Shared setup, mocks, and fixtures for the [NotificationService] test classes.
 *
 * The original single `NotificationServiceTest` was split into smaller, feature
 * focused test classes (channel creation, builder/display, status map, active
 * notifications, show* helpers, Firebase, and integration). Each subclass inherits
 * the identical dispatcher/mocks/fixtures and the same `@Before`/`@After` lifecycle
 * defined here, so behavior is unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class NotificationServiceTestBase {

    protected val testDispatcher = StandardTestDispatcher()

    // --- Mocks ---
    protected val notificationHandler: NotificationHandler = mockk(relaxed = true)
    protected val firebaseMessaging: FirebaseMessaging = mockk()

    protected lateinit var service: NotificationService

    // --- Test fixtures ---
    protected val testChannelId = "test_channel"
    protected val testNotificationName = "test_notification"
    protected val testTitle = "Test Title"
    protected val testContent = "Test Content"
    protected val channelConfig = ChannelConfig(
        id = testChannelId,
        name = "Test Channel",
        importance = 3,
        description = "Test channel description",
    )
    protected val builderConfig = BuilderConfig(
        channelConfig = channelConfig,
        smallIcon = android.R.drawable.ic_notification_overlay,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging

        service = NotificationService(notificationHandler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }
}
