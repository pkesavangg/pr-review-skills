package com.dmdbrands.gurus.weight.domain.model.api.baby

/**
 * Response body for the `/v3/baby/` endpoints (returned from POST / PUT, and as
 * list items from GET). Mirrors [BabyRequest] plus the server-assigned [id].
 */
data class BabyResponse(
    val id: String,
    val name: String,
    val birthdate: String? = null,
    val sex: String? = null,
    val birthWeightDecigrams: Int? = null,
    val birthLengthMillimeters: Int? = null,
)
