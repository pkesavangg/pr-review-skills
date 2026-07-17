package com.dmdbrands.gurus.weight.data.storage.db.entity.baby

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity

@Entity(
    tableName = "baby",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
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
    // True once the baby has been created on the server at least once. An offline-created baby
    // starts false (its babyId is a client UUID) and flips to true after the create POST +
    // id-remap. Drives POST-vs-PUT at sync time and guards refresh()'s reconcile-delete from
    // wiping a not-yet-synced offline baby (MOB-1476).
    val existsOnServer: Boolean = false,
    val activeBabyId: String? = null,
)
