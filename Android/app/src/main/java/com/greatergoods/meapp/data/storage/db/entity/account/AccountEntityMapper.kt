package com.greatergoods.meapp.data.storage.db.entity.account

import com.greatergoods.meapp.domain.model.Account

/**
 * Mapper object for converting between [AccountEntity] and domain [Account].
 */
object AccountEntityMapper {
    /**
     * Maps a domain [Account] to a database [AccountEntity].
     */
    fun toEntity(account: Account): AccountEntity = AccountEntity(
        id = account.id,
        firstName = account.firstName,
        lastName = account.lastName,
        dob = account.dob,
        email = account.email,
        expiresAt = account.expiresAt,
        fcmToken = account.fcmToken,
        gender = account.gender,
        isActiveAccount = account.isActiveAccount,
        isLoggedIn = account.isLoggedIn,
        isExpired = account.isExpired,
        isSynced = account.isSynced,
        lastActiveTime = account.lastActiveTime,
        zipcode = account.zipcode
    )

    /**
     * Maps a database [AccountEntity] to a domain [Account].
     */
    fun toDomain(entity: AccountEntity): Account = Account(
        id = entity.id,
        firstName = entity.firstName,
        lastName = entity.lastName,
        dob = entity.dob,
        email = entity.email,
        expiresAt = entity.expiresAt,
        fcmToken = entity.fcmToken,
        gender = entity.gender,
        isActiveAccount = entity.isActiveAccount,
        isLoggedIn = entity.isLoggedIn,
        isExpired = entity.isExpired,
        isSynced = entity.isSynced,
        lastActiveTime = entity.lastActiveTime,
        zipcode = entity.zipcode
    )
} 