package com.dmdbrands.gurus.weight.domain.model.api.entry

/**
 * Response body for `POST /v3/entries/`: the persisted entries echoed back
 * (flat, nulls stripped) plus the server sync [timestamp].
 */
data class UnifiedEntryResponse(
    val entries: List<UnifiedEntry>,
    // Nullable: server may omit the sync cursor; guard before writing it (MOB-591).
    val timestamp: String?,
)

/**
 * A single entry as returned by the unified API. Carries common + weight + BP
 * fields plus the server-assigned [serverTimestamp]. (Weight metric extras beyond
 * the documented set are not modeled here; full read mapping is MOB-380.)
 */
data class UnifiedEntry(
    val category: String? = null,
    val operationType: String? = null,
    val entryTimestamp: String,
    val serverTimestamp: String? = null,

    // weight
    val weight: Int? = null,
    val bodyFat: Int? = null,
    val muscleMass: Int? = null,
    val water: Int? = null,
    val bmi: Int? = null,
    val boneMass: Int? = null,
    val impedance: Int? = null,
    val unit: String? = null,
    // Advanced R4 body-composition metrics (12-metric scales); echoed back on POST so a
    // freshly-saved 12-metric reading isn't stripped when the response overwrites local. (MOB-1496 follow-up)
    val visceralFatLevel: Int? = null,
    val subcutaneousFatPercent: Int? = null,
    val proteinPercent: Int? = null,
    val skeletalMusclePercent: Int? = null,
    val bmr: Int? = null,
    val metabolicAge: Int? = null,

    // bp
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val note: String? = null,

    // baby (§2.16/§2.17): weight & measureLength are distinct entryTypes
    val babyId: String? = null,
    val entryId: String? = null,
    val entryType: String? = null,
    val babyWeightDecigrams: Int? = null,
    val babyLengthMillimeters: Int? = null,
    val entryNote: String? = null,

    // shared
    val pulse: Int? = null,
    val source: String? = null,
)
