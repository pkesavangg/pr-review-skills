package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Wrapper class that represents an account with all its associated settings.
 * This class is used to handle the one-to-one relationships between an account
 * and its various settings entities.
 */
data class AccountSettings(
    @Embedded val account: AccountEntity,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val weightCompSettings: WeightCompSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val goalSettings: GoalSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val streaksSettings: StreaksSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val weightlessSettings: WeightlessSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val notificationSettings: NotificationSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val dashboardSettings: DashboardSettingsEntity?,
    
    @Relation(
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val integrationsSettings: IntegrationsSettingsEntity?
)