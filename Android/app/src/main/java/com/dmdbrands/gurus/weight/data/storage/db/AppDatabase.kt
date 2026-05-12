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
import com.dmdbrands.gurus.weight.data.storage.db.dao.HistoryDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
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
    BabyProfileEntity::class,
    BabyEntryEntity::class,
  ],
  views = [ActiveEntryEntity::class],
  version = 2,
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

  abstract fun historyDao(): HistoryDao

  companion object {
    // Production 5.0.0 ships DB version 1. This single migration brings it to version 2.
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // device — add 2 columns
        db.execSQL("ALTER TABLE device ADD COLUMN productType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE device ADD COLUMN lastModified INTEGER DEFAULT NULL")

        // account — add 3 columns
        db.execSQL("ALTER TABLE account ADD COLUMN hasSeenAppReview INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE account ADD COLUMN hasSeenScaleReview INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE account ADD COLUMN accountSettings TEXT DEFAULT NULL")

        // notification_settings — add 1 column
        db.execSQL("ALTER TABLE notification_settings ADD COLUMN willReceiveEmails INTEGER NOT NULL DEFAULT 0")

        // baby — new table (FK to account.accountId)
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `baby` (
            `babyId` TEXT NOT NULL, `accountId` TEXT NOT NULL, `name` TEXT NOT NULL,
            `birthdate` TEXT, `sex` TEXT, `birthWeightDecigrams` INTEGER,
            `birthLengthMillimeters` INTEGER, `isBorn` INTEGER, `isOwnedByAccount` INTEGER,
            `permissions` INTEGER, `createdAt` INTEGER, `dueDate` TEXT, `lastUpdated` TEXT,
            `isSynced` INTEGER NOT NULL DEFAULT 0, `isDeleted` INTEGER NOT NULL DEFAULT 0,
            `activeBabyId` TEXT DEFAULT NULL, PRIMARY KEY(`babyId`),
            FOREIGN KEY(`accountId`) REFERENCES `account`(`accountId`) ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_baby_accountId` ON `baby` (`accountId`)")

        // baby_entry — new table
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `baby_entry` (
            `id` INTEGER NOT NULL, `babyId` TEXT NOT NULL, `babyWeightDecigrams` INTEGER,
            `babyLengthMillimeters` INTEGER, `entryNote` TEXT, `entryType` TEXT,
            `feedingTimeLeft` INTEGER, `feedingTimeRight` INTEGER, `feedingMilliliters` INTEGER,
            `diaperType` TEXT, `sleepTime` INTEGER, `babyDisplayWeightDecigrams` INTEGER,
            `photoUri` TEXT, `isPlaceholder` INTEGER, `source` TEXT, PRIMARY KEY(`id`),
            FOREIGN KEY(`id`) REFERENCES `entry`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`babyId`) REFERENCES `baby`(`babyId`) ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_baby_entry_babyId` ON `baby_entry` (`babyId`)")
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
            .addCallback(
              object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                  super.onCreate(db)

                  // Start Ionic migration worker when database is first created
                  val migrationWork = OneTimeWorkRequestBuilder<IonicMigrationWorker>()
                    .addTag("ionic_migration")
                    .build()

                  WorkManager.getInstance(context.applicationContext)
                    .enqueue(migrationWork)
                }
              },
            )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(false)
            .build()
        Companion.instance = instance
        instance
      }
  }
}
