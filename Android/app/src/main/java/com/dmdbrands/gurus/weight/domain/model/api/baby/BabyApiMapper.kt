package com.dmdbrands.gurus.weight.domain.model.api.baby

import com.dmdbrands.gurus.weight.domain.enums.BabySex
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile

/**
 * Extension functions mapping between the `/v3/baby/` API DTOs and the
 * [BabyProfile] domain model.
 */

/** Builds the API request body from a domain [BabyProfile]. */
fun BabyProfile.toRequest(): BabyRequest = BabyRequest(
    name = name,
    birthdate = birthdate,
    sex = sex?.let { BabySex.fromValue(it).value },
    birthWeightDecigrams = birthWeightDecigrams,
    birthLengthMillimeters = birthLengthMillimeters,
)

/**
 * Maps a server [BabyResponse] into a domain [BabyProfile] for the given
 * [accountId]. Profiles sourced from the server are marked [BabyProfile.isSynced].
 */
fun BabyResponse.toDomain(accountId: String): BabyProfile = BabyProfile(
    id = id,
    accountId = accountId,
    name = name,
    birthdate = birthdate,
    sex = sex,
    birthWeightDecigrams = birthWeightDecigrams,
    birthLengthMillimeters = birthLengthMillimeters,
    isSynced = true,
)

/** Maps a list of server [BabyResponse]s into domain [BabyProfile]s. */
fun List<BabyResponse>.toDomain(accountId: String): List<BabyProfile> =
    map { it.toDomain(accountId) }
