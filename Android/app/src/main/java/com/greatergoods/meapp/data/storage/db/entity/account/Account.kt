package com.greatergoods.meapp.data.storage.db.entity.account

import androidx.room.Embedded
import androidx.room.Relation


/**
 * Wrapper class that represents an account with all its associated settings.
 * This class is used to handle the one-to-one relationships between an account
 * and its various settings entities.
 */
data class Account(
    @Embedded val account: AccountEntity,

    @Relation(
        entity = WeightCompSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val weightCompSettings: WeightCompSettingsEntity?,

    @Relation(
        entity = GoalSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val goalSettings: GoalSettingsEntity?,

    @Relation(
        entity = StreaksSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val streaksSettings: StreaksSettingsEntity?,

    @Relation(
        entity = WeightlessSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val weightlessSettings: WeightlessSettingsEntity?,

    @Relation(
        entity = NotificationSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val notificationSettings: NotificationSettingsEntity?,

    @Relation(
        entity = DashboardSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val dashboardSettings: DashboardSettingsEntity?,

    @Relation(
        entity = IntegrationsSettingsEntity::class,
        parentColumn = "accountId",
        entityColumn = "accountId"
    )
    val integrationsSettings: IntegrationsSettingsEntity?,

    )