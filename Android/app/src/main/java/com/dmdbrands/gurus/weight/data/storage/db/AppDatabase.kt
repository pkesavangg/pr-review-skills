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
  version = 5,
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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Drop and recreate baby_profiles with full schema.
        // The table was added to the Room entity list in v3 without a corresponding migration,
        // so existing installs may not have it (or may have an outdated schema). The feature
        // (Smart Baby Scale) was not yet shipped to users at the time this migration was written,
        // so no user data is at risk. New fields (activeBabyId, isSynced, isDeleted) are also
        // added in this version and are not present in any prior schema.
        db.execSQL("DROP TABLE IF EXISTS `baby_profiles`")
        db.execSQL(
          """
          CREATE TABLE `baby_profiles` (
            `id` TEXT NOT NULL,
            `accountId` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `isOwnedByAccount` INTEGER,
            `babyPermissions` INTEGER,
            `birthDate` INTEGER,
            `dueDate` INTEGER,
            `isBorn` INTEGER,
            `biologicalSex` TEXT,
            `birthWeightDecigrams` INTEGER,
            `birthLengthMillimeters` INTEGER,
            `lastUpdated` TEXT,
            `isSynced` INTEGER NOT NULL DEFAULT 0,
            `isDeleted` INTEGER NOT NULL DEFAULT 0,
            `activeBabyId` TEXT,
            PRIMARY KEY(`id`)
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX `index_baby_profiles_accountId` ON `baby_profiles` (`accountId`)"
        )

        // Drop and recreate baby_entry (depends on baby_profiles, same missing-migration issue)
        db.execSQL("DROP TABLE IF EXISTS `baby_entry`")
        db.execSQL(
          """
          CREATE TABLE `baby_entry` (
            `id` INTEGER NOT NULL,
            `babyProfileId` TEXT NOT NULL,
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
            `photo` TEXT,
            `isPlaceholder` INTEGER,
            `source` TEXT,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`id`) REFERENCES `entry`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`babyProfileId`) REFERENCES `baby_profiles`(`id`) ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX `index_baby_entry_babyProfileId` ON `baby_entry` (`babyProfileId`)"
        )

        // Recreate account table without activeBabyId column
        db.execSQL(
          """
          CREATE TABLE `account_new` (
            `accountId` TEXT NOT NULL,
            `firstName` TEXT NOT NULL,
            `lastName` TEXT NOT NULL,
            `dob` TEXT NOT NULL,
            `email` TEXT NOT NULL,
            `expiresAt` TEXT,
            `fcmToken` TEXT,
            `gender` TEXT NOT NULL,
            `isActiveAccount` INTEGER NOT NULL DEFAULT 0,
            `isLoggedIn` INTEGER NOT NULL DEFAULT 0,
            `isExpired` INTEGER NOT NULL DEFAULT 0,
            `isSynced` INTEGER NOT NULL DEFAULT 0,
            `lastActiveTime` TEXT,
            `zipcode` TEXT NOT NULL,
            PRIMARY KEY(`accountId`)
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT INTO `account_new`
            (`accountId`,`firstName`,`lastName`,`dob`,`email`,`expiresAt`,`fcmToken`,`gender`,
             `isActiveAccount`,`isLoggedIn`,`isExpired`,`isSynced`,`lastActiveTime`,`zipcode`)
          SELECT
            `accountId`,`firstName`,`lastName`,`dob`,`email`,`expiresAt`,`fcmToken`,`gender`,
            `isActiveAccount`,`isLoggedIn`,`isExpired`,`isSynced`,`lastActiveTime`,`zipcode`
          FROM `account`
          """.trimIndent()
        )
        db.execSQL("DROP TABLE `account`")
        db.execSQL("ALTER TABLE `account_new` RENAME TO `account`")
        db.execSQL("CREATE UNIQUE INDEX `index_account_email` ON `account` (`email`)")
        db.execSQL("CREATE INDEX `index_account_isActiveAccount` ON `account` (`isActiveAccount`)")
        db.execSQL("CREATE INDEX `index_account_isLoggedIn` ON `account` (`isLoggedIn`)")
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration(false)
            .build()
        Companion.instance = instance
        instance
      }
  }
}
