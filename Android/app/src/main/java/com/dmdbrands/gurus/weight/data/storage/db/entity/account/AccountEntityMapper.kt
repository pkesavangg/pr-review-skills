package com.dmdbrands.gurus.weight.data.storage.db.entity.account

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account as DomainAccount

/**
 * Mapper object for converting between [AccountEntity] and domain [Account].
 * Provides comprehensive mapping functionality including DTO-like operations.
 */
object AccountEntityMapper {
    /**
     * Maps a domain [Account] to a database [AccountEntity].
     */
    fun toEntity(account: DomainAccount): AccountEntity =
        AccountEntity(
            id = account.id,
            firstName = account.firstName,
            lastName = account.lastName,
            // Persist "" for an unset dob so the Room column stays non-null (MOB-591).
            dob = account.dob ?: "",
            email = account.email,
            expiresAt = account.expiresAt,
            fcmToken = account.fcmToken,
            // Persist "" for an unset gender so the Room column stays non-null (MOB-591).
            gender = account.gender ?: "",
            isActiveAccount = account.isActiveAccount,
            isLoggedIn = account.isLoggedIn,
            isExpired = account.isExpired,
            isSynced = account.isSynced,
            lastActiveTime = account.lastActiveTime,
            zipcode = account.zipcode,
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
            weightUnit = WeightUnit.from(accountWithRelations.weightCompSettings?.weightUnit),
            isWeightlessOn = accountWithRelations.weightlessSettings?.isWeightlessOn ?: false,
            height = accountWithRelations.weightCompSettings?.height,
            activityLevel = accountWithRelations.weightCompSettings?.activityLevel,
            weightlessTimestamp = accountWithRelations.weightlessSettings?.weightlessTimestamp,
            weightlessWeight = accountWithRelations.weightlessSettings?.weightlessWeight,
            isStreakOn = accountWithRelations.streaksSettings?.isStreakOn ?: false,
            streakTimestamp = accountWithRelations.streaksSettings?.streakTimestamp,
            dashboardType = accountWithRelations.dashboardSettings?.dashboardType
              ?: DashboardType.DASHBOARD_4_METRICS.value,
            dashboardMetrics =
                accountWithRelations.dashboardSettings
                    ?.dashboardMetrics
                    ?: emptyList(),
            // Map notification settings
            shouldSendEntryNotifications =
                accountWithRelations.notificationSettings?.shouldSendEntryNotifications
                    ?: false,
            shouldSendWeightInEntryNotifications =
                accountWithRelations.notificationSettings?.shouldSendWeightInEntryNotifications
                    ?: false,
            goalType = accountWithRelations.goalSettings?.goalType,
            initialWeight = accountWithRelations.goalSettings?.weight?.toDouble() ?: 0.0,
            goalWeight = accountWithRelations.goalSettings?.goalWeight?.toDoubleOrNull() ?: 0.0,
            goalPercent = accountWithRelations.goalSettings?.goalPercent?.toDouble() ?: 0.0,
            isFitbitOn = accountWithRelations.integrationsSettings?.isFitbitOn ?: false,
            isFitbitValid = accountWithRelations.integrationsSettings?.isFitbitValid ?: false,
            isHealthConnectOn = accountWithRelations.integrationsSettings?.isHealthConnectOn ?: false,
            isHealthKitOn = accountWithRelations.integrationsSettings?.isHealthKitOn ?: false,
            isMFPOn = accountWithRelations.integrationsSettings?.isMFPOn ?: false,
            isMFPValid = accountWithRelations.integrationsSettings?.isMFPValid ?: false,
            // Phase 2 (MOB-377): product settings
            productTypes = accountWithRelations.productSettings?.productTypes
                ?: listOf(ProductType.MY_WEIGHT.apiValue),
            measurementUnits = MeasurementUnits.fromValue(accountWithRelations.productSettings?.measurementUnits),
        )
    }
}
