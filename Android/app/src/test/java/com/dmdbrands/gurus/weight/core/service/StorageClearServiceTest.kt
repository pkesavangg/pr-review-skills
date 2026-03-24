package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.BaseProtoDataStore
import com.dmdbrands.gurus.weight.data.storage.db.AppDatabase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageClearServiceTest {

    // --- Mocks ---
    private val context: Context = mockk(relaxed = true)
    private val appDatabase: AppDatabase = mockk(relaxed = true)
    private val navigationService: IAppNavigationService = mockk(relaxed = true)
    private val dataStore1: BaseProtoDataStore<*> = mockk(relaxed = true)
    private val dataStore2: BaseProtoDataStore<*> = mockk(relaxed = true)
    private val dataStore3: BaseProtoDataStore<*> = mockk(relaxed = true)

    private lateinit var service: StorageClearService

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit

        service = createServiceWith(setOf(dataStore1, dataStore2, dataStore3))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Helper to construct a StorageClearService with a custom DataStore set. */
    private fun createServiceWith(stores: Set<BaseProtoDataStore<*>>) =
        StorageClearService(
            context = context,
            appDatabase = appDatabase,
            dataStores = stores,
            navigationService = navigationService,
        )

    // -------------------------------------------------------------------------
    // clearAllStorage — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage clears database tables`() = runTest {
        service.clearAllStorage()

        verify(exactly = 1) { appDatabase.clearAllTables() }
    }

    @Test
    fun `clearAllStorage clears all DataStores`() = runTest {
        service.clearAllStorage()

        coVerify(exactly = 1) { dataStore1.clearData() }
        coVerify(exactly = 1) { dataStore2.clearData() }
        coVerify(exactly = 1) { dataStore3.clearData() }
    }

    @Test
    fun `clearAllStorage clears database before DataStores`() = runTest {
        val callOrder = mutableListOf<String>()
        every { appDatabase.clearAllTables() } answers { callOrder.add("db") }
        coEvery { dataStore1.clearData() } answers { callOrder.add("ds1") }
        coEvery { dataStore2.clearData() } answers { callOrder.add("ds2") }
        coEvery { dataStore3.clearData() } answers { callOrder.add("ds3") }

        service.clearAllStorage()

        assertThat(callOrder.first()).isEqualTo("db")
    }

    // -------------------------------------------------------------------------
    // clearAllStorage — empty DataStore set
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage with empty DataStore set still clears database`() = runTest {
        val serviceNoStores = createServiceWith(emptySet())

        serviceNoStores.clearAllStorage()

        verify(exactly = 1) { appDatabase.clearAllTables() }
    }

    @Test
    fun `clearAllStorage with empty DataStore set logs zero instances`() = runTest {
        val serviceNoStores = createServiceWith(emptySet())

        serviceNoStores.clearAllStorage()

        verify { AppLog.i("StorageClearService", match { it.contains("0 instances") }) }
    }

    // -------------------------------------------------------------------------
    // clearAllStorage — database failure
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage throws when database clearAllTables fails`() = runTest {
        every { appDatabase.clearAllTables() } throws RuntimeException("DB locked")

        var thrownException: Exception? = null
        try {
            service.clearAllStorage()
        } catch (e: Exception) {
            thrownException = e
        }

        val exception = requireNotNull(thrownException)
        assertThat(exception.message).isEqualTo("DB locked")
    }

    @Test
    fun `clearAllStorage does not clear DataStores when database fails`() = runTest {
        every { appDatabase.clearAllTables() } throws RuntimeException("DB locked")

        try {
            service.clearAllStorage()
        } catch (_: Exception) {
            // expected
        }

        coVerify(exactly = 0) { dataStore1.clearData() }
        coVerify(exactly = 0) { dataStore2.clearData() }
        coVerify(exactly = 0) { dataStore3.clearData() }
    }

    @Test
    fun `clearAllStorage logs error when database fails`() = runTest {
        every { appDatabase.clearAllTables() } throws RuntimeException("DB locked")

        try {
            service.clearAllStorage()
        } catch (_: Exception) {
            // expected
        }

        verify { AppLog.e("StorageClearService", match { it.contains("Failed to clear local data") }, any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // clearAllStorage — individual DataStore failure (continues with others)
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage continues clearing other DataStores when one fails`() = runTest {
        coEvery { dataStore1.clearData() } throws RuntimeException("DS1 corrupt")

        service.clearAllStorage()

        coVerify(exactly = 1) { dataStore1.clearData() }
        coVerify(exactly = 1) { dataStore2.clearData() }
        coVerify(exactly = 1) { dataStore3.clearData() }
    }

    @Test
    fun `clearAllStorage logs error for individual DataStore failure`() = runTest {
        coEvery { dataStore2.clearData() } throws RuntimeException("DS2 error")

        service.clearAllStorage()

        verify { AppLog.e("StorageClearService", match { it.contains("Failed to clear DataStore") }, any<Throwable>()) }
    }

    @Test
    fun `clearAllStorage continues when all DataStores fail individually`() = runTest {
        coEvery { dataStore1.clearData() } throws RuntimeException("DS1")
        coEvery { dataStore2.clearData() } throws RuntimeException("DS2")
        coEvery { dataStore3.clearData() } throws RuntimeException("DS3")

        // Should NOT throw — each failure is caught individually
        service.clearAllStorage()

        coVerify(exactly = 1) { dataStore1.clearData() }
        coVerify(exactly = 1) { dataStore2.clearData() }
        coVerify(exactly = 1) { dataStore3.clearData() }
    }

    @Test
    fun `clearAllStorage logs success despite individual DataStore failures because inner catch swallows them`() = runTest {
        coEvery { dataStore1.clearData() } throws RuntimeException("DS1")

        service.clearAllStorage()

        // Note: the service logs "All DataStores cleared successfully" even when individual clears
        // threw exceptions, because the inner catch swallows them. This documents current behavior.
        verify { AppLog.i("StorageClearService", match { it.contains("All DataStores cleared successfully") }) }
    }

    // -------------------------------------------------------------------------
    // clearAllDataStores — outer catch block
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage throws when clearAllDataStores outer try fails`() = runTest {
        every {
            AppLog.i("StorageClearService", match { it.contains("Clearing all DataStores") })
        } throws RuntimeException("Logging failure")

        var thrownException: Exception? = null
        try {
            service.clearAllStorage()
        } catch (e: Exception) {
            thrownException = e
        }

        val exception = requireNotNull(thrownException)
        assertThat(exception.message).isEqualTo("Logging failure")
    }

    @Test
    fun `clearAllStorage logs both DataStore and local data errors when clearAllDataStores fails`() = runTest {
        every {
            AppLog.i("StorageClearService", match { it.contains("Clearing all DataStores") })
        } throws RuntimeException("Logging failure")

        try {
            service.clearAllStorage()
        } catch (_: Exception) {
            // expected
        }

        // clearAllDataStores outer catch logs this
        verify { AppLog.e("StorageClearService", match { it.contains("Failed to clear DataStores") }, any<Throwable>()) }
        // clearAllLocalData catch re-catches and logs this
        verify { AppLog.e("StorageClearService", match { it.contains("Failed to clear local data") }, any<Throwable>()) }
    }

    @Test
    fun `clearAllStorage does not clear any DataStore when outer clearAllDataStores fails early`() = runTest {
        every {
            AppLog.i("StorageClearService", match { it.contains("Clearing all DataStores") })
        } throws RuntimeException("Logging failure")

        try {
            service.clearAllStorage()
        } catch (_: Exception) {
            // expected
        }

        coVerify(exactly = 0) { dataStore1.clearData() }
        coVerify(exactly = 0) { dataStore2.clearData() }
        coVerify(exactly = 0) { dataStore3.clearData() }
    }

    // -------------------------------------------------------------------------
    // clearAllStorage — constructor with Context-dependent path
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllStorage invoked on service constructed with different Context still clears all datastores`() = runTest {
        val anotherContext: Context = mockk(relaxed = true)
        val serviceWithDifferentContext = StorageClearService(
            context = anotherContext,
            appDatabase = appDatabase,
            dataStores = setOf(dataStore1, dataStore2),
            navigationService = navigationService,
        )

        serviceWithDifferentContext.clearAllStorage()

        verify(exactly = 1) { appDatabase.clearAllTables() }
        coVerify(exactly = 1) { dataStore1.clearData() }
        coVerify(exactly = 1) { dataStore2.clearData() }
    }

    @Test
    fun `clearAllStorage with single DataStore clears that store`() = runTest {
        val singleStoreService = createServiceWith(setOf(dataStore1))

        singleStoreService.clearAllStorage()

        verify(exactly = 1) { appDatabase.clearAllTables() }
        coVerify(exactly = 1) { dataStore1.clearData() }
        coVerify(exactly = 0) { dataStore2.clearData() }
        coVerify(exactly = 0) { dataStore3.clearData() }
    }

    @Test
    fun `clearAllStorage logs correct DataStore count`() = runTest {
        service.clearAllStorage()

        verify { AppLog.i("StorageClearService", match { it.contains("3 instances") }) }
    }

}
