// Migration tests seed a full DB and assert many columns/rows per version step, so the
// per-test methods are intentionally long — data setup + assertions don't decompose cleanly.
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
     * MOB-948 — full production upgrade path.
     *
     * Every shipped 5.0.x build (5.0.0–5.0.3) is Room DB **version 1**; versions 2–8 only
     * ever existed on internal Phase 2 builds. So the ONLY migration path a real user takes
     * on the 5.0.3 → 5.1.0 update is the whole chain **1 → 8** in one shot. This test creates
     * a version-1 database with representative weight, BPM, and baby data, runs the entire
     * migration chain, and asserts nothing is lost and the Phase 2 additions land correctly:
     *  - existing account / weight / BPM rows survive
     *  - baby_profiles → baby (id→babyId, biologicalSex→sex) with data preserved
     *  - baby_entry column renames (babyProfileId→babyId, photo→photoUri) with data preserved
     *  - bpm_entry PK rename (id→entryId) with data preserved
     *  - product_settings back-filled to the weight-only default for the legacy account
     */
    @Test
    fun migrate1To8_fullProductionUpgradePath() {
        // --- Create DB at version 1 and seed representative 5.0.3 data ---------------
        helper.createDatabase(TEST_DB, 1).apply {
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

            // entry #1 → weight (body_scale_entry)
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

            // entry #2 → BPM (bpm_entry, PK `id` at v1)
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

            // baby_profiles row (renamed to `baby` in 4→5; biologicalSex→sex, id→babyId)
            insert(
                "baby_profiles",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "baby-1")
                    put("accountId", "acct-1")
                    put("name", "Baby Test")
                    put("biologicalSex", "male")
                    put("birthWeightDecigrams", 35000)
                    put("isBorn", 1)
                    put("isOwnedByAccount", 1)
                },
            )

            // entry #3 → baby entry (baby_entry: babyProfileId→babyId, photo→photoUri)
            insert(
                "entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 3)
                    put("accountId", "acct-1")
                    put("entryTimestamp", "2025-06-03T10:00:00.000Z")
                    put("operationType", "create")
                    put("deviceType", "baby")
                    put("deviceId", "dev-1")
                    put("attempts", 0)
                    put("unit", "lb")
                    put("isSynced", 1)
                },
            )
            insert(
                "baby_entry",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", 3)
                    put("babyProfileId", "baby-1")
                    put("babyWeightDecigrams", 36000)
                    put("entryType", "weight")
                    put("photo", "file://old/photo.jpg")
                    put("source", "manual")
                },
            )

            close()
        }

        // --- Run the FULL chain 1 → 11 -----------------------------------------------
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
        )

        // --- Account survived --------------------------------------------------------
        db.query("SELECT * FROM account WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("email")))
                .isEqualTo("test@example.com")
        }

        // --- Weight entry survived ---------------------------------------------------
        db.query("SELECT * FROM body_scale_entry WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("weight"))).isEqualTo(1800.0)
        }

        // --- BPM entry survived and PK renamed id → entryId --------------------------
        db.query("SELECT * FROM bpm_entry WHERE entryId = 2").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("systolic"))).isEqualTo(120)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("diastolic"))).isEqualTo(80)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("note")))
                .isEqualTo("morning reading")
            // MIGRATION_8_9 (MOB-1173) adds the nullable source column; legacy rows are null.
            assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("source"))).isTrue()
        }

        // --- baby_profiles → baby (id→babyId, biologicalSex→sex), data preserved -----
        db.query("SELECT * FROM baby WHERE babyId = 'baby-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("name"))).isEqualTo("Baby Test")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("sex"))).isEqualTo("male")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("birthWeightDecigrams")))
                .isEqualTo(35000)
            // MIGRATION_10_11 (MOB-1476) backfills every pre-existing (server-sourced) baby to
            // existsOnServer = 1. If it stayed 0, refresh()'s guarded reconcile-delete would treat
            // this synced baby as a never-synced offline row, delete it, and cascade-wipe its
            // baby_entry rows on the first post-upgrade refresh (the MOB-598 failure).
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("existsOnServer"))).isEqualTo(1)
        }

        // --- baby_entry renames (babyProfileId→babyId, photo→photoUri), preserved ----
        db.query("SELECT * FROM baby_entry WHERE id = 3").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("babyId"))).isEqualTo("baby-1")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("photoUri")))
                .isEqualTo("file://old/photo.jpg")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("babyWeightDecigrams")))
                .isEqualTo(36000)
        }

        // --- product_settings back-filled to weight-only default for legacy account --
        db.query("SELECT * FROM product_settings WHERE accountId = 'acct-1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("productTypes")))
                .isEqualTo("[\"weight\"]")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("measurementUnits")))
                .isEqualTo("metric")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("isSynced"))).isEqualTo(0)
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
