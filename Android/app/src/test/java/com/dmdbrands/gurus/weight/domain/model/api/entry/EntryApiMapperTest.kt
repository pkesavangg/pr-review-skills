package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Read-down mapping for `GET /v3/entries/` ([EntryApiModel] → domain [Entry]).
 * Baby support added in MOB-598 (the cross-device / reinstall round-trip).
 */
class EntryApiMapperTest {

    private val accountId = "acc-1"
    private val timestamp = "2026-06-10T10:00:00.000Z"

    @Test
    fun `weight row maps to ScaleEntry`() {
        val model = EntryApiModel(category = "weight", entryTimestamp = timestamp, weight = 750, unit = "lb")
        assertThat(model.toDomainEntry(accountId)).isInstanceOf(ScaleEntry::class.java)
    }

    @Test
    fun `bp row maps to BpmEntry`() {
        val model = EntryApiModel(category = "bp", entryTimestamp = timestamp, systolic = 120, diastolic = 80, pulse = 72)
        assertThat(model.toDomainEntry(accountId)).isInstanceOf(BpmEntry::class.java)
    }

    @Test
    fun `baby weight row maps to BabyEntry with its fields`() {
        val model = EntryApiModel(
            category = "baby",
            operationType = "create",
            entryTimestamp = timestamp,
            serverTimestamp = timestamp,
            babyId = "baby-1",
            entryType = "weight",
            babyWeightDecigrams = 45_200,
            entryNote = "after bath",
            source = "0220",
        )

        val domain = model.toDomainEntry(accountId) as? BabyEntry

        assertThat(domain).isNotNull()
        assertThat(domain?.babyId).isEqualTo("baby-1")
        assertThat(domain?.babyWeightDecigrams).isEqualTo(45_200)
        assertThat(domain?.entryType).isEqualTo("weight")
        assertThat(domain?.entryNote).isEqualTo("after bath")
        assertThat(domain?.entry?.accountId).isEqualTo(accountId)
        assertThat(domain?.entry?.isSynced).isTrue()
    }

    @Test
    fun `baby row without babyId is dropped`() {
        // No babyId → can't satisfy the baby_entry → baby_profile FK, so it must not persist.
        val model = EntryApiModel(category = "baby", entryTimestamp = timestamp, babyWeightDecigrams = 45_200)
        assertThat(model.toDomainEntry(accountId)).isNull()
    }

    @Test
    fun `unknown category is dropped`() {
        val model = EntryApiModel(category = "mystery", entryTimestamp = timestamp)
        assertThat(model.toDomainEntry(accountId)).isNull()
    }
}
