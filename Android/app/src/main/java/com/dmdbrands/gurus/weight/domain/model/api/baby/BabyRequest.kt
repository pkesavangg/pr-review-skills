package com.dmdbrands.gurus.weight.domain.model.api.baby

/**
 * Request body for `POST /v3/baby/` and `PUT /v3/baby/{babyId}`.
 *
 * Only the fields the existing Baby app actually uses are included (per the
 * Baby App audit). `name` is required; everything else is optional.
 */
data class BabyRequest(
    val name: String,
    val birthdate: String? = null,
    val sex: String? = null,
    val birthWeightDecigrams: Int? = null,
    val birthLengthMillimeters: Int? = null,
)
