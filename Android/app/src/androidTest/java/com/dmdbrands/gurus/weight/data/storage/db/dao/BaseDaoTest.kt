package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import org.junit.After
import org.junit.Before

abstract class BaseDaoTest {

    protected lateinit var db: AppDatabase
    protected lateinit var accountDao: AccountDao
    protected lateinit var deviceDao: DeviceDao
    protected lateinit var entryDao: EntryDao
    protected lateinit var logDao: LogDao

    @Before
    open fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        accountDao = db.accountDao()
        deviceDao = db.deviceDao()
        entryDao = db.entryDao()
        logDao = db.logDao()
    }

    @After
    open fun tearDown() {
        db.close()
    }
}
