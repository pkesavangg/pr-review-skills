package com.dmdbrands.gurus.weight.core.network.interceptors

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp interceptor that handles SSL certificate pin mismatches.
 *
 * When the server certificate chain does not match the pinned intermediate CA hash,
 * OkHttp throws [SSLPeerUnverifiedException]. This interceptor catches that exception,
 * logs it as a non-fatal event, and navigates the user to the [AppRoute.ForceUpdate] screen
 * so they are prompted to update the app.
 *
 * Why intermediate CA (not leaf certificate):
 * Leaf certificates rotate every ~90 days (Let's Encrypt). Pinning to the leaf would
 * break the app silently on every renewal. The intermediate CA rotates much less
 * frequently and is replaced in a coordinated, planned process — see the rotation
 * procedure documented in Android/CLAUDE.md.
 */
class CertificatePinningInterceptor @Inject constructor(
    private val appNavigationService: IAppNavigationService,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: SSLPeerUnverifiedException) {
            val host = chain.request().url.host
            AppLog.e(TAG, "Certificate pin mismatch for $host — possible MitM attack or cert rotation required", e.toString())

            // TODO(MA-3355): Replace with Crashlytics non-fatal logging once Task 1.4 is complete:
            //   FirebaseCrashlytics.getInstance().recordException(e)

            runBlocking(Dispatchers.IO) {
                appNavigationService.navigateTo(AppRoute.ForceUpdate)
            }

            throw e
        }
    }

    companion object {
        private const val TAG = "CertificatePinningInterceptor"
    }
}
