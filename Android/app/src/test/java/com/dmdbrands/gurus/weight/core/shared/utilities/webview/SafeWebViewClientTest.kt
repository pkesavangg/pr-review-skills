package com.dmdbrands.gurus.weight.core.shared.utilities.webview

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeWebViewClientTest {

    // isUrlAllowed

    @Test
    fun `HTTPS weightgurus URL is allowed`() {
        assertTrue(SafeWebViewClient.isUrlAllowed("https", "weightgurus.com"))
    }

    @Test
    fun `HTTPS greatergoods URL is allowed`() {
        assertTrue(SafeWebViewClient.isUrlAllowed("https", "greatergoods.com"))
    }

    @Test
    fun `HTTPS Google OAuth URL is allowed`() {
        assertTrue(SafeWebViewClient.isUrlAllowed("https", "accounts.google.com"))
    }

    @Test
    fun `HTTPS Apple sign in URL is allowed`() {
        assertTrue(SafeWebViewClient.isUrlAllowed("https", "appleid.apple.com"))
    }

    @Test
    fun `HTTP URL is rejected`() {
        assertFalse(SafeWebViewClient.isUrlAllowed("http", "weightgurus.com"))
    }

    @Test
    fun `null scheme is rejected`() {
        assertFalse(SafeWebViewClient.isUrlAllowed(null, "weightgurus.com"))
    }

    @Test
    fun `Unknown domain is rejected`() {
        assertFalse(SafeWebViewClient.isUrlAllowed("https", "evil.com"))
    }

    @Test
    fun `Empty host is rejected`() {
        assertFalse(SafeWebViewClient.isUrlAllowed("https", ""))
    }

    // isAllowedDomain

    @Test
    fun `Subdomain of allowed domain is allowed`() {
        assertTrue(SafeWebViewClient.isAllowedDomain("app.weightgurus.com"))
        assertTrue(SafeWebViewClient.isAllowedDomain("api.greatergoods.com"))
    }

    @Test
    fun `Domain that ends with allowed domain string but is not a subdomain is rejected`() {
        assertFalse(SafeWebViewClient.isAllowedDomain("evilweightgurus.com"))
        assertFalse(SafeWebViewClient.isAllowedDomain("notgreatergoods.com"))
    }

    @Test
    fun `Deep subdomain of allowed domain is allowed`() {
        assertTrue(SafeWebViewClient.isAllowedDomain("staging.app.weightgurus.com"))
    }

    // shouldOverrideUrlLoading

    @Test
    fun `shouldOverrideUrlLoading returns false for allowed HTTPS URL`() {
        val client = SafeWebViewClient()
        val view: android.webkit.WebView = mockk(relaxed = true)
        val uri: android.net.Uri = mockk {
            every { scheme } returns "https"
            every { host } returns "weightgurus.com"
        }
        val request: android.webkit.WebResourceRequest = mockk {
            every { url } returns uri
        }

        val result = client.shouldOverrideUrlLoading(view, request)

        assertFalse(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns true for blocked URL`() {
        val client = SafeWebViewClient()
        val view: android.webkit.WebView = mockk(relaxed = true)
        val uri: android.net.Uri = mockk {
            every { scheme } returns "https"
            every { host } returns "evil.com"
        }
        val request: android.webkit.WebResourceRequest = mockk {
            every { url } returns uri
        }

        val result = client.shouldOverrideUrlLoading(view, request)

        assertTrue(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns true for HTTP URL`() {
        val client = SafeWebViewClient()
        val view: android.webkit.WebView = mockk(relaxed = true)
        val uri: android.net.Uri = mockk {
            every { scheme } returns "http"
            every { host } returns "weightgurus.com"
        }
        val request: android.webkit.WebResourceRequest = mockk {
            every { url } returns uri
        }

        val result = client.shouldOverrideUrlLoading(view, request)

        assertTrue(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns true when host is null`() {
        val client = SafeWebViewClient()
        val view: android.webkit.WebView = mockk(relaxed = true)
        val uri: android.net.Uri = mockk {
            every { scheme } returns "https"
            every { host } returns null
        }
        val request: android.webkit.WebResourceRequest = mockk {
            every { url } returns uri
        }

        val result = client.shouldOverrideUrlLoading(view, request)

        assertTrue(result)
    }
}
