package com.dmdbrands.gurus.weight.data.storage.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
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
  // 12 (not 2) so a dev device still on the never-shipped intermediate v2 — same version number,
  // different schema — is treated as an upgrade with no migration path and destructively wiped,
  // instead of crashing on Room's identity-hash check. Production (shipped v1) migrates via
  // MIGRATION_1_12. (MOB-1537, PR #2280 review)
  version = 12,
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

    // Exact CREATE VIEW text for entry_view. Must match the @DatabaseView on ActiveEntryEntity and
    // the generated v2 schema BYTE-FOR-BYTE (indentation included): Room's post-migration schema
    // validation compares the SQL stored in sqlite_master against this string. (MOB-1537 / MOB-1526)
    private const val ENTRY_VIEW_SQL =
      "CREATE VIEW `entry_view` AS SELECT * FROM entry e\n" +
        "        WHERE e.operationType != 'delete'\n" +
        "          AND e.pendingDelete = 0\n" +
        "          AND NOT EXISTS (\n" +
        "            SELECT 1 FROM entry d\n" +
        "            WHERE d.accountId = e.accountId\n" +
        "              AND d.entryTimestamp = e.entryTimestamp\n" +
        "              AND d.operationType = 'delete'\n" +
        "          )"

    // ----- Migration 1 -> 12 -----
    // The ONLY production upgrade path: shipped 5.0.x (Room v1, weight/BP only) -> Me.Health 2.0
    // (full multi-product schema). DB versions 2-11 only ever existed on internal builds and were
    // never released, so the historical per-step chain was collapsed into this single migration
    // (MOB-1537 / MOB-1526). The target version is 12 (not 2) so a dev device still on the old
    // never-shipped v2 is wiped rather than crashing on the identity-hash check; the SQL below is
    // the same v2 shape — only the endVersion changed.
    internal val MIGRATION_1_12 = object : Migration(1, 12) {
      @Suppress("LongMethod")
      override fun migrate(connection: SQLiteConnection) {
        // Columns added to the pre-existing weight/BP tables. None of these exist on a shipped
        // 5.0.x DB (they were all introduced by Phase 2), so a plain ADD COLUMN is safe.
        connection.execSQL("ALTER TABLE device ADD COLUMN productType TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE device ADD COLUMN lastModified INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE account ADD COLUMN hasSeenAppReview INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE account ADD COLUMN hasSeenScaleReview INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE account ADD COLUMN accountSettings TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE notification_settings ADD COLUMN willReceiveEmails INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE body_scale_entry ADD COLUMN note TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE bpm_entry ADD COLUMN source TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE entry ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_entry_accountId_operationType` ON `entry` (`accountId`, `operationType`)",
        )

        // New Phase 2 tables. Drop any legacy baby tables first: they never existed on a shipped
        // 5.0.x DB (no-op there), but an internal Phase-2 v1 build may carry an old-shape
        // baby_profiles/baby_entry -> drop so the fresh v2 tables below are the ones that stick.
        connection.execSQL("DROP TABLE IF EXISTS `baby_entry`")
        connection.execSQL("DROP TABLE IF EXISTS `baby_profiles`")
        connection.execSQL(
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
            `isSynced` INTEGER NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            `existsOnServer` INTEGER NOT NULL,
            `activeBabyId` TEXT,
            PRIMARY KEY(`babyId`),
            FOREIGN KEY(`accountId`) REFERENCES `account`(`accountId`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_baby_accountId` ON `baby` (`accountId`)")
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `baby_entry` (
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
            FOREIGN KEY(`id`) REFERENCES `entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`babyId`) REFERENCES `baby`(`babyId`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_baby_entry_babyId` ON `baby_entry` (`babyId`)")
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `product_settings` (
            `accountId` TEXT NOT NULL,
            `productTypes` TEXT NOT NULL,
            `measurementUnits` TEXT NOT NULL,
            `isSynced` INTEGER NOT NULL,
            PRIMARY KEY(`accountId`),
            FOREIGN KEY(`accountId`) REFERENCES `account`(`accountId`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        // Back-fill existing accounts with the weight-only default so the relation is populated.
        connection.execSQL(
          "INSERT OR IGNORE INTO `product_settings` (`accountId`, `productTypes`, `measurementUnits`, `isSynced`) " +
            "SELECT `accountId`, '[\"weight\"]', 'metric', 0 FROM `account`",
        )

        // Recreate entry_view with the v2 definition (filters delete + pendingDelete). A shipped
        // 5.0.x DB has an older view; drop + create to converge.
        connection.execSQL("DROP VIEW IF EXISTS `entry_view`")
        connection.execSQL(ENTRY_VIEW_SQL)
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
            // Bundled SQLite (3.25+) instead of the device's system SQLite, which is tied to
            // API level — minSdk 26 ships 3.18, which lacks window functions and crashes the
            // graph queries (baby/weight/BPM) on API 26-29. The 5.0.x line bundled it for the
            // same reason; the Phase 2 rewrite dropped it (MOB-1129).
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(
              object : Callback() {
                // With BundledSQLiteDriver, Room invokes the SQLiteConnection-based callback;
                // the legacy onCreate(SupportSQLiteDatabase) is NOT called on the driver path,
                // so the Ionic migration worker must be enqueued from this overload.
                override fun onCreate(connection: SQLiteConnection) {
                  super.onCreate(connection)

                  // Start Ionic migration worker when database is first created. Unique-work +
                  // KEEP avoids stacking duplicate workers if Room is recreated mid-retry.
                  val migrationWork = OneTimeWorkRequestBuilder<IonicMigrationWorker>()
                    .addTag("ionic_migration")
                    .build()

                  WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork("ionic_migration", ExistingWorkPolicy.KEEP, migrationWork)
                }
              },
            )
            .addMigrations(MIGRATION_1_12)
            // Only v1 (shipped 5.0.x) ever reached production and it takes MIGRATION_1_12. Every
            // never-shipped internal build (old v2-v11) has no path to v12, so it's destructively
            // wiped instead of crashing — including a device still on the old same-numbered v2,
            // which a downgrade-only fallback would miss. (MOB-1537, PR #2280 review)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        Companion.instance = instance
        instance
      }
  }
}
