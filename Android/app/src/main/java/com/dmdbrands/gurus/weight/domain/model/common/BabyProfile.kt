package com.dmdbrands.gurus.weight.domain.model.common

data class BabyProfile(
    val id: String,
    val accountId: String,
    val name: String,
    val birthdate: String? = null,
    val sex: String? = null,
    val birthWeightDecigrams: Int? = null,
    val birthLengthMillimeters: Int? = null,
    val isBorn: Boolean? = null,
    val isOwnedByAccount: Boolean? = null,
    val permissions: Int? = null,
    val createdAt: Long? = null,
    val dueDate: String? = null,
    val lastUpdated: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)
