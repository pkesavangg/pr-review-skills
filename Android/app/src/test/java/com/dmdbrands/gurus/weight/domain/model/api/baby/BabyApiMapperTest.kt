package com.dmdbrands.gurus.weight.domain.model.api.baby

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.enums.BabySex
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BabyApiMapperTest {

    companion object {
        private const val ACCOUNT_ID = "account-123"
        private const val BABY_ID = "baby-1"
        private const val NAME = "Luna"
        private const val BIRTHDATE = "2023-01-15"
        private const val TIMESTAMP = "2024-05-01T10:00:00Z"
    }

    private fun profile() = BabyProfile(
        id = BABY_ID,
        accountId = ACCOUNT_ID,
        name = NAME,
        birthdate = BIRTHDATE,
        sex = "female",
        birthWeightDecigrams = 320,
        birthLengthMillimeters = 500,
    )

    // ── BabySex ──────────────────────────────────────────────────────────────────

    @Test
    fun `BabySex fromValue resolves known values`() {
        assertThat(BabySex.fromValue("male")).isEqualTo(BabySex.MALE)
        assertThat(BabySex.fromValue("female")).isEqualTo(BabySex.FEMALE)
        assertThat(BabySex.fromValue("private")).isEqualTo(BabySex.PRIVATE)
    }

    @Test
    fun `BabySex fromValue defaults to PRIVATE for null or unknown`() {
        assertThat(BabySex.fromValue(null)).isEqualTo(BabySex.PRIVATE)
        assertThat(BabySex.fromValue("alien")).isEqualTo(BabySex.PRIVATE)
    }

    // ── BabyEntryType ──────────────────────────────────────────────────────────────

    @Test
    fun `BabyEntryType fromValue resolves known values`() {
        assertThat(BabyEntryType.fromValue("weight")).isEqualTo(BabyEntryType.WEIGHT)
        assertThat(BabyEntryType.fromValue("measureLength")).isEqualTo(BabyEntryType.MEASURE_LENGTH)
    }

    @Test
    fun `BabyEntryType fromValue defaults to WEIGHT for null or unknown`() {
        assertThat(BabyEntryType.fromValue(null)).isEqualTo(BabyEntryType.WEIGHT)
        assertThat(BabyEntryType.fromValue("sleep")).isEqualTo(BabyEntryType.WEIGHT)
    }

    // ── BabyProfile.toRequest ──────────────────────────────────────────────────────

    @Test
    fun `toRequest copies all fields`() {
        val request = profile().toRequest()

        assertThat(request.name).isEqualTo(NAME)
        assertThat(request.birthdate).isEqualTo(BIRTHDATE)
        assertThat(request.sex).isEqualTo("female")
        assertThat(request.birthWeightDecigrams).isEqualTo(320)
        assertThat(request.birthLengthMillimeters).isEqualTo(500)
    }

    @Test
    fun `toRequest normalises sex and tolerates nulls`() {
        val request = profile().copy(
            sex = "garbage",
            birthdate = null,
            birthWeightDecigrams = null,
            birthLengthMillimeters = null,
        ).toRequest()

        // Unknown sex normalises to PRIVATE's value.
        assertThat(request.sex).isEqualTo(BabySex.PRIVATE.value)
        assertThat(request.birthdate).isNull()
        assertThat(request.birthWeightDecigrams).isNull()
        assertThat(request.birthLengthMillimeters).isNull()
    }

    @Test
    fun `toRequest keeps null sex as null`() {
        val request = profile().copy(sex = null).toRequest()
        assertThat(request.sex).isNull()
    }

    // ── BabyResponse.toDomain ──────────────────────────────────────────────────────

    @Test
    fun `toDomain maps response and marks synced`() {
        val response = BabyResponse(
            id = "server-id",
            name = NAME,
            birthdate = BIRTHDATE,
            sex = "male",
            birthWeightDecigrams = 300,
            birthLengthMillimeters = 480,
        )

        val domain = response.toDomain(ACCOUNT_ID)

        assertThat(domain.id).isEqualTo("server-id")
        assertThat(domain.accountId).isEqualTo(ACCOUNT_ID)
        assertThat(domain.name).isEqualTo(NAME)
        assertThat(domain.isSynced).isTrue()
    }

    @Test
    fun `list toDomain maps every item`() {
        val responses = listOf(
            BabyResponse(id = "a", name = "A"),
            BabyResponse(id = "b", name = "B"),
        )

        val domain = responses.toDomain(ACCOUNT_ID)

        assertThat(domain.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(domain.all { it.accountId == ACCOUNT_ID }).isTrue()
    }

    // ── BabyEntry.toBabyEntryRequest (gated DTO) ────────────────────────────────────

    private fun babyEntry(type: String, weight: Int? = null, length: Int? = null) = BabyEntry(
        entry = EntryEntity(
            accountId = ACCOUNT_ID,
            entryTimestamp = TIMESTAMP,
            operationType = "CREATE",
            deviceType = "manual",
            deviceId = "device-1",
        ),
        babyEntry = BabyEntryEntity(
            id = 1L,
            babyId = BABY_ID,
            babyWeightDecigrams = weight,
            babyLengthMillimeters = length,
            entryNote = "note",
            entryType = type,
            source = "manual",
        ),
    )

    @Test
    fun `weight entry maps weight and nulls length`() {
        val request = babyEntry("weight", weight = 350, length = 999).toBabyEntryRequest()

        assertThat(request.babyId).isEqualTo(BABY_ID)
        assertThat(request.entryType).isEqualTo(BabyEntryType.WEIGHT.value)
        assertThat(request.entryTimestamp).isEqualTo(TIMESTAMP)
        assertThat(request.operationType).isEqualTo("create")
        assertThat(request.babyWeightDecigrams).isEqualTo(350)
        assertThat(request.babyLengthMillimeters).isNull()
        assertThat(request.source).isEqualTo("manual")
    }

    @Test
    fun `length entry maps length and nulls weight`() {
        val request = babyEntry("measureLength", weight = 999, length = 510).toBabyEntryRequest()

        assertThat(request.entryType).isEqualTo(BabyEntryType.MEASURE_LENGTH.value)
        assertThat(request.babyLengthMillimeters).isEqualTo(510)
        assertThat(request.babyWeightDecigrams).isNull()
    }
}
