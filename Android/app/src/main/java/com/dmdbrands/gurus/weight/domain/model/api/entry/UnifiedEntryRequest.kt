package com.dmdbrands.gurus.weight.domain.model.api.entry

/**
 * A single entry in the unified `POST /v3/entries/` request body (the body is an
 * array of these — a mixed-category, atomic batch).
 *
 * Only the fields relevant to [category] are populated; Gson omits null fields
 * on the wire (spec: "nulls stripped"). `pulse`/`source` are shared between
 * weight and BP. Canonical for MOB-379; reused by MOB-380 (read) and MOB-381 (baby).
 */
data class UnifiedEntryRequest(
    // ── common (required) ──
    val category: String,
    val operationType: String,
    val entryTimestamp: String,

    // ── weight ──
    val weight: Int? = null,
    val bodyFat: Int? = null,
    val muscleMass: Int? = null,
    val water: Int? = null,
    val bmi: Int? = null,
    val boneMass: Int? = null,
    val impedance: Int? = null,
    val unit: String? = null,
    // Advanced R4 body-composition metrics (12-metric scales). Mirror the legacy
    // /v3/operation/r4/ field names carried by ScaleApiEntry so a full 12-metric manual/scale
    // reading survives the /v3/entries round-trip instead of being dropped on sync. (MOB-1496 follow-up)
    val visceralFatLevel: Int? = null,
    val subcutaneousFatPercent: Int? = null,
    val proteinPercent: Int? = null,
    val skeletalMusclePercent: Int? = null,
    val bmr: Int? = null,
    val metabolicAge: Int? = null,

    // ── bp ──
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val note: String? = null,

    // ── baby (MOB-381 / §2.16): weight & measureLength are separate entryTypes ──
    val babyId: String? = null,
    // Client-generated idempotency key — REQUIRED for baby create/edit (§2.16). Stable
    // across retries so a re-sent batch doesn't duplicate the entry server-side.
    val entryId: String? = null,
    val entryType: String? = null,
    val babyWeightDecigrams: Int? = null,
    val babyLengthMillimeters: Int? = null,
    val entryNote: String? = null,

    // ── shared (weight optional / bp required) ──
    val pulse: Int? = null,
    val source: String? = null,
)
