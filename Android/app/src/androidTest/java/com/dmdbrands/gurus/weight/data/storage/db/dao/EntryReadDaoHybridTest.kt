package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.scaleEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the MA-3965 hybrid daywise weight graph query
 * ([EntryReadDao.getWeightDailyGraphData]): the most recent day with a valid weight
 * surfaces that day's latest entry, while every earlier day surfaces the daily average.
 */
// TODO(MA-3965): This instrumented test compiles, but the androidTest source set on
//  phase2-dev currently fails to COMPILE due to pre-existing breakage unrelated to this
//  change (e.g. EntryDaoTest references the removed `entryTimestamp` API). Until that is
//  fixed, `connectedAndroidTest` cannot run, so this class cannot execute. Re-enable once
//  the androidTest suite compiles.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EntryReadDaoHybridTest : BaseDaoTest() {

    private suspend fun insertParentAccount(accountId: String = "acc-1") {
        accountDao.insertFullAccount(account(id = accountId, email = "$accountId@test.com"))
    }

    @Test
    fun getWeightDailyGraphData_latestDay_usesLatestEntry_not_average() = runTest {
        insertParentAccount()
        // Latest day (2025-06-16): two entries — latest (20:00) is 160, earlier (08:00) is 200.
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-16T08:00:00.000Z", weight = 200.0))
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-16T20:00:00.000Z", weight = 160.0))

        val latest = entryReadDao.getWeightDailyGraphData("acc-1").first()
            .first { it.period == "2025-06-16" }

        // Latest entry semantics → 160 (the 20:00 reading), NOT the average of 180.
        assertThat(latest.weight).isEqualTo(160.0)
    }

    @Test
    fun getWeightDailyGraphData_earlierDay_usesDailyAverage() = runTest {
        insertParentAccount()
        // Earlier day (2025-06-15): two entries → average of 180 and 170 = 175.
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-15T08:00:00.000Z", weight = 180.0))
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-15T20:00:00.000Z", weight = 170.0))
        // Latest day (2025-06-16): single entry so it is unambiguously the latest day.
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-16T12:00:00.000Z", weight = 150.0))

        val result = entryReadDao.getWeightDailyGraphData("acc-1").first()
        val earlier = result.first { it.period == "2025-06-15" }
        val latest = result.first { it.period == "2025-06-16" }

        assertThat(result).hasSize(2)
        // Earlier day → daily average (175), latest day → its (only) entry (150).
        assertThat(earlier.weight).isEqualTo(175.0)
        assertThat(latest.weight).isEqualTo(150.0)
    }

    @Test
    fun getWeightDailyGraphData_singleDay_usesLatestEntry() = runTest {
        insertParentAccount()
        // Only one day exists → it is the latest day → latest-entry semantics (170, the 20:00 reading).
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-15T08:00:00.000Z", weight = 180.0))
        entryDao.insert(scaleEntry(entryTimestamp = "2025-06-15T20:00:00.000Z", weight = 170.0))

        val result = entryReadDao.getWeightDailyGraphData("acc-1").first()

        assertThat(result).hasSize(1)
        assertThat(result[0].weight).isEqualTo(170.0)
    }
}
