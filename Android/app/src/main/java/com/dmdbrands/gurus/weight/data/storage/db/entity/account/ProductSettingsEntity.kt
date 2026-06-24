package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Account-level product settings (Phase 2 / MOB-377): the products the account owns
 * and its measurement system. Mirrors the other per-account settings relation tables.
 *
 * @property productTypes API product values (`weight`/`blood_pressure`/`baby`); stored as
 *   JSON via the shared [com.dmdbrands.gurus.weight.data.storage.db.converter.JsonConverter].
 * @property measurementUnits One of `metric` / `imperialLbOz` / `imperialLbDecimal`.
 */
@Entity(
    tableName = "product_settings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProductSettingsEntity(
    @PrimaryKey
    val accountId: String,
    val productTypes: List<String>,
    val measurementUnits: String,
    val isSynced: Boolean,
)
