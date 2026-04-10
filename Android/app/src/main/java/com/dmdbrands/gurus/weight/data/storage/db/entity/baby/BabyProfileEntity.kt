package com.dmdbrands.gurus.weight.data.storage.db.entity.baby

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "baby",
    indices = [
        Index(value = ["accountId"]),
    ],
)
data class BabyProfileEntity(
    @PrimaryKey
    val babyId: String,
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
    val activeBabyId: String? = null,
)
