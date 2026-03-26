# DAO Testing Pattern (Instrumented)

## Overview

DAO tests are **instrumented tests** that run on a real Android device or emulator. They live in `androidTest/`, not `test/`, because Room requires an Android `Context` to build the database.

**No MockK, no Turbine** — DAO tests use real Room in-memory databases. Turbine is unavailable in `androidTest` due to a version conflict with `compose-ui-test`. Use `flow.first()` instead.

## Test infrastructure

### BaseDaoTest

All DAO tests extend `BaseDaoTest`, which provides:
- An in-memory `AppDatabase` via `Room.inMemoryDatabaseBuilder()`
- `allowMainThreadQueries()` — standard for Room instrumented tests
- All 4 DAO accessors (`accountDao`, `deviceDao`, `entryDao`, `logDao`)
- Fresh database per test (`@Before` creates, `@After` closes)

```kotlin
@RunWith(AndroidJUnit4::class)
class FooDaoTest : BaseDaoTest() {
    // accountDao, deviceDao, entryDao, logDao are available from BaseDaoTest
    // No @Before/@After needed unless you have extra setup
}
```

**Location**: `app/src/androidTest/java/com/dmdbrands/gurus/weight/data/storage/db/dao/BaseDaoTest.kt`

### DaoTestFixtures

Shared factory object for creating entities with sensible defaults. **Always use these** instead of manual entity construction:

```kotlin
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.device
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.entryEntity
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.logEntity
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.scaleEntry
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.bpmEntry

// Override only the fields your test cares about:
val acc = account(id = "acc-1", isActiveAccount = true)
val log = logEntity(id = "log-1", type = "e")

// insertFullAccount() — inserts account + all 7 settings in one call
accountDao.insertFullAccount(account(id = "acc-1"))
```

**Available fixture builders** (20+ functions):
- **Account**: `account()`, `weightCompSettings()`, `goalSettings()`, `streaksSettings()`, `weightlessSettings()`, `notificationSettings()`, `dashboardSettings()`, `integrationsSettings()`
- **Device**: `device()`, `bodyScale()`, `bpmDevice()`, `deviceMeta()`, `r4Preference()`, `deviceDetails()`
- **Entry**: `entryEntity()`, `bodyScaleEntry()`, `bodyScaleMetric()`, `bpmEntryEntity()`, `scaleEntry()`, `bpmEntry()`
- **Log**: `logEntity()`

**Location**: `app/src/androidTest/java/com/dmdbrands/gurus/weight/data/storage/db/dao/DaoTestFixtures.kt`

## Import ordering convention

**CRITICAL**: Follow this exact import group order to match sibling DAO tests:

```
1. androidx.test.*
2. com.dmdbrands.* (DaoTestFixtures imports)
3. com.google.common.truth.*
4. kotlinx.coroutines.*
5. org.junit.*
```

> **Common mistake**: Placing `androidx.test.ext.junit.runners.AndroidJUnit4` after `org.junit.*` imports. It must come FIRST.

## Complete DAO test file structure

```kotlin
package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
// ... other fixture imports as needed
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class {DaoName}Test : BaseDaoTest() {

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Insert a parent account (with all settings) for FK dependencies.
     * Use unique emails when inserting multiple accounts.
     */
    private suspend fun insertParentAccount(accountId: String = "acc-1") {
        accountDao.insertFullAccount(account(id = accountId, email = "$accountId@test.com"))
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    @Test
    fun insertEntity_storesAndRetrieves() = runTest {
        val entity = account()
        accountDao.insertAccount(entity)

        assertThat(accountDao.getAccountEntity(entity.id)).isEqualTo(entity)
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @Test
    fun queryReturnsExpectedResults() = runTest {
        insertParentAccount()
        // ... insert test data
        // ... query and assert
    }

    // -------------------------------------------------------------------------
    // Flow queries
    // -------------------------------------------------------------------------

    @Test
    fun flowQueryEmitsCorrectValue() = runTest {
        insertParentAccount()

        val result = accountDao.getActiveAccount().first()
        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // CASCADE delete
    // -------------------------------------------------------------------------

    @Test
    fun deleteParent_cascadesToChildren() = runTest {
        insertParentAccount()
        // ... insert child entities
        accountDao.deleteAccountById("acc-1")
        // ... verify children are gone
    }
}
```

## FK dependency order

**CRITICAL**: Always insert parent entities before children. Room enforces FK constraints.

**Insertion order**:
1. `AccountEntity` (no FK dependencies)
2. `DeviceEntity` / `EntryEntity` (FK → AccountEntity.id)
3. Sub-entities: `BodyScaleEntity`, `BpmEntity`, `DeviceMetaDataEntity`, `R4ScalePreferenceEntity` (FK → DeviceEntity.id)
4. Sub-entries: `BodyScaleEntryEntity`, `BodyScaleEntryMetricEntity`, `BpmEntryEntity` (FK → EntryEntity.id)

**Shortcut**: Use `insertFullAccount()` to set up the FK parent + all 7 settings in one call.

```kotlin
// ❌ WRONG — FK violation, no parent account
entryDao.insert(scaleEntry())  // crashes: FOREIGN KEY constraint failed

// ✅ CORRECT — insert parent first
accountDao.insertFullAccount(account(id = "acc-1"))
entryDao.insert(scaleEntry(accountId = "acc-1"))
```

## Test patterns

### Pattern A: CRUD operations

```kotlin
// INSERT + retrieve
@Test
fun insertAccount_storesAndRetrievesEntity() = runTest {
    val acc = account()
    accountDao.insertAccount(acc)
    assertThat(accountDao.getAccountEntity(acc.id)).isEqualTo(acc)
}

// UPDATE
@Test
fun updateAccount_modifiesExistingEntity() = runTest {
    val acc = account(firstName = "John")
    accountDao.insertAccount(acc)
    accountDao.updateAccount(acc.copy(firstName = "Jane"))
    assertThat(accountDao.getAccountEntity(acc.id)?.firstName).isEqualTo("Jane")
}

// DELETE
@Test
fun deleteAccount_removesEntity() = runTest {
    accountDao.insertAccount(account())
    accountDao.deleteAccountById("acc-1")
    assertThat(accountDao.getAccountEntity("acc-1")).isNull()
}
```

### Pattern B: Flow testing with `flow.first()`

Room Flows are **infinite** — they never complete. Use `flow.first()` to collect the first emission:

```kotlin
// Basic flow query — store result in val when asserting multiple properties
@Test
fun getActiveAccount_emitsActiveAccount() = runTest {
    accountDao.insertFullAccount(account(isActiveAccount = true))

    val result = accountDao.getActiveAccount().first()
    assertThat(result).isNotNull()
    assertThat(result?.account?.isActiveAccount).isTrue()
}

// Reactive behavior — verify new emissions after mutations
@Test
fun getLogCount_updatesAfterInsert() = runTest {
    insertParentAccount()

    assertThat(logDao.getLogCount().first()).isEqualTo(0)
    logDao.insertLog(logEntity())
    assertThat(logDao.getLogCount().first()).isEqualTo(1)
}

// Null/empty result
@Test
fun getActiveAccount_emitsNullWhenNoActiveAccount() = runTest {
    val result = accountDao.getActiveAccount().first()
    assertThat(result).isNull()
}
```

> **Avoid redundant flow collection**: When you need the result for multiple assertions, store it in a `val`. Never call `.first()` twice on the same flow for the same logical check:
> ```kotlin
> // BAD — collects the flow twice
> assertThat(accountDao.getAllLoggedInAccounts().first()).hasSize(1)
> assertThat(accountDao.getAllLoggedInAccounts().first()[0].account.id).isEqualTo("acc-2")
>
> // GOOD — collect once, assert multiple times
> val result = accountDao.getAllLoggedInAccounts().first()
> assertThat(result).hasSize(1)
> assertThat(result[0].account.id).isEqualTo("acc-2")
> ```

### Pattern C: Relation queries (account with settings)

```kotlin
@Test
fun getAccount_returnsAllRelations() = runTest {
    accountDao.insertFullAccount()

    val result = accountDao.getAccount("acc-1").first()
    assertThat(result).isNotNull()
    assertThat(result?.account?.id).isEqualTo("acc-1")
    assertThat(result?.weightCompSettings).isNotNull()
    assertThat(result?.goalSettings).isNotNull()
    assertThat(result?.streaksSettings).isNotNull()
    assertThat(result?.weightlessSettings).isNotNull()
    assertThat(result?.notificationSettings).isNotNull()
    assertThat(result?.dashboardSettings).isNotNull()
    assertThat(result?.integrationsSettings).isNotNull()
}
```

### Pattern D: CASCADE delete behavior

> **CRITICAL**: When testing CASCADE, verify ALL child entities are deleted — not just one. Use the relation query (`getAccount`) which returns all 7 settings at once, rather than checking a single settings table.

```kotlin
// Verify CASCADE deletes ALL settings — use getAccount() which checks all relations
@Test
fun deleteAccountById_cascadesToAllSettings() = runTest {
    accountDao.insertFullAccount()

    accountDao.deleteAccountById("acc-1")

    assertThat(accountDao.getAccountEntity("acc-1")).isNull()
    // getAccount returns null only if the account AND all relations are gone
    assertThat(accountDao.getAccount("acc-1").first()).isNull()
    assertThat(accountDao.getDashboardSettings("acc-1").first()).isNull()
}

// Verify CASCADE to cross-entity types (devices)
@Test
fun deleteAccountById_cascadesToDevices() = runTest {
    accountDao.insertFullAccount()
    deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1"))

    accountDao.deleteAccountById("acc-1")

    val devices = deviceDao.getDevices("acc-1").first()
    assertThat(devices).isEmpty()
}

// Isolation: verify OTHER account's relations survive intact — check ALL 7 settings
@Test
fun deleteAllTables_isolatesToOneAccount() = runTest {
    accountDao.insertFullAccount(account(id = "acc-1", email = "a@b.com"))
    accountDao.insertFullAccount(account(id = "acc-2", email = "c@d.com"))

    accountDao.deleteAllTables("acc-1")

    val result = accountDao.getAccount("acc-2").first()
    assertThat(result).isNotNull()
    assertThat(result?.weightCompSettings).isNotNull()
    assertThat(result?.goalSettings).isNotNull()
    assertThat(result?.streaksSettings).isNotNull()
    assertThat(result?.weightlessSettings).isNotNull()
    assertThat(result?.notificationSettings).isNotNull()
    assertThat(result?.dashboardSettings).isNotNull()
    assertThat(result?.integrationsSettings).isNotNull()
}
```

> **Do NOT partially check relations.** If the test name says "cascades to ALL settings" or "isolates to one account", verify ALL 7 settings — not just 1 or 2. A regression in any single FK CASCADE constraint would go undetected otherwise.

### Pattern E: Conflict strategy testing

```kotlin
// REPLACE strategy — duplicate primary key replaces
@Test
fun insertAccount_replacesOnDuplicateId() = runTest {
    accountDao.insertAccount(account(id = "acc-1", firstName = "John"))
    accountDao.insertAccount(account(id = "acc-1", firstName = "Jane"))

    val result = accountDao.getAccountEntity("acc-1")
    assertThat(result?.firstName).isEqualTo("Jane")
}

// IGNORE strategy — duplicate is silently ignored
@Test
fun insertLog_ignoresDuplicate() = runTest {
    insertParentAccount()
    logDao.insertLog(logEntity(id = "log-1", message = "first"))
    logDao.insertLog(logEntity(id = "log-1", message = "second"))

    val result = logDao.getLog("log-1")
    assertThat(result?.message).isEqualTo("first")  // original preserved
}
```

### Pattern F: `entry_view` soft-delete filtering

The `entry_view` database view filters out soft-deleted entries (where `operationType = 'delete'`). Queries against `entry_view` exclude deleted entries automatically.

```kotlin
@Test
fun entryView_excludesSoftDeletedEntries() = runTest {
    insertParentAccount()
    val id1 = entryDao.insert(scaleEntry(entryTimestamp = "2025-06-15T12:00:00.000Z"))
    entryDao.insert(scaleEntry(entryTimestamp = "2025-06-16T12:00:00.000Z"))

    // Soft-delete the first entry
    entryDao.delete(/* entry with id1 */)

    // View-based query excludes deleted entry
    val result = entryDao.getEntriesByAccount("acc-1")
    assertThat(result).hasSize(1)
}

@Test
fun totalCount_excludesSoftDeletedEntries() = runTest {
    insertParentAccount()
    entryDao.insert(scaleEntry())
    entryDao.insert(scaleEntry(entryTimestamp = "2025-06-16T12:00:00.000Z"))

    // Delete one
    entryDao.delete(/* one entry */)

    assertThat(entryDao.getTotalCount("acc-1").first()).isEqualTo(1)
}
```

### Pattern G: Aggregation query testing

For queries that compute averages, counts, or group by period:
- Insert entries with **known values** at **noon UTC** timestamps to avoid timezone ambiguity
- Verify exact computed values
- Use `isWithin(tolerance)` for floating-point comparisons

```kotlin
@Test
fun monthlyAverages_computesCorrectly() = runTest {
    insertParentAccount()
    entryDao.insert(scaleEntry(entryTimestamp = "2025-06-10T12:00:00.000Z", weight = 180.0))
    entryDao.insert(scaleEntry(entryTimestamp = "2025-06-20T12:00:00.000Z", weight = 170.0))

    val result = entryDao.getMonthlyBodyScaleAveragesWithJoin("acc-1").first()
    assertThat(result[0].weight).isWithin(0.1).of(175.0)
}
```

### Pattern H: State management methods

```kotlin
@Test
fun activateAccount_setsIsActiveAccountTrue() = runTest {
    accountDao.insertAccount(account(isActiveAccount = false))

    accountDao.activateAccount("acc-1")

    assertThat(accountDao.getAccountEntity("acc-1")?.isActiveAccount).isTrue()
}

@Test
fun deactivateOtherAccounts_deactivatesAllExceptTarget() = runTest {
    accountDao.insertAccount(account(id = "acc-1", isActiveAccount = true, email = "a@test.com"))
    accountDao.insertAccount(account(id = "acc-2", isActiveAccount = true, email = "b@test.com"))

    accountDao.deactivateOtherAccounts("acc-1")

    assertThat(accountDao.getAccountEntity("acc-1")?.isActiveAccount).isTrue()
    assertThat(accountDao.getAccountEntity("acc-2")?.isActiveAccount).isFalse()
}
```

### Pattern I: Unsynced/filtered queries

```kotlin
@Test
fun getUnsyncedAccounts_returnsOnlyUnsynced() = runTest {
    accountDao.insertAccount(account(id = "acc-1", isSynced = false, email = "a@test.com"))
    accountDao.insertAccount(account(id = "acc-2", isSynced = true, email = "b@test.com"))

    val result = accountDao.getUnsyncedAccounts()
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo("acc-1")
}
```

### Pattern J: `datetime('now')` queries

Queries like `getMonthlyHistoryLastYear` use `datetime('now')`. For these:
- Use timestamps within the last 30 days for "recent" test data
- Use timestamps >365 days ago for "old" test data
- Assert with `isAtMost()` since exact behavior depends on test execution time

```kotlin
@Test
fun getRecentEntries_returnsOnlyLast30Days() = runTest {
    insertParentAccount()
    // Recent entry (within 30 days)
    entryDao.insert(scaleEntry(entryTimestamp = recentTimestamp()))
    // Old entry (>365 days ago)
    entryDao.insert(scaleEntry(entryTimestamp = "2024-01-01T12:00:00.000Z"))

    val result = entryDao.getRecentEntries("acc-1")
    assertThat(result.size).isAtMost(1)
}
```

### Pattern K: Multiple accounts — unique email constraint

When inserting multiple accounts, always use unique emails:

```kotlin
@Test
fun getAllLoggedInAccounts_returnsMultipleAccounts() = runTest {
    accountDao.insertFullAccount(account(id = "acc-1", email = "a@test.com", isLoggedIn = true))
    accountDao.insertFullAccount(account(id = "acc-2", email = "b@test.com", isLoggedIn = true))
    accountDao.insertFullAccount(account(id = "acc-3", email = "c@test.com", isLoggedIn = false))

    val result = accountDao.getAllLoggedInAccounts().first()
    assertThat(result).hasSize(2)
    assertThat(result.map { it.account.id }).containsExactly("acc-1", "acc-2")
}
```

## Run commands

```bash
# Compile check (no emulator needed — catches most errors)
./gradlew :app:compileDebugAndroidTestKotlin

# Single DAO test class (requires emulator)
./gradlew :app:connectedDebugAndroidTest --tests "*.LogDaoTest"

# All DAO tests
./gradlew :app:connectedDebugAndroidTest --tests "com.dmdbrands.gurus.weight.data.storage.db.dao.*"

# All instrumented tests
./gradlew :app:connectedDebugAndroidTest
```

> Requires a connected emulator or device. Always run `compileDebugAndroidTestKotlin` first.

## Test reports

After running `connectedDebugAndroidTest`, reports are generated at:
- HTML: `app/build/reports/androidTests/connected/debug/index.html`
- XML: `app/build/outputs/androidTest-results/connected/debug/`

## Test file placement

```
Source:  app/src/main/java/com/dmdbrands/gurus/weight/data/storage/db/dao/FooDao.kt
Test:    app/src/androidTest/java/com/dmdbrands/gurus/weight/data/storage/db/dao/FooDaoTest.kt
```

## DAO-specific success criteria

- [ ] Test extends `BaseDaoTest`
- [ ] Uses `@RunWith(AndroidJUnit4::class)`
- [ ] Uses `@OptIn(ExperimentalCoroutinesApi::class)` if using `runTest`
- [ ] Uses `DaoTestFixtures` for entity construction
- [ ] FK parents inserted before children (`insertFullAccount()`)
- [ ] Flow queries use `flow.first()` (NOT Turbine)
- [ ] **No redundant flow collection** — store `flow.first()` in a `val` when asserting multiple properties
- [ ] Reactive behavior tested: `first()` before + after mutation
- [ ] `entry_view` soft-delete filtering tested (if EntryDao)
- [ ] **CASCADE delete: ALL child entities verified** — not just one settings table; use `getAccount().first()` which checks all 7 relations
- [ ] **Isolation tests: ALL relations verified for surviving entity** — check all 7 settings, not a subset
- [ ] Aggregation queries use noon UTC timestamps (`T12:00:00.000Z`)
- [ ] Conflict strategies tested (duplicate → REPLACE/IGNORE)
- [ ] Multiple accounts use unique emails
- [ ] State management methods tested (activate, deactivate, logout)
- [ ] Unsynced/filtered queries tested — **all SQL branches covered** (e.g., `OR isSynced = 0` in compound WHERE clauses)
- [ ] **Import ordering**: `androidx.test.*` → `com.dmdbrands.*` → `com.google.*` → `kotlinx.*` → `org.junit.*`
- [ ] Test naming follows `methodName_condition` pattern (e.g., `insertAccount_storesAndRetrievesEntity`)
- [ ] Every test has at least one meaningful assertion (no assertion-free "no-op" tests)
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` passes
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "*.{DaoName}Test"` passes
- [ ] File placed in `androidTest/` (NOT `test/`)
- [ ] Truth assertions used throughout (`assertThat`)
- [ ] No MockK or Turbine imports
