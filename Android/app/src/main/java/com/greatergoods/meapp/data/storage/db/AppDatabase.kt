package com.greatergoods.meapp.data.storage.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.DateConverter
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.dao.LogDao
import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.DeviceEntity
import com.greatergoods.meapp.data.storage.db.entity.DeviceMetaDataEntity
import com.greatergoods.meapp.data.storage.db.entity.BpmEntity
import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.R4ScalePreferenceEntity
import android.content.Context
import com.greatergoods.meapp.data.storage.db.entity.LogEntity

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
        LogEntity::class
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DateConverter::class, JsonConverter::class)
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
                        ).fallbackToDestructiveMigration(false)
                        .build()
                Companion.instance = instance
                instance
            }
    }
}
