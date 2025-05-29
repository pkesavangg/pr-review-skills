package com.greatergoods.meapp.data.storage.db

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.DateConverter
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter
import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import com.greatergoods.meapp.data.storage.db.dao.*
import com.greatergoods.meapp.data.storage.db.entity.*

/**
 * Main database class for the MeApp application.
 */
@Database(
    entities = [
        AccountEntity::class,
        DeviceEntity::class,
        ScaleEntity::class,
        DeviceMetaDataEntity::class,
        BpmEntity::class,
        R4ScalePreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class, JsonConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun deviceDao(): DeviceDao
    abstract fun scaleDao(): ScaleDao
    abstract fun deviceMetaDataDao(): DeviceMetaDataDao
    abstract fun bpmDao(): BpmDao
    abstract fun r4ScalePreferenceDao(): R4ScalePreferenceDao

    companion object {
        /*The value of a volatile variable will never be cached, and all writes and reads will be done to and from the main memory.
        This helps make sure the value of INSTANCE is always up-to-date and the same for all execution threads.
        It means that changes made by one thread to INSTANCE are visible to all other threads immediately.*/
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "MeApp"
                )
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
