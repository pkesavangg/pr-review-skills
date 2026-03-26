package com.dmdbrands.gurus.weight.data.storage.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dmdbrands.gurus.weight.data.storage.db.converter.DateConverter
import com.dmdbrands.gurus.weight.data.storage.db.converter.JsonConverter
import com.dmdbrands.gurus.weight.data.storage.db.converter.WeightUnitConverter
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
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
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
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
  ],
  views = [ActiveEntryEntity::class],
  version = 1,
  exportSchema = true,
)
@TypeConverters(DateConverter::class, JsonConverter::class, WeightUnitConverter::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao

  abstract fun deviceDao(): DeviceDao

  abstract fun entryDao(): EntryDao

  abstract fun logDao(): LogDao

  companion object {
    /*The value of a volatile variable will never be cached, and all writes and reads will be done to and from the main memory.
    This helps make sure the value of INSTANCE is always up-to-date and the same for all execution threads.
    It means that changes made by one thread to INSTANCE are visible to all other threads immediately.*/
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
            .fallbackToDestructiveMigration(false)
            .build()
        Companion.instance = instance
        instance
      }
  }
}
