package com.dmdbrands.gurus.weight.domain.model.common

data class BabyProfile(
    val id: String,
    val accountId: String,
    val name: String,
    val birthDate: Long? = null,
    val biologicalSex: String? = null,
    val birthWeightDecigrams: Int? = null,
    val birthLengthMillimeters: Int? = null,
    val isBorn: Boolean? = null,
    val isOwnedByAccount: Boolean? = null,
    val babyPermissions: Int? = null,
    val createdAt: Long? = null,
)
