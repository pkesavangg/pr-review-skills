package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class BaseServiceTest {

    // --- Mocks ---
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: TestableBaseService

    @BeforeEach
    fun setUp() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(
            available = true,
            unAvailable = false,
        )
        service = TestableBaseService(connectivityObserver, dialogQueueService, appNavigationService)
    }

    // -------------------------------------------------------------------------
    // constructor — protected properties are accessible via subclass
    // -------------------------------------------------------------------------

    @Test
    fun `connectivityObserver is injected correctly`() {
        assertThat(service.exposedConnectivityObserver).isEqualTo(connectivityObserver)
    }

    @Test
    fun `dialogQueueService is injected correctly`() {
        assertThat(service.exposedDialogQueueService).isEqualTo(dialogQueueService)
    }

    @Test
    fun `appNavigationService is injected correctly`() {
        assertThat(service.exposedAppNavigationService).isEqualTo(appNavigationService)
    }

    // -------------------------------------------------------------------------
    // isNetworkAvailable — negates unAvailable from connectivity observer
    // -------------------------------------------------------------------------

    @Test
    fun `isNetworkAvailable returns true when network is available`() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(
            available = true,
            unAvailable = false,
        )
        assertThat(service.exposedIsNetworkAvailable()).isTrue()
    }

    @Test
    fun `isNetworkAvailable returns false when network is unavailable`() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(
            available = false,
            unAvailable = true,
        )
        assertThat(service.exposedIsNetworkAvailable()).isFalse()
    }

    // -------------------------------------------------------------------------
    // requireNetworkAvailable — runs onError or block based on network state
    // -------------------------------------------------------------------------

    @Test
    fun `requireNetworkAvailable runs block and not onError when online`() {
        var blockRan = false
        var errorRan = false

        service.exposedRequireNetworkAvailable(
            onError = { errorRan = true },
            block = { blockRan = true },
        )

        assertThat(blockRan).isTrue()
        assertThat(errorRan).isFalse()
    }

    @Test
    fun `requireNetworkAvailable runs onError and not block when offline`() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(
            available = false,
            unAvailable = true,
        )
        var blockRan = false
        var errorRan = false

        service.exposedRequireNetworkAvailable(
            onError = { errorRan = true },
            block = { blockRan = true },
        )

        assertThat(errorRan).isTrue()
        assertThat(blockRan).isFalse()
    }

    @Test
    fun `requireNetworkAvailable uses default empty block when not provided`() {
        var errorRan = false

        // Only provide onError — block defaults to {}
        service.exposedRequireNetworkAvailable(
            onError = { errorRan = true },
        )

        // Online, so default block runs (no-op), onError does not
        assertThat(errorRan).isFalse()
    }

    // -------------------------------------------------------------------------
    // showNetworkErrorAndThrow — shows toast then throws Exception
    // -------------------------------------------------------------------------

    @Test
    fun `showNetworkErrorAndThrow shows toast with network error message`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        try {
            service.exposedShowNetworkErrorAndThrow()
        } catch (_: Exception) {
            // expected
        }

        assertThat(toastSlot.captured.message).isEqualTo(ToastStrings.Error.NetworkError.Message)
        assertThat(toastSlot.captured.title).isNull()
        assertThat(toastSlot.captured.action).isNull()
    }

    @Test
    fun `showNetworkErrorAndThrow throws exception with correct message`() {
        val exception = assertThrows(Exception::class.java) {
            service.exposedShowNetworkErrorAndThrow()
        }
        assertThat(exception.message).isEqualTo("No network connection available")
    }

    // -------------------------------------------------------------------------
    // showNetworkError — shows toast without throwing
    // -------------------------------------------------------------------------

    @Test
    fun `showNetworkError shows toast with network error message`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.exposedShowNetworkError()

        assertThat(toastSlot.captured.message).isEqualTo(ToastStrings.Error.NetworkError.Message)
        assertThat(toastSlot.captured.title).isNull()
        assertThat(toastSlot.captured.action).isNull()
    }

    // -------------------------------------------------------------------------
    // showSuccessToast — creates Toast with title, message, null action
    // -------------------------------------------------------------------------

    @Test
    fun `showSuccessToast creates toast with provided title and message`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.exposedShowSuccessToast("Done!", "Entry saved.")

        assertThat(toastSlot.captured.title).isEqualTo("Done!")
        assertThat(toastSlot.captured.message).isEqualTo("Entry saved.")
        assertThat(toastSlot.captured.action).isNull()
    }

    // -------------------------------------------------------------------------
    // showErrorToast — creates Toast with nullable title, message, null action
    // -------------------------------------------------------------------------

    @Test
    fun `showErrorToast creates toast with title and message`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.exposedShowErrorToast("Error!", "Something failed.")

        assertThat(toastSlot.captured.title).isEqualTo("Error!")
        assertThat(toastSlot.captured.message).isEqualTo("Something failed.")
        assertThat(toastSlot.captured.action).isNull()
    }

    @Test
    fun `showErrorToast creates toast with null title when title is null`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.exposedShowErrorToast(null, "Something failed.")

        assertThat(toastSlot.captured.title).isNull()
        assertThat(toastSlot.captured.message).isEqualTo("Something failed.")
    }

    @Test
    fun `showErrorToast uses null title by default when title not provided`() {
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.exposedShowErrorToastDefaultTitle("Something broke")

        assertThat(toastSlot.captured.title).isNull()
        assertThat(toastSlot.captured.message).isEqualTo("Something broke")
    }

    // -------------------------------------------------------------------------
    // checkInternetError — returns true only for HttpException with code 0
    // -------------------------------------------------------------------------

    @Test
    fun `checkInternetError returns false for null`() {
        assertThat(service.exposedCheckInternetError(null)).isFalse()
    }

    @Test
    fun `checkInternetError returns false for non-HttpException`() {
        assertThat(service.exposedCheckInternetError(RuntimeException("fail"))).isFalse()
    }

    @Test
    fun `checkInternetError returns false for IOException`() {
        assertThat(service.exposedCheckInternetError(java.io.IOException("timeout"))).isFalse()
    }

    @Test
    fun `checkInternetError returns true for HttpException with code 0`() {
        val httpException = mockk<HttpException>()
        every { httpException.code() } returns 0
        assertThat(service.exposedCheckInternetError(httpException)).isTrue()
    }

    @Test
    fun `checkInternetError returns false for HttpException with code 404`() {
        val response = Response.error<Any>(404, "".toResponseBody())
        assertThat(service.exposedCheckInternetError(HttpException(response))).isFalse()
    }

    @Test
    fun `checkInternetError returns false for HttpException with code 500`() {
        val response = Response.error<Any>(500, "".toResponseBody())
        assertThat(service.exposedCheckInternetError(HttpException(response))).isFalse()
    }
}

/**
 * Concrete subclass of BaseService that exposes protected methods for testing.
 */
class TestableBaseService(
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
    appNavigationService: IAppNavigationService,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService) {

    val exposedConnectivityObserver get() = connectivityObserver
    val exposedDialogQueueService get() = dialogQueueService
    val exposedAppNavigationService get() = appNavigationService

    fun exposedIsNetworkAvailable(): Boolean = isNetworkAvailable()

    fun exposedRequireNetworkAvailable(
        onError: () -> Unit,
        block: () -> Unit = {},
    ) = requireNetworkAvailable(onError, block)

    fun exposedShowNetworkErrorAndThrow() = showNetworkErrorAndThrow()

    fun exposedShowNetworkError() = showNetworkError()

    fun exposedShowSuccessToast(title: String, message: String) = showSuccessToast(title, message)

    fun exposedShowErrorToast(title: String? = null, message: String) = showErrorToast(title, message)

    fun exposedCheckInternetError(error: Throwable?): Boolean = checkInternetError(error)

    fun exposedShowErrorToastDefaultTitle(message: String) = showErrorToast(message = message)
}
