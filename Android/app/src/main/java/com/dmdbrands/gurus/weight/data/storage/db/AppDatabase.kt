package com.dmdbrands.gurus.weight.data.storage.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dmdbrands.gurus.weight.data.storage.db.converter.DateConverter
import com.dmdbrands.gurus.weight.data.storage.db.converter.JsonConverter
import com.dmdbrands.gurus.weight.data.storage.db.converter.WeightUnitConverter
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyEntryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryReadDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.ProductSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.StreaksSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.BodyScaleEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.BpmEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceMetaDataEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.R4ScalePreferenceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.ActiveEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.log.LogEntity
import com.dmdbrands.gurus.weight.migration.service.IonicMigrationWorker
import kotlinx.coroutines.Dispatchers
import android.content.Context

/**
 * Main database class for the MeApp application.
 */
@Database(
  entities = [
    AccountEntity::class,
    DeviceEntity::class,
    BodyScaleEntity::class,
    DeviceMetaDataEntity::class,
    BpmEntity::class,
    R4ScalePreferenceEntity::class,
    EntryEntity::class,
    BodyScaleEntryEntity::class,
    BodyScaleEntryMetricEntity::class,
    BpmEntryEntity::class,
    LogEntity::class,
    WeightCompSettingsEntity::class,
    GoalSettingsEntity::class,
    StreaksSettingsEntity::class,
    WeightlessSettingsEntity::class,
    NotificationSettingsEntity::class,
    DashboardSettingsEntity::class,
    IntegrationsSettingsEntity::class,
    ProductSettingsEntity::class,
    BabyProfileEntity::class,
    BabyEntryEntity::class,
  ],
  views = [ActiveEntryEntity::class],
  version = 7,
  exportSchema = true,
)
@TypeConverters(DateConverter::class, JsonConverter::class, WeightUnitConverter::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao

  abstract fun deviceDao(): DeviceDao

  abstract fun entryDao(): EntryDao

  abstract fun logDao(): LogDao

  abstract fun babyProfileDao(): BabyProfileDao

  abstract fun babyEntryDao(): BabyEntryDao

  abstract fun entryReadDao(): EntryReadDao

  companion object {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE device ADD COLUMN productType TEXT DEFAULT NULL")
      }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE device ADD COLUMN lastModified INTEGER DEFAULT NULL")
      }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN activeBabyId TEXT DEFAULT NULL")
      }
    }

    // ----- Migration 4 → 5 -----
    // Task 1: account — add 4 new columns
    // Task 2: notification_settings — add willReceiveEmails
    // Task 3: baby_entry — rename babyProfileId→babyId, photo→photoUri (table recreation)
    // Task 4: bpm_entry — rename PK id→entryId (table recreation)
    // Task 5: baby_profiles → baby table, rename PK + columns, add new fields (table recreation)
    @Suppress("LongMethod")
    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {

        // ── Task 1: account — add 3 columns ────────────────────────────────────
        db.execSQL("ALTER TABLE account ADD COLUMN hasSeenAppReview INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE account ADD COLUMN hasSeenScaleReview INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE account ADD COLUMN accountSettings TEXT DEFAULT NULL")

        // ── Task 2: notification_settings — add willReceiveEmails ───────────────
        db.execSQL(
          "ALTER TABLE notification_settings ADD COLUMN willReceiveEmails INTEGER NOT NULL DEFAULT 0",
        )

        // ── Task 5: baby_profiles → baby (table rename + column renames + new fields)
        //    Do this before Task 3 because baby_entry has a FK into this table.
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `baby` (
            `babyId` TEXT NOT NULL,
            `accountId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `birthdate` TEXT,
            `sex` TEXT,
            `birthWeightDecigrams` INTEGER,
            `birthLengthMillimeters` INTEGER,
            `isBorn` INTEGER,
            `isOwnedByAccount` INTEGER,
            `permissions` INTEGER,
            `createdAt` INTEGER,
            `dueDate` TEXT,
            `lastUpdated` TEXT,
            `isSynced` INTEGER NOT NULL DEFAULT 0,
            `isDeleted` INTEGER NOT NULL DEFAULT 0,
            `activeBabyId` TEXT DEFAULT NULL,
            PRIMARY KEY(`babyId`)
          )
          """.trimIndent(),
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_baby_accountId` ON `baby` (`accountId`)",
        )
        // Copy data — birthDate (Long) is cast to TEXT to match new String? type
        db.execSQL(
          """
          INSERT INTO `baby`
            (babyId, accountId, name, birthdate, sex, birthWeightDecigrams,
             birthLengthMillimeters, isBorn, isOwnedByAccount, permissions, createdAt,
             dueDate, lastUpdated, isSynced, isDeleted, activeBabyId)
          SELECT
            id, accountId, name,
            CASE WHEN birthDate IS NULL THEN NULL ELSE CAST(birthDate AS TEXT) END,
            biologicalSex, birthWeightDecigrams, birthLengthMillimeters,
            isBorn, isOwnedByAccount, babyPermissions, createdAt,
            NULL, NULL, 0, 0, NULL
          FROM `baby_profiles`
          """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `baby_profiles`")

        // ── Task 3: baby_entry — rename babyProfileId→babyId, photo→photoUri ──
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `baby_entry_new` (
            `id` INTEGER NOT NULL,
            `babyId` TEXT NOT NULL,
            `babyWeightDecigrams` INTEGER,
            `babyLengthMillimeters` INTEGER,
            `entryNote` TEXT,
            `entryType` TEXT,
            `feedingTimeLeft` INTEGER,
            `feedingTimeRight` INTEGER,
            `feedingMilliliters` INTEGER,
            `diaperType` TEXT,
            `sleepTime` INTEGER,
            `babyDisplayWeightDecigrams` INTEGER,
            `photoUri` TEXT,
            `isPlaceholder` INTEGER,
            `source` TEXT,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`id`) REFERENCES `entry`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`babyId`) REFERENCES `baby`(`babyId`) ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_baby_entry_babyId` ON `baby_entry_new` (`babyId`)",
        )
        db.execSQL(
          """
          INSERT INTO `baby_entry_new`
            (id, babyId, babyWeightDecigrams, babyLengthMillimeters, entryNote, entryType,
             feedingTimeLeft, feedingTimeRight, feedingMilliliters, diaperType, sleepTime,
             babyDisplayWeightDecigrams, photoUri, isPlaceholder, source)
          SELECT
            id, babyProfileId, babyWeightDecigrams, babyLengthMillimeters, entryNote, entryType,
            feedingTimeLeft, feedingTimeRight, feedingMilliliters, diaperType, sleepTime,
            babyDisplayWeightDecigrams, photo, isPlaceholder, source
          FROM `baby_entry`
          """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `baby_entry`")
        db.execSQL("ALTER TABLE `baby_entry_new` RENAME TO `baby_entry`")

        // ── Task 4: bpm_entry — rename PK id → entryId ─────────────────────────
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `bpm_entry_new` (
            `entryId` INTEGER NOT NULL,
            `systolic` INTEGER NOT NULL,
            `diastolic` INTEGER NOT NULL,
            `pulse` INTEGER NOT NULL,
            `meanArterial` TEXT NOT NULL,
            `note` TEXT,
            PRIMARY KEY(`entryId`),
            FOREIGN KEY(`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        db.execSQL(
          """
          INSERT INTO `bpm_entry_new` (entryId, systolic, diastolic, pulse, meanArterial, note)
          SELECT id, systolic, diastolic, pulse, meanArterial, note
          FROM `bpm_entry`
          """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `bpm_entry`")
        db.execSQL("ALTER TABLE `bpm_entry_new` RENAME TO `bpm_entry`")
      }
    }

    // ----- Migration 5 → 6 -----
    // Add composite index on (accountId, operationType) to speed up entry_view's NOT EXISTS subquery.
    private val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_accountId_operationType` ON `entry` (`accountId`, `operationType`)")
      }
    }

    // Phase 2 (MOB-377): per-account product settings (productTypes + measurementUnits).
    private val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `product_settings` (
            `accountId` TEXT NOT NULL,
            `productTypes` TEXT NOT NULL,
            `measurementUnits` TEXT NOT NULL,
            `isSynced` INTEGER NOT NULL,
            PRIMARY KEY(`accountId`),
            FOREIGN KEY(`accountId`) REFERENCES `account`(`accountId`) ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        // Backfill existing accounts with the weight-only default so the relation is populated.
        db.execSQL(
          "INSERT OR IGNORE INTO `product_settings` (`accountId`, `productTypes`, `measurementUnits`, `isSynced`) " +
            "SELECT `accountId`, '[\"weight\"]', 'metric', 0 FROM `account`",
        )
      }
    }

    @Volatile
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        val instance =
          Room
            .databaseBuilder(
              context.applicationContext,
              AppDatabase::class.java,
              "MeApp",
            )
            // Bundled SQLite (currently 3.46+) instead of the device's system SQLite.
            // Required for window functions (need SQLite 3.25+); the device's SQLite is
            // tied to API level — minSdk 26 ships 3.18, which lacks them. Bundled gives
            // us a single version across all supported API levels.
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(
              object : Callback() {
                // When `.setDriver(BundledSQLiteDriver())` is used, Room invokes the
                // SQLiteConnection-based callback overloads. The legacy
                // `onCreate(SupportSQLiteDatabase)` is NOT called on the new driver path,
                // which is why the Ionic migration worker was never enqueued.
                override fun onCreate(connection: SQLiteConnection) {
                  super.onCreate(connection)

                  // Start Ionic migration worker when database is first created.
                  // Unique-work + KEEP policy ensures that if Room is recreated mid-retry
                  // (e.g. after performEmergencyCleanup), we don't stack duplicate workers
                  // on top of one that's already retrying.
                  val migrationWork = OneTimeWorkRequestBuilder<IonicMigrationWorker>()
                    .addTag("ionic_migration")
                    .build()

                  WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork("ionic_migration", ExistingWorkPolicy.KEEP, migrationWork)
                }
              },
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration(false)
            .build()
        Companion.instance = instance
        instance
      }
  }
}
