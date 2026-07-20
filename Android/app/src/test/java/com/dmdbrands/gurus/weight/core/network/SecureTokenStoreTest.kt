package com.dmdbrands.gurus.weight.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Covers the create/recover orchestration in [SecureTokenStore]. The real Android keystore is
 * replaced with an injected factory so the recovery path (invalidated master key → wipe + retry)
 * can be exercised on the JVM. (MOB-1598)
 */
class SecureTokenStoreTest {

    private companion object {
        const val PREFS_FILE_NAME = "secure_tokens"
        const val META_PREFS_FILE_NAME = "secure_tokens_meta"
        const val ENCRYPTION_FAILURE_COUNT_KEY = "encryption_failure_count"
    }

    private lateinit var context: Context
    private lateinit var metaPrefs: SharedPreferences
    private lateinit var metaEditor: SharedPreferences.Editor

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        metaPrefs = mockk(relaxed = true)
        metaEditor = mockk(relaxed = true)
        every { context.getSharedPreferences(META_PREFS_FILE_NAME, Context.MODE_PRIVATE) } returns metaPrefs
        every { context.deleteSharedPreferences(any()) } returns true
        every { metaPrefs.edit() } returns metaEditor
        every { metaEditor.putInt(any(), any()) } returns metaEditor
        every { metaEditor.apply() } just runs
    }

    @Test
    fun `happy path opens the store once and never touches recovery`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        var factoryCalls = 0
        val factory: (Context) -> SharedPreferences = { factoryCalls++; prefs }

        val store = SecureTokenStore(context, factory)

        assertThat(store.isAvailable).isTrue()
        assertThat(factoryCalls).isEqualTo(1)
        // No recovery: the corrupt-prefs delete and the failure-count reset must NOT run.
        verify(exactly = 0) { context.deleteSharedPreferences(any()) }
        verify(exactly = 0) { metaEditor.putInt(ENCRYPTION_FAILURE_COUNT_KEY, 0) }
    }

    @Test
    fun `invalidated key recovers by wiping prefs, retrying, and incrementing failure count`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        var factoryCalls = 0
        val factory: (Context) -> SharedPreferences = {
            factoryCalls++
            if (factoryCalls == 1) throw GeneralSecurityException("master key invalidated") else prefs
        }

        val store = SecureTokenStore(context, factory)

        assertThat(store.isAvailable).isTrue()
        assertThat(factoryCalls).isEqualTo(2)
        verify { context.deleteSharedPreferences(PREFS_FILE_NAME) }
        // Recovery increments (not resets) the failure counter — a recovery still means this
        // session paid the wipe/re-login cost. Only a subsequent successful token save
        // (TokenManager.setTokens) resets it to 0, so a device whose key is invalidated on every
        // launch doesn't mask the repeating loop from the MOB-1537/1526 safeguard. (MOB-1598)
        verify { metaEditor.putInt(ENCRYPTION_FAILURE_COUNT_KEY, 1) }
        verify(exactly = 0) { metaEditor.putInt(ENCRYPTION_FAILURE_COUNT_KEY, 0) }
    }

    @Test
    fun `store stays unavailable when recovery also fails`() {
        var factoryCalls = 0
        val factory: (Context) -> SharedPreferences = {
            factoryCalls++
            throw GeneralSecurityException("keystore unusable")
        }

        val store = SecureTokenStore(context, factory)

        assertThat(store.isAvailable).isFalse()
        // Exactly one recovery attempt — never loops.
        assertThat(factoryCalls).isEqualTo(2)
        // Failure-count increment only happens on successful recovery.
        verify(exactly = 0) { metaEditor.putInt(any(), any()) }
    }

    @Test
    fun `transient IOException during create does not wipe prefs or invoke recovery`() {
        var factoryCalls = 0
        val factory: (Context) -> SharedPreferences = {
            factoryCalls++
            throw IOException("disk full")
        }

        val store = SecureTokenStore(context, factory)

        // A transient I/O failure is not a key-invalidation case: the store is unavailable for
        // this construction, but recovery must NOT run — otherwise-valid tokens and the master
        // key must survive so a later attempt can succeed once the condition clears. (MOB-1598)
        assertThat(store.isAvailable).isFalse()
        assertThat(factoryCalls).isEqualTo(1)
        verify(exactly = 0) { context.deleteSharedPreferences(any()) }
        verify(exactly = 0) { metaEditor.putInt(any(), any()) }
    }

    @Test
    fun `non-security exception during create does not wipe prefs or invoke recovery`() {
        var factoryCalls = 0
        val factory: (Context) -> SharedPreferences = {
            factoryCalls++
            throw IllegalStateException("unexpected runtime failure")
        }

        val store = SecureTokenStore(context, factory)

        assertThat(store.isAvailable).isFalse()
        assertThat(factoryCalls).isEqualTo(1)
        verify(exactly = 0) { context.deleteSharedPreferences(any()) }
        verify(exactly = 0) { metaEditor.putInt(any(), any()) }
    }
}
