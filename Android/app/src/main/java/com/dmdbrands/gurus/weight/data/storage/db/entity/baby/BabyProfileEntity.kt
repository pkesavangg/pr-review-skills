package com.dmdbrands.gurus.weight.data.storage.db.entity.baby

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "baby_profiles",
    indices = [
        Index(value = ["accountId"]),
    ],
)
data class BabyProfileEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val name: String,
    val isOwnedByAccount: Boolean? = null,
    val babyPermissions: Int? = null,
    val birthDate: Long? = null,
    val dueDate: Long? = null,
    val isBorn: Boolean? = null,
    val biologicalSex: String? = null,
    val birthWeightDecigrams: Int? = null,
    val birthLengthMillimeters: Int? = null,
    val lastUpdated: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val activeBabyId: String? = null,
)
