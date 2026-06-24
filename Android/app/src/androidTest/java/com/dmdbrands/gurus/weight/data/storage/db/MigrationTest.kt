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
 * Instrumented test that validates [AppDatabase.MIGRATION_1_2] using Room's
 * [MigrationTestHelper]. Creates a real SQLite DB at version 1 (from the
 * exported schema JSON), inserts representative rows, runs the migration,
 * and asserts the resulting schema + data.
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
    fun migrate1To2() {
        // --- Create DB at version 1 and seed data -----------------------------------
        helper.createDatabase(TEST_DB, 1).apply {
            // Insert an account
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

            // Insert a device
            insert(
                "device",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "dev-1")
                    put("accountId", "acct-1")
                    put("isDeleted", 0)
                    put("isSynced", 1)
                    put("hasServerID", 1)
                },
            )

            // Insert notification_settings
            insert(
                "notification_settings",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("accountId", "acct-1")
                    put("shouldSendEntryNotifications", 1)
                    put("shouldSendWeightInEntryNotifications", 0)
                    put("isSynced", 1)
                },
            )

            // Insert an entry
            insert(
                "entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("accountId", "acct-1")
                    put("entryTimestamp", "2025-06-01T10:00:00.000Z")
                    put("operationType", "create")
                    put("deviceType", "bpm")
                    put("deviceId", "dev-1")
                    put("attempts", 0)
                    put("unit", "lb")
                    put("isSynced", 1)
                },
            )

            // Insert a bpm_entry (FK to entry.id = 1, the auto-generated id)
            insert(
                "bpm_entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 1)
                    put("systolic", 120)
                    put("diastolic", 80)
                    put("pulse", 72)
                    put("meanArterial", "93")
                    put("note", "morning reading")
                },
            )

            close()
        }

        // --- Run MIGRATION_1_2 -------------------------------------------------------
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        // --- Assert new columns on existing tables -----------------------------------

        // device: productType, lastModified
        db.query("PRAGMA table_info(device)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("productType")
            assertThat(columns).contains("lastModified")
        }

        // account: hasSeenAppReview, hasSeenScaleReview, accountSettings (activeBabyId is on baby table)
        db.query("PRAGMA table_info(account)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("hasSeenAppReview")
            assertThat(columns).contains("hasSeenScaleReview")
            assertThat(columns).contains("accountSettings")
        }

        // notification_settings: willReceiveEmails
        db.query("PRAGMA table_info(notification_settings)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("willReceiveEmails")
        }

        // --- Assert new tables created -----------------------------------------------

        // baby table exists
        db.query("PRAGMA table_info(baby)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("babyId")
            assertThat(columns).contains("accountId")
            assertThat(columns).contains("name")
            assertThat(columns).contains("isSynced")
            assertThat(columns).contains("isDeleted")
            assertThat(columns).contains("activeBabyId")
        }

        // baby_entry table exists
        db.query("PRAGMA table_info(baby_entry)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("id")
            assertThat(columns).contains("babyId")
            assertThat(columns).contains("babyWeightDecigrams")
        }

        // --- Assert existing bpm_entry data preserved --------------------------------
        db.query("SELECT * FROM bpm_entry WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("systolic"))).isEqualTo(120)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("diastolic"))).isEqualTo(80)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("pulse"))).isEqualTo(72)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("meanArterial"))).isEqualTo("93")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("note"))).isEqualTo("morning reading")
        }

        // --- Assert default values for new columns on existing rows ------------------
        db.query("SELECT * FROM account WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("hasSeenAppReview"))).isEqualTo(0)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("hasSeenScaleReview"))).isEqualTo(0)
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("accountSettings"))).isTrue()
        }

        db.query("SELECT * FROM notification_settings WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("willReceiveEmails"))).isEqualTo(0)
        }

        db.close()
    }

    /**
     * Validates [AppDatabase.MIGRATION_6_7] (MOB-438): adds the nullable `note` column to
     * body_scale_entry, defaults existing rows to null, and preserves existing data.
     */
    @Test
    fun migrate6To7() {
        // --- Create DB at version 6 and seed a weight entry --------------------------
        helper.createDatabase(TEST_DB, 6).apply {
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
            insert(
                "entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
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
            // body_scale_entry at v6 has no note column yet (FK id = entry.id = 1)
            insert(
                "body_scale_entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 1)
                    put("weight", 1800.0)
                    put("source", "manual")
                },
            )
            close()
        }

        // --- Run MIGRATION_6_7 -------------------------------------------------------
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7,
        )

        // note column was added
        db.query("PRAGMA table_info(body_scale_entry)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertThat(columns).contains("note")
        }

        // existing data survived; note defaults to null
        db.query("SELECT * FROM body_scale_entry WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("weight"))).isEqualTo(1800.0)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("source"))).isEqualTo("manual")
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("note"))).isTrue()
        }

        db.close()
    }
}
