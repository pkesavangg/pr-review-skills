package com.dmdbrands.gurus.weight.domain.model.api.entry

/**
 * A single entry returned by `GET /v3/entries/` (MOB-380). Shared by the cursor-
 * pagination and sync-mode responses. The canonical category-aware read DTO; the
 * write counterpart (`UnifiedEntryRequest`) lives in MOB-379 and reuses these enums.
 */
data class EntryApiModel(
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
    // Advanced R4 body-composition metrics (12-metric scales); read counterpart of the
    // UnifiedEntryRequest fields so a synced 12-metric reading keeps them. (MOB-1496 follow-up)
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

    // baby (§2.16): weight & measureLength are separate entryTypes
    val babyId: String? = null,
    val entryType: String? = null,
    val babyWeightDecigrams: Int? = null,
    val babyLengthMillimeters: Int? = null,
    val entryNote: String? = null,

    // shared
    val pulse: Int? = null,
    val source: String? = null,
)

/**
 * Response for cursor-pagination mode (`?cursor=&limit=`):
 * `{ entries: [...], nextCursor: ISO, hasMore: bool }`.
 */
data class EntriesCursorResponse(
    val entries: List<EntryApiModel>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/**
 * Response for sync mode (`?start=ISO`):
 * `{ entries: [...], timestamp: ISO }`.
 */
data class EntriesSyncResponse(
    val entries: List<EntryApiModel>,
    // Nullable: the server may omit the sync cursor (e.g. first sync with an empty start).
    // Guard before writing it to avoid a proto-setter NPE (MOB-591).
    val timestamp: String?,
)
