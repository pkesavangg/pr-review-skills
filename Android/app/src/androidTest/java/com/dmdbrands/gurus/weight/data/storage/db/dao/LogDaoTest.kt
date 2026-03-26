package com.dmdbrands.gurus.weight.data.storage.db.dao

import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.logEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LogDaoTest : BaseDaoTest() {

    // -------------------------------------------------------------------------
    // insertLog
    // -------------------------------------------------------------------------

    @Test
    fun insertLog_storesAndRetrieves() = runTest {
        val log = logEntity()
        logDao.insertLog(log)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(log)
    }

    @Test
    fun insertLog_replacesOnConflict() = runTest {
        val log = logEntity(id = "log-1", message = "original")
        logDao.insertLog(log)

        val updated = log.copy(message = "replaced")
        logDao.insertLog(updated)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].message).isEqualTo("replaced")
    }

    @Test
    fun insertLog_preservesNullableDataField() = runTest {
        val log = logEntity(data = "stack trace info")
        logDao.insertLog(log)

        val result = logDao.getAllLogs().first()
        assertThat(result[0].data).isEqualTo("stack trace info")
    }

    @Test
    fun insertLog_preservesNullDataField() = runTest {
        val log = logEntity(data = null)
        logDao.insertLog(log)

        val result = logDao.getAllLogs().first()
        assertThat(result[0].data).isNull()
    }

    // -------------------------------------------------------------------------
    // insertLogs
    // -------------------------------------------------------------------------

    @Test
    fun insertLogs_bulkInsert() = runTest {
        val logs = (1..5).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        logDao.insertLogs(logs)

        assertThat(logDao.getAllLogs().first()).hasSize(5)
    }

    @Test
    fun insertLogs_emptyList_noOp() = runTest {
        logDao.insertLogs(emptyList())

        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    @Test
    fun insertLogs_replacesOnConflict() = runTest {
        logDao.insertLog(logEntity(id = "log-1", message = "original"))

        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", message = "replaced"),
                logEntity(id = "log-2", message = "new"),
            )
        )

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(2)
        assertThat(result.first { it.id == "log-1" }.message).isEqualTo("replaced")
    }

    // -------------------------------------------------------------------------
    // updateLog
    // -------------------------------------------------------------------------

    @Test
    fun updateLog_modifiesExisting() = runTest {
        val log = logEntity()
        logDao.insertLog(log)

        logDao.updateLog(log.copy(message = "updated message"))

        val result = logDao.getAllLogs().first()
        assertThat(result[0].message).isEqualTo("updated message")
    }

    @Test
    fun updateLog_nonExistent_noOp() = runTest {
        logDao.insertLog(logEntity(id = "log-1"))

        logDao.updateLog(logEntity(id = "log-999", message = "ghost"))

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("log-1")
    }

    // -------------------------------------------------------------------------
    // deleteLog
    // -------------------------------------------------------------------------

    @Test
    fun deleteLog_removesSpecificLog() = runTest {
        val log1 = logEntity(id = "log-1", timestamp = 2)
        val log2 = logEntity(id = "log-2", timestamp = 1)
        logDao.insertLogs(listOf(log1, log2))

        logDao.deleteLog(log1)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("log-2")
    }

    @Test
    fun deleteLog_nonExistent_noOp() = runTest {
        logDao.insertLog(logEntity(id = "log-1"))

        logDao.deleteLog(logEntity(id = "log-999"))

        assertThat(logDao.getAllLogs().first()).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // getAllLogs
    // -------------------------------------------------------------------------

    @Test
    fun getAllLogs_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 100),
                logEntity(id = "log-2", timestamp = 300),
                logEntity(id = "log-3", timestamp = 200),
            )
        )

        val result = logDao.getAllLogs().first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    @Test
    fun getAllLogs_emptyDatabase_returnsEmptyList() = runTest {
        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    @Test
    fun getAllLogs_reactiveAfterInsert() = runTest {
        assertThat(logDao.getAllLogs().first()).isEmpty()

        logDao.insertLog(logEntity(id = "log-1"))
        assertThat(logDao.getAllLogs().first()).hasSize(1)

        logDao.insertLog(logEntity(id = "log-2"))
        assertThat(logDao.getAllLogs().first()).hasSize(2)
    }

    @Test
    fun getAllLogs_reactiveAfterDelete() = runTest {
        val log = logEntity()
        logDao.insertLog(log)
        assertThat(logDao.getAllLogs().first()).hasSize(1)

        logDao.deleteLog(log)
        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getLogsByAccountId
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByAccountId_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1"),
                logEntity(id = "log-2", accountId = "acc-2"),
                logEntity(id = "log-3", accountId = "acc-1"),
            )
        )

        val result = logDao.getLogsByAccountId("acc-1").first()
        assertThat(result).hasSize(2)
        assertThat(result.all { it.accountId == "acc-1" }).isTrue()
    }

    @Test
    fun getLogsByAccountId_nonExistentAccount_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(accountId = "acc-1"))

        assertThat(logDao.getLogsByAccountId("acc-999").first()).isEmpty()
    }

    @Test
    fun getLogsByAccountId_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1", timestamp = 100),
                logEntity(id = "log-2", accountId = "acc-1", timestamp = 300),
                logEntity(id = "log-3", accountId = "acc-1", timestamp = 200),
            )
        )

        val result = logDao.getLogsByAccountId("acc-1").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsBySessionId
    // -------------------------------------------------------------------------

    @Test
    fun getLogsBySessionId_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", sessionId = "s1"),
                logEntity(id = "log-2", sessionId = "s2"),
            )
        )

        assertThat(logDao.getLogsBySessionId("s1").first()).hasSize(1)
    }

    @Test
    fun getLogsBySessionId_nonExistentSession_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(sessionId = "s1"))

        assertThat(logDao.getLogsBySessionId("s-999").first()).isEmpty()
    }

    @Test
    fun getLogsBySessionId_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", sessionId = "s1", timestamp = 100),
                logEntity(id = "log-2", sessionId = "s1", timestamp = 300),
                logEntity(id = "log-3", sessionId = "s1", timestamp = 200),
            )
        )

        val result = logDao.getLogsBySessionId("s1").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByType
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByType_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", type = "e"),
                logEntity(id = "log-2", type = "i"),
                logEntity(id = "log-3", type = "e"),
            )
        )

        assertThat(logDao.getLogsByType("e").first()).hasSize(2)
    }

    @Test
    fun getLogsByType_nonExistentType_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(type = "i"))

        assertThat(logDao.getLogsByType("x").first()).isEmpty()
    }

    @Test
    fun getLogsByType_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", type = "e", timestamp = 100),
                logEntity(id = "log-2", type = "e", timestamp = 300),
                logEntity(id = "log-3", type = "e", timestamp = 200),
            )
        )

        val result = logDao.getLogsByType("e").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByTag
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByTag_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", tag = "AccountService"),
                logEntity(id = "log-2", tag = "DeviceService"),
            )
        )

        assertThat(logDao.getLogsByTag("AccountService").first()).hasSize(1)
    }

    @Test
    fun getLogsByTag_nonExistentTag_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(tag = "AccountService"))

        assertThat(logDao.getLogsByTag("UnknownService").first()).isEmpty()
    }

    @Test
    fun getLogsByTag_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", tag = "AccountService", timestamp = 100),
                logEntity(id = "log-2", tag = "AccountService", timestamp = 300),
                logEntity(id = "log-3", tag = "AccountService", timestamp = 200),
            )
        )

        val result = logDao.getLogsByTag("AccountService").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByTagId
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByTagId_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", tagId = "login"),
                logEntity(id = "log-2", tagId = "logout"),
            )
        )

        assertThat(logDao.getLogsByTagId("login").first()).hasSize(1)
    }

    @Test
    fun getLogsByTagId_nonExistentTagId_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(tagId = "login"))

        assertThat(logDao.getLogsByTagId("unknown").first()).isEmpty()
    }

    @Test
    fun getLogsByTagId_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", tagId = "login", timestamp = 100),
                logEntity(id = "log-2", tagId = "login", timestamp = 300),
                logEntity(id = "log-3", tagId = "login", timestamp = 200),
            )
        )

        val result = logDao.getLogsByTagId("login").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByTimeRange
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByTimeRange_returnsLogsInRange() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 50),
                logEntity(id = "log-2", timestamp = 150),
                logEntity(id = "log-3", timestamp = 250),
            )
        )

        val result = logDao.getLogsByTimeRange(100, 200).first()
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("log-2")
    }

    @Test
    fun getLogsByTimeRange_excludesOutOfRange() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 10),
                logEntity(id = "log-2", timestamp = 500),
            )
        )

        assertThat(logDao.getLogsByTimeRange(100, 200).first()).isEmpty()
    }

    @Test
    fun getLogsByTimeRange_inclusiveBoundaries() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 100),
                logEntity(id = "log-2", timestamp = 200),
                logEntity(id = "log-3", timestamp = 99),
                logEntity(id = "log-4", timestamp = 201),
            )
        )

        val result = logDao.getLogsByTimeRange(100, 200).first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("log-2", "log-1").inOrder()
    }

    @Test
    fun getLogsByTimeRange_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 110),
                logEntity(id = "log-2", timestamp = 190),
                logEntity(id = "log-3", timestamp = 150),
            )
        )

        val result = logDao.getLogsByTimeRange(100, 200).first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByAccountIdAndType
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByAccountIdAndType_filtersCorrectly() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1", type = "e"),
                logEntity(id = "log-2", accountId = "acc-1", type = "i"),
                logEntity(id = "log-3", accountId = "acc-2", type = "e"),
            )
        )

        val result = logDao.getLogsByAccountIdAndType("acc-1", "e").first()
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("log-1")
    }

    @Test
    fun getLogsByAccountIdAndType_noMatch_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(accountId = "acc-1", type = "e"))

        assertThat(logDao.getLogsByAccountIdAndType("acc-1", "d").first()).isEmpty()
        assertThat(logDao.getLogsByAccountIdAndType("acc-2", "e").first()).isEmpty()
    }

    @Test
    fun getLogsByAccountIdAndType_orderedByTimestampDesc() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1", type = "e", timestamp = 100),
                logEntity(id = "log-2", accountId = "acc-1", type = "e", timestamp = 300),
                logEntity(id = "log-3", accountId = "acc-1", type = "e", timestamp = 200),
            )
        )

        val result = logDao.getLogsByAccountIdAndType("acc-1", "e").first()
        assertThat(result.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    // -------------------------------------------------------------------------
    // getLogsByAccountIdWithLimit
    // -------------------------------------------------------------------------

    @Test
    fun getLogsByAccountIdWithLimit_respectsLimit() = runTest {
        logDao.insertLogs(
            (1..10).map { logEntity(id = "log-$it", accountId = "acc-1", timestamp = it.toLong()) }
        )

        assertThat(logDao.getLogsByAccountIdWithLimit("acc-1", 3).first()).hasSize(3)
    }

    @Test
    fun getLogsByAccountIdWithLimit_returnsNewest() = runTest {
        logDao.insertLogs(
            (1..5).map { logEntity(id = "log-$it", accountId = "acc-1", timestamp = it.toLong()) }
        )

        val result = logDao.getLogsByAccountIdWithLimit("acc-1", 2).first()
        assertThat(result.map { it.id }).containsExactly("log-5", "log-4").inOrder()
    }

    @Test
    fun getLogsByAccountIdWithLimit_limitExceedsTotal_returnsAll() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1"),
                logEntity(id = "log-2", accountId = "acc-1"),
            )
        )

        assertThat(logDao.getLogsByAccountIdWithLimit("acc-1", 100).first()).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // getLogsForLastDays
    // -------------------------------------------------------------------------

    @Test
    fun getLogsForLastDays_returnsLogsAfterTimestamp() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 50),
                logEntity(id = "log-2", timestamp = 150),
                logEntity(id = "log-3", timestamp = 250),
            )
        )

        val result = logDao.getLogsForLastDays(100).first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("log-3", "log-2").inOrder()
    }

    @Test
    fun getLogsForLastDays_inclusiveBoundary() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 99),
                logEntity(id = "log-2", timestamp = 100),
                logEntity(id = "log-3", timestamp = 101),
            )
        )

        val result = logDao.getLogsForLastDays(100).first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("log-3", "log-2").inOrder()
    }

    @Test
    fun getLogsForLastDays_noLogsAfterTimestamp_returnsEmpty() = runTest {
        logDao.insertLog(logEntity(timestamp = 50))

        assertThat(logDao.getLogsForLastDays(100).first()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getAllLogsPaginated
    // -------------------------------------------------------------------------

    @Test
    fun getAllLogsPaginated_returnsCorrectPage() = runTest {
        logDao.insertLogs(
            (1..10).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )

        val result = logDao.getAllLogsPaginated(limit = 3, offset = 2).first()
        assertThat(result).hasSize(3)
        // Ordered DESC, so offset=2 skips the two highest timestamps (10, 9) -> starts at 8
        assertThat(result[0].timestamp).isEqualTo(8L)
    }

    @Test
    fun getAllLogsPaginated_firstPage() = runTest {
        logDao.insertLogs(
            (1..5).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )

        val result = logDao.getAllLogsPaginated(limit = 2, offset = 0).first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.timestamp }).containsExactly(5L, 4L).inOrder()
    }

    @Test
    fun getAllLogsPaginated_offsetBeyondTotal_returnsEmpty() = runTest {
        logDao.insertLogs(
            (1..3).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )

        assertThat(logDao.getAllLogsPaginated(limit = 10, offset = 100).first()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getLogCount
    // -------------------------------------------------------------------------

    @Test
    fun getLogCount_returnsCorrectCount() = runTest {
        assertThat(logDao.getLogCount().first()).isEqualTo(0)

        logDao.insertLog(logEntity(id = "log-1"))
        assertThat(logDao.getLogCount().first()).isEqualTo(1)

        logDao.insertLog(logEntity(id = "log-2"))
        assertThat(logDao.getLogCount().first()).isEqualTo(2)
    }

    @Test
    fun getLogCount_reactiveAfterDelete() = runTest {
        val log = logEntity()
        logDao.insertLog(log)
        assertThat(logDao.getLogCount().first()).isEqualTo(1)

        logDao.deleteLog(log)
        assertThat(logDao.getLogCount().first()).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // deleteAllLogs
    // -------------------------------------------------------------------------

    @Test
    fun deleteAllLogs_removesAll() = runTest {
        logDao.insertLogs(listOf(logEntity(id = "log-1"), logEntity(id = "log-2")))
        logDao.deleteAllLogs()

        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    @Test
    fun deleteAllLogs_emptyDatabase_noOp() = runTest {
        logDao.deleteAllLogs()

        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // deleteLogsByAccountId
    // -------------------------------------------------------------------------

    @Test
    fun deleteLogsByAccountId_removesOnlyForAccount() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", accountId = "acc-1"),
                logEntity(id = "log-2", accountId = "acc-2"),
            )
        )
        logDao.deleteLogsByAccountId("acc-1")

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].accountId).isEqualTo("acc-2")
    }

    @Test
    fun deleteLogsByAccountId_nonExistentAccount_noOp() = runTest {
        logDao.insertLog(logEntity(accountId = "acc-1"))

        logDao.deleteLogsByAccountId("acc-999")

        assertThat(logDao.getAllLogs().first()).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // deleteLogsBySessionId
    // -------------------------------------------------------------------------

    @Test
    fun deleteLogsBySessionId_removesOnlyForSession() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", sessionId = "s1"),
                logEntity(id = "log-2", sessionId = "s2"),
            )
        )
        logDao.deleteLogsBySessionId("s1")

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(1)
        assertThat(result[0].sessionId).isEqualTo("s2")
    }

    @Test
    fun deleteLogsBySessionId_nonExistentSession_noOp() = runTest {
        logDao.insertLog(logEntity(sessionId = "s1"))

        logDao.deleteLogsBySessionId("s-999")

        assertThat(logDao.getAllLogs().first()).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // deleteLogsOlderThanDays
    // -------------------------------------------------------------------------

    @Test
    fun deleteLogsOlderThanDays_removesOlderLogs() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 50),
                logEntity(id = "log-2", timestamp = 150),
                logEntity(id = "log-3", timestamp = 250),
            )
        )
        logDao.deleteLogsOlderThanDays(100)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(2)
        assertThat(result.all { it.timestamp >= 100 }).isTrue()
    }

    @Test
    fun deleteLogsOlderThanDays_boundaryExclusive() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 99),
                logEntity(id = "log-2", timestamp = 100),
                logEntity(id = "log-3", timestamp = 101),
            )
        )
        // DELETE WHERE timestamp < 100 → removes 99, keeps 100 and 101
        logDao.deleteLogsOlderThanDays(100)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("log-3", "log-2").inOrder()
    }

    @Test
    fun deleteLogsOlderThanDays_noOlderLogs_noOp() = runTest {
        logDao.insertLogs(
            listOf(
                logEntity(id = "log-1", timestamp = 200),
                logEntity(id = "log-2", timestamp = 300),
            )
        )
        logDao.deleteLogsOlderThanDays(100)

        assertThat(logDao.getAllLogs().first()).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // deleteOldestLogs
    // -------------------------------------------------------------------------

    @Test
    fun deleteOldestLogs_keepsNewestN() = runTest {
        logDao.insertLogs(
            (1..5).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )
        // Keep only the 3 newest by id (ORDER BY id DESC → "log-5", "log-4", "log-3")
        logDao.deleteOldestLogs(3)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(3)
        assertThat(result.map { it.id }).containsExactly("log-5", "log-4", "log-3").inOrder()
    }

    @Test
    fun deleteOldestLogs_countExceedsTotal_keepsAll() = runTest {
        logDao.insertLogs(
            (1..3).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )
        logDao.deleteOldestLogs(10)

        assertThat(logDao.getAllLogs().first()).hasSize(3)
    }

    @Test
    fun deleteOldestLogs_countZero_deletesAll() = runTest {
        logDao.insertLogs(
            (1..3).map { logEntity(id = "log-$it", timestamp = it.toLong()) }
        )
        logDao.deleteOldestLogs(0)

        assertThat(logDao.getAllLogs().first()).isEmpty()
    }

    @Test
    fun deleteOldestLogs_keepsNewestById() = runTest {
        // The query uses ORDER BY id DESC LIMIT :count — keeps newest by id, not by timestamp
        logDao.insertLogs(
            listOf(
                logEntity(id = "a-oldest", timestamp = 300),
                logEntity(id = "b-middle", timestamp = 100),
                logEntity(id = "c-newest", timestamp = 200),
            )
        )
        logDao.deleteOldestLogs(2)

        val result = logDao.getAllLogs().first()
        assertThat(result).hasSize(2)
        // Kept by id DESC ("c-newest", "b-middle"), NOT by timestamp DESC (which would be "a-oldest", "c-newest")
        assertThat(result.map { it.id }).containsExactly("c-newest", "b-middle")
    }
}
