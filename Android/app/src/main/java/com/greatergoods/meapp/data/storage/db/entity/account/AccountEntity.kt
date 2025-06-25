package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing the account table in the database.
 * Stores user account details such as personal info, tokens, and app settings.
 */
@Entity(
    tableName = "account",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["isActiveAccount"]),
        Index(value = ["isLoggedIn"])
    ]
)
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "accountId")
    val id: String,
    val firstName: String,
    val lastName: String,
    val dob: String,
    val email: String,
    val expiresAt: String? = null,
    val fcmToken: String? = null,
    val gender: String,
    val isActiveAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isExpired: Boolean = false,
    val isSynced: Boolean = false,
    val lastActiveTime: String? = null,
    val zipcode: String
)
