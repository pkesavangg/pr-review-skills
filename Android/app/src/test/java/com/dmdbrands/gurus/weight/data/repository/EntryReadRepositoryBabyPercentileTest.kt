package com.dmdbrands.gurus.weight.data.repository

import android.content.Context
import android.content.res.Resources
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryReadDao
import com.dmdbrands.gurus.weight.domain.model.common.BabyDailySummaryResult
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

/**
 * Verifies the baby weekly-history read path populates each row's CDC growth
 * percentile (the wiring added for the history-screen percentile feature).
 *
 * Loads the REAL res/raw CSVs (mocking only Android Context) so this exercises the
 * actual percentile pipeline — day-0 boy weight M=33464/SD=4886 means the published
 * percentile-band values round-trip to their own band numbers.
 */
class EntryReadRepositoryBabyPercentileTest {

    // res/raw isn't on the unit-test classpath, so read the CSVs from disk. Gradle runs tests
    // with workingDir = the :app module dir, but resolve defensively against a few roots so the
    // test is independent of where it's launched (CI, IDE, repo root).
    private val rawDir: String = listOf(
        "src/main/res/raw",
        "app/src/main/res/raw",
        "Android/app/src/main/res/raw",
    ).firstOrNull { File(it, "boy_weight_decigrams.csv").exists() } ?: "src/main/res/raw"

    private val fileForId: Map<Int, String> = mapOf(
        R.raw.boy_weight_percentiles to "boy_weight_percentiles.csv",
        R.raw.girl_weight_percentiles to "girl_weight_percentiles.csv",
        R.raw.boy_length_percentiles to "boy_length_percentiles.csv",
        R.raw.girl_length_percentiles to "girl_length_percentiles.csv",
        R.raw.boy_weight_decigrams to "boy_weight_decigrams.csv",
        R.raw.girl_weight_decigrams to "girl_weight_decigrams.csv",
        R.raw.boy_length_mm to "boy_length_mm.csv",
        R.raw.girl_length_mm to "girl_length_mm.csv",
    )

    private lateinit var dao: EntryReadDao
    private lateinit var context: Context

    // day 0 for the entry (dateKey == birthdate) → exact day-0 M/SD lookup
    private val birthIso = "2025-06-15"
    private val birthMillis = DateTimeConverter.isoToTimestamp(birthIso)

    @BeforeEach
    fun setup() {
        // reset singleton caches so loadIfNeeded re-reads from our mocked context
        listOf(
            "boyWeightData", "girlWeightData", "boyLengthData", "girlLengthData",
            "boyWeightMeasurements", "girlWeightMeasurements", "boyLengthMeasurements", "girlLengthMeasurements",
        ).forEach { f ->
            BabyPercentileHelper::class.java.getDeclaredField(f).apply { isAccessible = true }
                .set(BabyPercentileHelper, null)
        }
        val res = mockk<Resources>()
        every { res.openRawResource(any()) } answers {
            FileInputStream(File(rawDir, fileForId.getValue(firstArg())))
        }
        context = mockk<Context>()
        every { context.resources } returns res
        dao = mockk<EntryReadDao>()
    }

    private fun repoWith(summaries: List<BabyDailySummaryResult>): EntryReadRepository {
        every { dao.getBabyWeeklyHistory(any(), any()) } returns flowOf(summaries)
        return EntryReadRepository(dao, context)
    }

    private fun daySummary(weightDg: Int? = null, lengthMm: Int? = null) =
        BabyDailySummaryResult(
            date = "6/15/25", entryCount = 1,
            babyWeightDecigrams = weightDg, babyLengthMillimeters = lengthMm,
            weekNumber = 24, year = 2025, dateKey = birthIso,
        )

    @Test
    fun `boy weight row gets correct percentile`() = runTest {
        val repo = repoWith(listOf(daySummary(weightDg = 33464))) // p50 = M
        val row = repo.getBabyWeeklyHistory("acct", "baby", "male", birthMillis).first().first().entries.first()
        assertThat(row.percentile).isNotNull()
        assertThat(Math.abs(row.percentile!! - 50)).isAtMost(1)
    }

    @Test
    fun `boy weight p5 band maps to 5th percentile`() = runTest {
        val repo = repoWith(listOf(daySummary(weightDg = 25427)))
        val row = repo.getBabyWeeklyHistory("acct", "baby", "male", birthMillis).first().first().entries.first()
        assertThat(Math.abs(row.percentile!! - 5)).isAtMost(1)
    }

    @Test
    fun `weight is preferred over length on a combined row`() = runTest {
        // p50 weight + a length value; row should reflect the WEIGHT percentile (~50)
        val repo = repoWith(listOf(daySummary(weightDg = 33464, lengthMm = 600)))
        val row = repo.getBabyWeeklyHistory("acct", "baby", "male", birthMillis).first().first().entries.first()
        assertThat(Math.abs(row.percentile!! - 50)).isAtMost(1)
    }

    @Test
    fun `length-only row uses length percentile`() = runTest {
        // girl length day0 M=491.48 → p50 value 491 ≈ 50th
        val repo = repoWith(listOf(daySummary(lengthMm = 491)))
        val row = repo.getBabyWeeklyHistory("acct", "baby", "female", birthMillis).first().first().entries.first()
        assertThat(row.percentile).isNotNull()
        assertThat(Math.abs(row.percentile!! - 50)).isAtMost(2)
    }

    @Test
    fun `private sex yields null percentile`() = runTest {
        val repo = repoWith(listOf(daySummary(weightDg = 33464)))
        val row = repo.getBabyWeeklyHistory("acct", "baby", "private", birthMillis).first().first().entries.first()
        assertThat(row.percentile).isNull()
    }

    @Test
    fun `unknown birthdate yields null percentile`() = runTest {
        val repo = repoWith(listOf(daySummary(weightDg = 33464)))
        val row = repo.getBabyWeeklyHistory("acct", "baby", "male", 0L).first().first().entries.first()
        assertThat(row.percentile).isNull()
    }
}
