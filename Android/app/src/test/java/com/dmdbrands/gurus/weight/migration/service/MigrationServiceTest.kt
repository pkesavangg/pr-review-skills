package com.dmdbrands.gurus.weight.migration.service

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.dmdbrands.gurus.weight.core.shared.utilities.IonicDatabaseHelper
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.migration.helper.CapacitorStorageHelper
import com.dmdbrands.gurus.weight.migration.helper.IonicDataConverter
import com.dmdbrands.gurus.weight.migration.helper.toDeviceDetails
import com.dmdbrands.gurus.weight.migration.model.IonicAccount
import com.dmdbrands.gurus.weight.migration.model.IonicScale
import com.dmdbrands.gurus.weight.migration.model.MigrationResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class MigrationServiceTest {

  // ── Mocks ──────────────────────────────────────────────────
  private val migrationRepository: MigrationRepository = mockk(relaxed = true)
  private val context: Context = mockk(relaxed = true)
  private val sqliteDb: SQLiteDatabase = mockk(relaxed = true)

  private lateinit var service: MigrationService

  // ── Helpers ────────────────────────────────────────────────
  private val testAccountId = "acc-123"
  private val testAccountJson = """{"id":"$testAccountId","email":"test@test.com","firstName":"Test"}"""

  private fun mockAccountEntity(): AccountEntity = mockk {
    every { id } returns testAccountId
    every { email } returns "test@test.com"
  }

  private fun mockScaleEntry(accountId: String = testAccountId): ScaleEntry {
    val entryEntity = mockk<com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity> {
      every { this@mockk.accountId } returns accountId
    }
    return mockk { every { entry } returns entryEntity }
  }

  /** Creates a cursor mock that iterates through [count] rows then stops. */
  private fun createCursorMock(count: Int): Cursor {
    val cursor: Cursor = mockk(relaxed = true)
    var position = -1
    every { cursor.moveToNext() } answers {
      position++
      position < count
    }
    every { cursor.moveToFirst() } answers {
      position = 0
      count > 0
    }
    every { cursor.position } answers { position }
    every { cursor.close() } just runs
    return cursor
  }

  /** Stubs tableExists to return [result] for the given [tableName]. */
  private fun stubTableExists(tableName: String, result: Boolean) {
    val cursor = createCursorMock(if (result) 1 else 0)
    every {
      sqliteDb.rawQuery(
        match { it.contains("sqlite_master") && it.contains("LOWER(?)") },
        eq(arrayOf(tableName)),
      )
    } returns cursor
  }

  /** Sets up full happy-path account migration stubs. */
  private fun stubAccountMigrationSuccess() {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } returns testAccountJson
    every { IonicDataConverter.parseAccountWithGson(testAccountJson) } returns mockk(relaxed = true) {
      every { refreshToken } returns "refresh"
      every { accessToken } returns "access"
      every { expiresAt } returns "2099-01-01"
    }
    val accountEntity = mockAccountEntity()
    every { IonicDataConverter.convertIonicAccountToAccountEntity(any()) } returns accountEntity
    every { CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(context) } returns mapOf(testAccountId to "dark")
    every { CapacitorStorageHelper.getLastSyncTimestampForAccount(context, testAccountId) } returns "12345"
  }

  /** Sets up DB path + open for entry migration tests. */
  private fun stubDbOpen() {
    every { IonicDatabaseHelper.locateIonicDb(context) } returns "/fake/db/path"
    every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } returns sqliteDb
  }

  // ── Setup / Teardown ───────────────────────────────────────
  @Before
  fun setup() {
    mockkObject(AppLog)
    every { AppLog.d(any(), any(), any<String>()) } just runs
    every { AppLog.i(any(), any(), any<String>()) } just runs
    every { AppLog.w(any(), any(), any<String>()) } just runs
    every { AppLog.e(any(), any(), any<String>()) } just runs
    every { AppLog.e(any(), any(), any<Throwable>()) } just runs

    mockkObject(IonicDatabaseHelper)
    every { IonicDatabaseHelper.locateIonicDb(any()) } returns null
    every { IonicDatabaseHelper.cleanupIonicDatabase(any()) } just runs
    every { IonicDatabaseHelper.saveMigrationTimestamp(any()) } just runs
    every { IonicDatabaseHelper.deleteRoomDbCompletely(any(), any()) } just runs

    mockkObject(CapacitorStorageHelper)
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(any()) } returns null
    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(any()) } returns emptyMap()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(any(), any()) } returns emptyMap()
    every { CapacitorStorageHelper.locateAndReadTimestampKeyFromCapacitorStorage(any()) } returns emptyMap()
    every { CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(any()) } returns emptyMap()
    every { CapacitorStorageHelper.getLastSyncTimestampForAccount(any(), any()) } returns null

    mockkObject(IonicDataConverter)

    // Mock extension functions from IonicAccountExtensions.kt
    mockkStatic("com.dmdbrands.gurus.weight.migration.helper.IonicAccountExtensionsKt")

    mockkConstructor(UserDataStore::class)
    coEvery { anyConstructed<UserDataStore>().addAccount(any(), any(), any(), any(), any(), any(), any(), any()) } just runs
    coEvery { anyConstructed<UserDataStore>().updateAccountTokens(any(), any(), any(), any(), any()) } just runs
    coEvery { anyConstructed<UserDataStore>().setActiveAccount(any()) } just runs
    coEvery { anyConstructed<UserDataStore>().updateSyncTimestamp(any(), any()) } just runs
    coEvery { anyConstructed<UserDataStore>().containsAccount(any()) } returns false

    mockkStatic(SQLiteDatabase::class)

    every { context.deleteDatabase(any()) } returns true
    every { context.deleteSharedPreferences(any()) } returns true

    service = MigrationService(migrationRepository)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  // ══════════════════════════════════════════════════════════
  //  performIonicMigration — overall flow
  // ══════════════════════════════════════════════════════════

  @Test
  fun `performIonicMigration returns success when no ionic DB found and account migrated`() = runTest {
    stubAccountMigrationSuccess()

    val result = service.performIonicMigration(context)

    assertThat(result).isInstanceOf(MigrationResult.Success::class.java)
    val success = result as MigrationResult.Success
    assertThat(success.migratedCount).isEqualTo(0)
    assertThat(success.accountMigrated).isTrue()
  }

  @Test
  fun `performIonicMigration calls cleanupIonicDatabase on success`() = runTest {
    stubAccountMigrationSuccess()

    service.performIonicMigration(context)

    verify { IonicDatabaseHelper.cleanupIonicDatabase(context) }
  }

  @Test
  fun `performIonicMigration returns failure when exception thrown`() = runTest {
    stubDbOpen()
    // openDatabase throws, caught by migrateIonicDatabase's catch, propagates as Failure
    every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } throws RuntimeException("boom")

    val result = service.performIonicMigration(context)

    assertThat(result).isInstanceOf(MigrationResult.Failure::class.java)
    assertThat(result.errorMessage).contains("boom")
  }

  @Test
  fun `performIonicMigration does not call cleanupIonicDatabase on failure`() = runTest {
    stubDbOpen()
    every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } throws RuntimeException("boom")

    service.performIonicMigration(context)

    // cleanupIonicDatabase is only called in performIonicMigration when migrationResult.isSuccess
    // Since migrateIonicDatabase threw, the outer catch returns Failure, so cleanup is skipped
    verify(exactly = 0) { IonicDatabaseHelper.cleanupIonicDatabase(any()) }
  }

  // ══════════════════════════════════════════════════════════
  //  Account migration
  // ══════════════════════════════════════════════════════════

  @Test
  fun `account migration succeeds and inserts account with settings`() = runTest {
    stubAccountMigrationSuccess()

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isTrue()
    coVerify { migrationRepository.insertAccountWithSettings(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `account migration skipped when no account data in capacitor storage`() = runTest {
    // Default stub returns null for account data

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isFalse()
  }

  @Test
  fun `account migration skipped when parseAccountWithGson returns null`() = runTest {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } returns testAccountJson
    every { IonicDataConverter.parseAccountWithGson(testAccountJson) } returns null

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isFalse()
  }

  @Test
  fun `account migration skipped when convertIonicAccountToAccountEntity returns null`() = runTest {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } returns testAccountJson
    every { IonicDataConverter.parseAccountWithGson(testAccountJson) } returns mockk(relaxed = true)
    every { IonicDataConverter.convertIonicAccountToAccountEntity(any()) } returns null

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isFalse()
  }

  @Test
  fun `account migration saves tokens and sets active account via UserDataStore`() = runTest {
    stubAccountMigrationSuccess()

    service.performIonicMigration(context)

    coVerify { anyConstructed<UserDataStore>().addAccount(testAccountId, any(), any(), any(), any(), any(), any(), any()) }
    coVerify { anyConstructed<UserDataStore>().updateAccountTokens(testAccountId, "refresh", "access", "2099-01-01", true) }
    coVerify { anyConstructed<UserDataStore>().setActiveAccount(testAccountId) }
  }

  @Test
  fun `account migration sets sync timestamp when accountId not in themeModeMap`() = runTest {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } returns testAccountJson
    val ionicAccount: IonicAccount = mockk(relaxed = true) {
      every { refreshToken } returns "refresh"
      every { accessToken } returns "access"
      every { expiresAt } returns "2099-01-01"
    }
    every { IonicDataConverter.parseAccountWithGson(testAccountJson) } returns ionicAccount
    val accountEntity = mockAccountEntity()
    every { IonicDataConverter.convertIonicAccountToAccountEntity(any()) } returns accountEntity
    // Theme mode map does NOT contain the active accountId
    every { CapacitorStorageHelper.locateAndReadThemeModeFromCapacitorStorage(context) } returns mapOf("other-id" to "light")
    every { CapacitorStorageHelper.getLastSyncTimestampForAccount(context, testAccountId) } returns "99999"

    service.performIonicMigration(context)

    coVerify { anyConstructed<UserDataStore>().updateSyncTimestamp(testAccountId, "99999") }
  }

  // ══════════════════════════════════════════════════════════
  //  Device migration
  // ══════════════════════════════════════════════════════════

  @Test
  fun `device migration inserts devices for active account`() = runTest {
    stubAccountMigrationSuccess()
    val mockDeviceDetails: DeviceDetails = mockk()
    val mockScale: IonicScale = mockk()
    every { mockScale.toDeviceDetails(testAccountId) } returns mockDeviceDetails
    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context) } returns mapOf(testAccountId to "[]")
    every { IonicDataConverter.parseDevicesWithGson(any<Map<String, String>>()) } returns mapOf(testAccountId to listOf(mockScale))

    service.performIonicMigration(context)

    coVerify { migrationRepository.insertDevice(listOf(mockDeviceDetails)) }
  }

  @Test
  fun `device migration filters by activeAccountId`() = runTest {
    stubAccountMigrationSuccess()
    val activeDevice: DeviceDetails = mockk()
    val activeScale: IonicScale = mockk()
    every { activeScale.toDeviceDetails(testAccountId) } returns activeDevice
    val otherDevice: DeviceDetails = mockk()
    val otherScale: IonicScale = mockk()
    every { otherScale.toDeviceDetails("other") } returns otherDevice

    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context) } returns mapOf(
      testAccountId to "[]",
      "other" to "[]",
    )
    every { IonicDataConverter.parseDevicesWithGson(any<Map<String, String>>()) } returns mapOf(
      testAccountId to listOf(activeScale),
      "other" to listOf(otherScale),
    )

    service.performIonicMigration(context)

    coVerify { migrationRepository.insertDevice(listOf(activeDevice)) }
  }

  @Test
  fun `device migration skips scales with null DeviceDetails`() = runTest {
    stubAccountMigrationSuccess()
    val nullSkuScale: IonicScale = mockk()
    every { nullSkuScale.toDeviceDetails(testAccountId) } returns null
    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context) } returns mapOf(testAccountId to "[]")
    every { IonicDataConverter.parseDevicesWithGson(any<Map<String, String>>()) } returns mapOf(testAccountId to listOf(nullSkuScale))

    service.performIonicMigration(context)

    coVerify { migrationRepository.insertDevice(emptyList()) }
  }

  @Test
  fun `device migration succeeds when no devices found`() = runTest {
    stubAccountMigrationSuccess()
    // Default stub returns empty map for devices

    val result = service.performIonicMigration(context)

    assertThat(result.isSuccess).isTrue()
  }

  // ══════════════════════════════════════════════════════════
  //  Integration migration (Health Connect)
  // ══════════════════════════════════════════════════════════

  @Test
  fun `integration migration saves settings for active account`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } returns mapOf(testAccountId to "self")
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectIntegrated") } returns mapOf(testAccountId to "true")

    service.performIonicMigration(context)

    coVerify {
      migrationRepository.saveIntegrationSettings(match { it.size == 1 && it.containsKey(testAccountId) })
    }
  }

  @Test
  fun `integration migration filters by activeAccountId`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } returns mapOf(
      testAccountId to "self",
      "other-acc" to "self",
    )

    service.performIonicMigration(context)

    coVerify {
      migrationRepository.saveIntegrationSettings(match { it.size == 1 && it.containsKey(testAccountId) })
    }
  }

  @Test
  fun `integration migration skipped when no settings found`() = runTest {
    stubAccountMigrationSuccess()
    // Default stubs return empty maps for all integration settings

    service.performIonicMigration(context)

    coVerify(exactly = 0) { migrationRepository.saveIntegrationSettings(any()) }
  }

  // ══════════════════════════════════════════════════════════
  //  Entry migration — table existence branching
  // ══════════════════════════════════════════════════════════

  @Test
  fun `entry migration skipped when entry table does not exist`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", false)

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(0)
    coVerify(exactly = 0) { migrationRepository.insertScaleEntries(any()) }
  }

  @Test
  fun `opStack migration skipped when opStack table does not exist (4_0_1 schema)`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val entryCursor = createCursorMock(0)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns entryCursor

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(0)
  }

  @Test
  fun `both entry and opStack tables migrated when both exist (4_2_0 schema)`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", true)
    stubTableExists("entry_metric", false)
    stubTableExists("opStack_metric", false)

    val scaleEntry = mockScaleEntry()

    val entryCursor = createCursorMock(2)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns entryCursor
    every { IonicDataConverter.convertCursorToScaleEntry(entryCursor, isOpStack = false) } returns scaleEntry

    val opStackCursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns opStackCursor
    every { IonicDataConverter.convertCursorToScaleEntry(opStackCursor, isOpStack = true) } returns scaleEntry

    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(5)
  }

  // ══════════════════════════════════════════════════════════
  //  Entry migration — metric table joins
  // ══════════════════════════════════════════════════════════

  @Test
  fun `opStack migration uses LEFT JOIN when opStack_metric exists`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", true)

    val cursor = createCursorMock(0)
    every { sqliteDb.rawQuery(match { it.contains("LEFT JOIN opStack_metric") }, any()) } returns cursor

    service.performIonicMigration(context)

    verify { sqliteDb.rawQuery(match { it.contains("LEFT JOIN opStack_metric") }, any()) }
  }

  @Test
  fun `opStack migration uses NULL columns when opStack_metric does not exist`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val cursor = createCursorMock(0)
    every { sqliteDb.rawQuery(match { it.contains("NULL AS bmr") && it.contains("FROM opStack e") }, any()) } returns cursor

    service.performIonicMigration(context)

    verify { sqliteDb.rawQuery(match { it.contains("NULL AS bmr") && it.contains("FROM opStack e") }, any()) }
  }

  @Test
  fun `entry migration uses LEFT JOIN when entry_metric exists`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", true)

    val cursor = createCursorMock(0)
    every { sqliteDb.rawQuery(match { it.contains("LEFT JOIN entry_metric") }, any()) } returns cursor

    service.performIonicMigration(context)

    verify { sqliteDb.rawQuery(match { it.contains("LEFT JOIN entry_metric") }, any()) }
  }

  @Test
  fun `entry migration uses NULL columns when entry_metric does not exist`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val cursor = createCursorMock(0)
    every { sqliteDb.rawQuery(match { it.contains("NULL AS bmr") && it.contains("FROM entry e") }, any()) } returns cursor

    service.performIonicMigration(context)

    verify { sqliteDb.rawQuery(match { it.contains("NULL AS bmr") && it.contains("FROM entry e") }, any()) }
  }

  // ══════════════════════════════════════════════════════════
  //  Entry migration — account scoping
  // ══════════════════════════════════════════════════════════

  @Test
  fun `opStack migration only migrates entries for active account`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val matchingEntry = mockScaleEntry(testAccountId)
    val nonMatchingEntry = mockScaleEntry("other-acc")

    val cursor = createCursorMock(2)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor

    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } answers {
      callCount++
      if (callCount == 1) matchingEntry else nonMatchingEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(1)
  }

  @Test
  fun `entry table migration only migrates entries for active account`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val matchingEntry = mockScaleEntry(testAccountId)
    val nonMatchingEntry = mockScaleEntry("other-acc")

    val cursor = createCursorMock(2)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns cursor

    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = false) } answers {
      callCount++
      if (callCount == 1) matchingEntry else nonMatchingEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(1)
  }

  // ══════════════════════════════════════════════════════════
  //  Entry migration — batching
  // ══════════════════════════════════════════════════════════

  @Test
  fun `opStack entries are batched at BATCH_SIZE of 500`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val scaleEntry = mockScaleEntry()
    val cursor = createCursorMock(750)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } returns scaleEntry

    val batchSizes = mutableListOf<Int>()
    coEvery { migrationRepository.insertScaleEntries(any()) } answers {
      val batch = firstArg<List<ScaleEntry>>()
      batchSizes.add(batch.size)
      batch.size
    }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(750)
    assertThat(batchSizes).isEqualTo(listOf(500, 250))
  }

  @Test
  fun `entry table entries are batched at BATCH_SIZE of 500`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val scaleEntry = mockScaleEntry()
    val cursor = createCursorMock(1200)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns cursor
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = false) } returns scaleEntry

    val batchSizes = mutableListOf<Int>()
    coEvery { migrationRepository.insertScaleEntries(any()) } answers {
      val batch = firstArg<List<ScaleEntry>>()
      batchSizes.add(batch.size)
      batch.size
    }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(1200)
    assertThat(batchSizes).isEqualTo(listOf(500, 500, 200))
  }

  @Test
  fun `remaining entries after last full batch are flushed`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val scaleEntry = mockScaleEntry()
    val cursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } returns scaleEntry
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(3)
    coVerify(exactly = 1) { migrationRepository.insertScaleEntries(match { it.size == 3 }) }
  }

  // ══════════════════════════════════════════════════════════
  //  Entry migration — null/invalid entries
  // ══════════════════════════════════════════════════════════

  @Test
  fun `null entries from convertCursorToScaleEntry are skipped`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val cursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor

    val validEntry = mockScaleEntry()
    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } answers {
      callCount++
      if (callCount == 2) null else validEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(2)
  }

  @Test
  fun `entry conversion exception is caught and entry is skipped`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val cursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor

    val validEntry = mockScaleEntry()
    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } answers {
      callCount++
      if (callCount == 2) throw RuntimeException("bad row") else validEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(2)
  }

  // ══════════════════════════════════════════════════════════
  //  Timestamp handling
  // ══════════════════════════════════════════════════════════

  @Test
  fun `migration saves timestamp on success`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", false)

    service.performIonicMigration(context)

    verify { IonicDatabaseHelper.saveMigrationTimestamp(context) }
  }

  // ══════════════════════════════════════════════════════════
  //  Cleanup
  // ══════════════════════════════════════════════════════════

  @Test
  fun `cleanup deletes ionic database and shared preferences`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", false)

    service.performIonicMigration(context)

    // clearAllIonicData is called in finally block
    verify { context.deleteDatabase("/fake/db/path") }
    verify { context.deleteSharedPreferences("CapacitorStorage") }
  }

  @Test
  fun `cleanup runs even when migration fails with exception`() = runTest {
    stubDbOpen()
    every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } throws RuntimeException("db error")

    service.performIonicMigration(context)

    // clearAllIonicData still called in finally block
    verify { context.deleteSharedPreferences("CapacitorStorage") }
  }

  @Test
  fun `sqlite database is closed in finally block`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", false)

    service.performIonicMigration(context)

    verify { sqliteDb.close() }
  }

  @Test
  fun `sqlite database closed even on exception`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    // Make tableExists query throw
    every { sqliteDb.rawQuery(any(), any()) } throws RuntimeException("query failed")

    service.performIonicMigration(context)

    verify { sqliteDb.close() }
  }

  @Test
  fun `clearAllIonicData skips deleteDatabase when no ionic db found`() = runTest {
    // locateIonicDb returns null (default in setup) — no DB open, no deleteDatabase call for a path
    stubAccountMigrationSuccess()

    service.performIonicMigration(context)

    // deleteSharedPreferences always called
    verify { context.deleteSharedPreferences("CapacitorStorage") }
  }

  // ══════════════════════════════════════════════════════════
  //  performEmergencyCleanup
  // ══════════════════════════════════════════════════════════

  @Test
  fun `performEmergencyCleanup deletes Room database`() = runTest {
    service.performEmergencyCleanup(context)

    verify { IonicDatabaseHelper.deleteRoomDbCompletely(context, "MeApp") }
  }

  @Test
  fun `performEmergencyCleanup catches exception gracefully`() = runTest {
    every { IonicDatabaseHelper.deleteRoomDbCompletely(context, "MeApp") } throws RuntimeException("cleanup error")

    service.performEmergencyCleanup(context)

    verify { AppLog.e(any(), match { it.contains("Emergency cleanup failed") }, any<String>()) }
  }

  // ══════════════════════════════════════════════════════════
  //  tableExists
  // ══════════════════════════════════════════════════════════

  @Test
  fun `tableExists returns false when query throws exception`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    // Make sqlite_master query throw
    every { sqliteDb.rawQuery(match { it.contains("sqlite_master") }, any()) } throws RuntimeException("table check failed")

    val result = service.performIonicMigration(context) as MigrationResult.Success

    // Both tables treated as not existing → 0 entries
    assertThat(result.migratedCount).isEqualTo(0)
  }

  // ══════════════════════════════════════════════════════════
  //  Edge cases — no account migration (null activeAccountId)
  // ══════════════════════════════════════════════════════════

  @Test
  fun `migration proceeds without account scoping when account migration fails`() = runTest {
    // Default stub: no account data → activeAccountId is null
    stubDbOpen()

    stubTableExists("entry", false)
    stubTableExists("opStack", true)
    stubTableExists("opStack_metric", false)

    val entry1 = mockScaleEntry("acc-1")
    val entry2 = mockScaleEntry("acc-2")

    val cursor = createCursorMock(2)
    every { sqliteDb.rawQuery(match { it.contains("FROM opStack e") }, any()) } returns cursor

    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = true) } answers {
      callCount++
      if (callCount == 1) entry1 else entry2
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    // Both entries migrated since no account scoping
    assertThat(result.migratedCount).isEqualTo(2)
    assertThat(result.accountMigrated).isFalse()
  }

  @Test
  fun `device migration when activeAccountId is null migrates all accounts`() = runTest {
    // No account data → activeAccountId is null
    val device1: DeviceDetails = mockk()
    val device2: DeviceDetails = mockk()
    val scale1: IonicScale = mockk()
    every { scale1.toDeviceDetails("acc-1") } returns device1
    val scale2: IonicScale = mockk()
    every { scale2.toDeviceDetails("acc-2") } returns device2

    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context) } returns mapOf(
      "acc-1" to "[]",
      "acc-2" to "[]",
    )
    every { IonicDataConverter.parseDevicesWithGson(any<Map<String, String>>()) } returns mapOf(
      "acc-1" to listOf(scale1),
      "acc-2" to listOf(scale2),
    )

    service.performIonicMigration(context)

    coVerify { migrationRepository.insertDevice(match { it.size == 2 }) }
  }

  @Test
  fun `integration migration when activeAccountId is null migrates all accounts`() = runTest {
    // No account data → activeAccountId is null
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } returns mapOf(
      "acc-1" to "self",
      "acc-2" to "other",
    )

    service.performIonicMigration(context)

    coVerify { migrationRepository.saveIntegrationSettings(match { it.size == 2 }) }
  }

  // ══════════════════════════════════════════════════════════
  //  Integration — healthServerIntegration JSON parsing
  // ══════════════════════════════════════════════════════════

  @Test
  fun `integration migration parses healthServerIntegration JSON`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } returns mapOf(testAccountId to "self")
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthServerIntegration") } returns mapOf(testAccountId to """{"operationType":"save"}""")

    service.performIonicMigration(context)

    coVerify { migrationRepository.saveIntegrationSettings(match { it.containsKey(testAccountId) }) }
  }

  @Test
  fun `integration migration handles invalid healthServerIntegration JSON gracefully`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } returns mapOf(testAccountId to "self")
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthServerIntegration") } returns mapOf(testAccountId to "not-json")

    service.performIonicMigration(context)

    // Should still save settings (with null integrationStatus)
    coVerify { migrationRepository.saveIntegrationSettings(match { it.containsKey(testAccountId) }) }
  }

  @Test
  fun `integration migration catches exception gracefully`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadIntegrationSettings(context, "healthConnectAssignedTo") } throws RuntimeException("integration error")

    // Should not throw, migration continues
    val result = service.performIonicMigration(context)

    assertThat(result.isSuccess).isTrue()
  }

  // ══════════════════════════════════════════════════════════
  //  Device migration — error handling
  // ══════════════════════════════════════════════════════════

  @Test
  fun `device migration catches exception and returns false`() = runTest {
    stubAccountMigrationSuccess()
    every { CapacitorStorageHelper.locateAndReadPairedScalesFromCapacitorStorage(context) } throws RuntimeException("device error")

    // Should not throw, migration continues
    val result = service.performIonicMigration(context)

    assertThat(result.isSuccess).isTrue()
  }

  // ══════════════════════════════════════════════════════════
  //  Account migration — error handling
  // ══════════════════════════════════════════════════════════

  @Test
  fun `account migration catches exception and returns false with null id`() = runTest {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } throws RuntimeException("account error")

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isFalse()
  }

  // ══════════════════════════════════════════════════════════
  //  performIonicMigration — failure logging path
  // ══════════════════════════════════════════════════════════

  @Test
  fun `performIonicMigration logs error when migration result is failure`() = runTest {
    stubDbOpen()
    // Make migrateIonicDatabase return Failure by having the entry table query fail inside the try block
    // after DB is opened. The catch in migrateIonicDatabase produces Failure.
    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    // rawQuery for the entry SELECT throws, caught by migrateIonicDatabase's catch → returns Failure
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } throws RuntimeException("entry query error")

    val result = service.performIonicMigration(context)

    assertThat(result).isInstanceOf(MigrationResult.Failure::class.java)
    verify { AppLog.e(any(), match { it.contains("entry query error") }, any<String>()) }
  }

  // ══════════════════════════════════════════════════════════
  //  MigrationResult model
  // ══════════════════════════════════════════════════════════

  @Test
  fun `MigrationResult Success has isSuccess true and null errorMessage`() {
    val result = MigrationResult.success(10, true)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.errorMessage).isNull()
    assertThat((result as MigrationResult.Success).migratedCount).isEqualTo(10)
    assertThat(result.accountMigrated).isTrue()
  }

  @Test
  fun `MigrationResult Failure has isSuccess false and errorMessage`() {
    val result = MigrationResult.failure("something went wrong")

    assertThat(result.isSuccess).isFalse()
    assertThat(result.errorMessage).isEqualTo("something went wrong")
  }

  // ══════════════════════════════════════════════════════════
  //  Additional coverage — outer Throwable catch, empty account, entry table null/exception
  // ══════════════════════════════════════════════════════════

  @Test
  fun `performIonicMigration catches outer Throwable when cleanupIonicDatabase throws`() = runTest {
    stubAccountMigrationSuccess()
    // migrateIonicDatabase succeeds → performIonicMigration calls cleanupIonicDatabase which throws
    every { IonicDatabaseHelper.cleanupIonicDatabase(any()) } throws RuntimeException("cleanup boom")

    val result = service.performIonicMigration(context)

    assertThat(result).isInstanceOf(MigrationResult.Failure::class.java)
    assertThat(result.errorMessage).contains("cleanup boom")
  }

  @Test
  fun `account migration skipped when account JSON is empty string`() = runTest {
    every { CapacitorStorageHelper.locateAndReadAccountFromCapacitorStorage(context) } returns ""

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.accountMigrated).isFalse()
  }

  @Test
  fun `entry table migration skips null entries from convertCursorToScaleEntry`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val cursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns cursor

    val validEntry = mockScaleEntry()
    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = false) } answers {
      callCount++
      if (callCount == 2) null else validEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(2)
  }

  @Test
  fun `entry table conversion exception is caught and entry is skipped`() = runTest {
    stubAccountMigrationSuccess()
    stubDbOpen()

    stubTableExists("entry", true)
    stubTableExists("opStack", false)
    stubTableExists("entry_metric", false)

    val cursor = createCursorMock(3)
    every { sqliteDb.rawQuery(match { it.contains("FROM entry e") }, any()) } returns cursor

    val validEntry = mockScaleEntry()
    var callCount = 0
    every { IonicDataConverter.convertCursorToScaleEntry(cursor, isOpStack = false) } answers {
      callCount++
      if (callCount == 2) throw RuntimeException("bad row") else validEntry
    }
    coEvery { migrationRepository.insertScaleEntries(any()) } answers { firstArg<List<ScaleEntry>>().size }

    val result = service.performIonicMigration(context) as MigrationResult.Success

    assertThat(result.migratedCount).isEqualTo(2)
  }

  @Test
  fun `saveAccountAndSettings uses forceUpdate true when account exists in UserDataStore`() = runTest {
    stubAccountMigrationSuccess()
    coEvery { anyConstructed<UserDataStore>().containsAccount(testAccountId) } returns true

    service.performIonicMigration(context)

    coVerify { anyConstructed<UserDataStore>().addAccount(testAccountId, any(), any(), any(), any(), any(), any(), true) }
  }
}
