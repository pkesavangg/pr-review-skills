// Migration tests seed a full DB and assert many columns/rows, so the per-test methods are
// intentionally long — data setup + assertions don't decompose cleanly.
@file:Suppress("LongMethod")

package com.dmdbrands.gurus.weight.data.storage.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the single [AppDatabase.MIGRATION_1_12] — the only production upgrade path,
 * shipped 5.0.x (Room v1) → Me.Health 2.0 (v12). DB versions 2–11 never shipped and were
 * collapsed into this one migration (MOB-1537 / MOB-1526).
 *
 * The v1 baseline in 1.json still carries the Phase-2 `baby_profiles`/`baby_entry` tables, which a
 * genuine shipped 5.0.x database never had — so the test drops them to reproduce the real device,
 * then runs the migration and validates the full v2 schema + data preservation.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_realShipped5_0_xUpgrade() {
        helper.createDatabase(TEST_DB, 1).apply {
            // Reproduce a genuine shipped 5.0.x DB: weight/BP only, no baby tables.
            execSQL("DROP TABLE IF EXISTS `baby_entry`")
            execSQL("DROP TABLE IF EXISTS `baby_profiles`")

            insert(
                "account",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("accountId", "acct-1")
                    put("firstName", "Test")
                    put("lastName", "User")
                    put("dob", "1990-01-01")
                    put("email", "test@example.com")
                    put("gender", "male")
                    put("isActiveAccount", 1)
                    put("isLoggedIn", 1)
                    put("isExpired", 0)
                    put("isSynced", 1)
                    put("zipcode", "12345")
                },
            )

            // Weight entry (#1) + body_scale_entry
            insert(
                "entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 1)
                    put("accountId", "acct-1")
                    put("entryTimestamp", "2025-06-01T10:00:00.000Z")
                    put("operationType", "create")
                    put("deviceType", "scale")
                    put("deviceId", "dev-1")
                    put("attempts", 0)
                    put("unit", "lb")
                    put("isSynced", 1)
                },
            )
            insert(
                "body_scale_entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 1)
                    put("weight", 1800.0)
                    put("source", "manual")
                },
            )

            // BPM entry (#2) + bpm_entry (PK is `id` on shipped 5.0.x)
            insert(
                "entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 2)
                    put("accountId", "acct-1")
                    put("entryTimestamp", "2025-06-02T10:00:00.000Z")
                    put("operationType", "create")
                    put("deviceType", "bpm")
                    put("deviceId", "dev-1")
                    put("attempts", 0)
                    put("unit", "mmHg")
                    put("isSynced", 1)
                },
            )
            insert(
                "bpm_entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 2)
                    put("systolic", 120)
                    put("diastolic", 80)
                    put("pulse", 72)
                    put("meanArterial", "93")
                    put("note", "morning reading")
                },
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_1_12)

        // Account survived + new columns default correctly.
        db.query("SELECT * FROM account WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("email"))).isEqualTo("test@example.com")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("hasSeenAppReview"))).isEqualTo(0)
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("accountSettings"))).isTrue()
        }

        // Weight entry survived.
        db.query("SELECT * FROM body_scale_entry WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("weight"))).isEqualTo(1800.0)
        }

        // BPM entry survived; PK stays `id`; the v2-added `source` column defaults null.
        db.query("SELECT * FROM bpm_entry WHERE id = 2").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("systolic"))).isEqualTo(120)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("note"))).isEqualTo("morning reading")
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("source"))).isTrue()
        }

        // New tables exist and are empty (no baby data on a shipped 5.0.x DB).
        db.query("SELECT COUNT(*) FROM baby").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        db.query("SELECT COUNT(*) FROM baby_entry").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }

        // product_settings back-filled to the weight-only default for the legacy account.
        db.query("SELECT * FROM product_settings WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("productTypes"))).isEqualTo("[\"weight\"]")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("measurementUnits"))).isEqualTo("metric")
        }

        db.close()
    }
}
