package com.greatergoods.meapp.data.storage.db

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.Database
import androidx.room.Room
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.DeviceEntity

/**
 * Main database class for the MeApp application.
 */
@Database(
    entities = [],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

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
