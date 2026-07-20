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
    fun `invalidated key recovers by wiping prefs, retrying, and resetting failure count`() {
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
        verify { metaEditor.putInt(ENCRYPTION_FAILURE_COUNT_KEY, 0) }
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
        // Failure-count reset only happens on successful recovery.
        verify(exactly = 0) { metaEditor.putInt(ENCRYPTION_FAILURE_COUNT_KEY, 0) }
    }
}
