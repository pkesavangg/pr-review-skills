package com.greatergoods.meapp.data.storage.db.entity.account

import com.greatergoods.meapp.domain.model.storage.Account.Account as DomainAccount

/**
 * Mapper object for converting between [AccountEntity] and domain [Account].
 * Provides comprehensive mapping functionality including DTO-like operations.
 */
object AccountEntityMapper {
    /**
     * Maps a domain [Account] to a database [AccountEntity].
     */
    fun toEntity(account: DomainAccount): AccountEntity = AccountEntity(
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
     * Note: This mapper only handles basic AccountEntity properties.
     * For full Account with settings, use the toDomainFromAccountWithRelations method.
     */
    fun toDomain(entity: AccountEntity): DomainAccount = DomainAccount(
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
        zipcode = entity.zipcode,
        // Default values for settings (use toDomainFromAccountWithRelations for full mapping)
        weightUnit = null,
        isWeightlessOn = false,
        height = null,
        activityLevel = null,
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = null,
        dashboardMetrics = null
    )

    /**
     * Maps a database [Account] (with all relations) to a domain [Account].
     * This handles the full Account entity with all related settings.
     */
    fun toDomainFromAccountWithRelations(accountWithRelations: Account): DomainAccount {
        val entity = accountWithRelations.account
        return DomainAccount(
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
            zipcode = entity.zipcode,
            // Map from related entities
            weightUnit = accountWithRelations.dashboardSettings?.dashboardType,
            isWeightlessOn = accountWithRelations.weightlessSettings?.isWeightlessOn ?: false,
            height = accountWithRelations.weightCompSettings?.height,
            activityLevel = accountWithRelations.weightCompSettings?.activityLevel,
            weightlessTimestamp = accountWithRelations.weightlessSettings?.weightlessTimestamp,
            weightlessWeight = accountWithRelations.weightlessSettings?.weightlessWeight,
            isStreakOn = accountWithRelations.streaksSettings?.isStreakOn ?: false,
            dashboardType = accountWithRelations.dashboardSettings?.dashboardType,
            dashboardMetrics = accountWithRelations.dashboardSettings?.dashboardMetrics?.split(",")
                ?.filterNot { it.isBlank() }
        )
    }
}
