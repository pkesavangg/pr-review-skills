package com.dmdbrands.gurus.weight.core.helpers

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import io.mockk.every
import io.mockk.mockk
import retrofit2.HttpException
import retrofit2.Response

/**
 * Creates an [HttpException] with a mocked [Response] for the given HTTP status code.
 * Use in all HTTP error path tests across service and repository test classes.
 */
fun httpException(code: Int): HttpException {
    val response = mockk<Response<*>> {
        every { code() } returns code
        every { message() } returns "Mock HTTP error"
        every { errorBody() } returns null
    }
    return HttpException(response)
}

/**
 * Stubs [IConnectivityObserver.getCurrentNetworkState] to return an available network state.
 */
fun IConnectivityObserver.stubNetworkAvailable() {
    every { getCurrentNetworkState() } returns NetworkState(available = true, unAvailable = false)
}

/**
 * Stubs [IConnectivityObserver.getCurrentNetworkState] to return an unavailable network state.
 */
fun IConnectivityObserver.stubNetworkUnavailable() {
    every { getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
}
