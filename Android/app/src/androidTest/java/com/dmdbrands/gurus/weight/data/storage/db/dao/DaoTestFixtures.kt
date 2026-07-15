package com.dmdbrands.gurus.weight.data.storage.db.dao

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.StreaksSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.BodyScaleEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.BpmEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceMetaDataEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.R4ScalePreferenceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.log.LogEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics

object DaoTestFixtures {

    // -------------------------------------------------------------------------
    // Account
    // -------------------------------------------------------------------------

    fun account(
        id: String = "acc-1",
        firstName: String = "John",
        lastName: String = "Doe",
        dob: String = "1990-01-01",
        email: String = "john@example.com",
        expiresAt: String? = null,
        fcmToken: String? = null,
        gender: String = "male",
        isActiveAccount: Boolean = true,
        isLoggedIn: Boolean = true,
        isExpired: Boolean = false,
        isSynced: Boolean = false,
        lastActiveTime: String? = null,
        zipcode: String = "12345",
    ) = AccountEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dob = dob,
        email = email,
        expiresAt = expiresAt,
        fcmToken = fcmToken,
        gender = gender,
        isActiveAccount = isActiveAccount,
        isLoggedIn = isLoggedIn,
        isExpired = isExpired,
        isSynced = isSynced,
        lastActiveTime = lastActiveTime,
        zipcode = zipcode,
    )

    fun weightCompSettings(
        accountId: String = "acc-1",
        height: Int = 170,
        activityLevel: String = "normal",
        weightUnit: String = "lb",
        isSynced: Boolean = true,
    ) = WeightCompSettingsEntity(
        accountId = accountId,
        height = height,
        activityLevel = activityLevel,
        weightUnit = weightUnit,
        isSynced = isSynced,
    )

    fun goalSettings(
        accountId: String = "acc-1",
        goalType: String? = "lose",
        weight: Float = 180f,
        goalWeight: String = "160",
        goalPercent: Float = 0f,
        isSynced: Boolean = true,
    ) = GoalSettingsEntity(
        accountId = accountId,
        goalType = goalType,
        weight = weight,
        goalWeight = goalWeight,
        goalPercent = goalPercent,
        isSynced = isSynced,
    )

    fun streaksSettings(
        accountId: String = "acc-1",
        isStreakOn: Boolean = true,
        streakTimestamp: String = "2025-01-01T00:00:00.000Z",
        isSynced: Boolean = true,
    ) = StreaksSettingsEntity(
        accountId = accountId,
        isStreakOn = isStreakOn,
        streakTimestamp = streakTimestamp,
        isSynced = isSynced,
    )

    fun weightlessSettings(
        accountId: String = "acc-1",
        isWeightlessOn: Boolean = false,
        weightlessTimestamp: String = "",
        weightlessWeight: Float = 0f,
        isSynced: Boolean = true,
    ) = WeightlessSettingsEntity(
        accountId = accountId,
        isWeightlessOn = isWeightlessOn,
        weightlessTimestamp = weightlessTimestamp,
        weightlessWeight = weightlessWeight,
        isSynced = isSynced,
    )

    fun notificationSettings(
        accountId: String = "acc-1",
        shouldSendEntryNotifications: Boolean = true,
        shouldSendWeightInEntryNotifications: Boolean = true,
        isSynced: Boolean = true,
    ) = NotificationSettingsEntity(
        accountId = accountId,
        shouldSendEntryNotifications = shouldSendEntryNotifications,
        shouldSendWeightInEntryNotifications = shouldSendWeightInEntryNotifications,
        isSynced = isSynced,
    )

    fun dashboardSettings(
        accountId: String = "acc-1",
        dashboardMetrics: List<String> = listOf("weight", "bmi"),
        dashboardMilestones: List<String> = listOf("goal"),
        dashboardType: String = "standard",
        isSynced: Boolean = true,
    ) = DashboardSettingsEntity(
        accountId = accountId,
        dashboardMetrics = dashboardMetrics,
        dashboardMilestones = dashboardMilestones,
        dashboardType = dashboardType,
        isSynced = isSynced,
    )

    fun integrationsSettings(
        accountId: String = "acc-1",
        isFitbitOn: Boolean = false,
        isFitbitValid: Boolean = false,
        isHealthConnectOn: Boolean = false,
        isHealthKitOn: Boolean = false,
        isMFPOn: Boolean = false,
        isMFPValid: Boolean = false,
        isSynced: Boolean = true,
    ) = IntegrationsSettingsEntity(
        accountId = accountId,
        isFitbitOn = isFitbitOn,
        isFitbitValid = isFitbitValid,
        isHealthConnectOn = isHealthConnectOn,
        isHealthKitOn = isHealthKitOn,
        isMFPOn = isMFPOn,
        isMFPValid = isMFPValid,
        isSynced = isSynced,
    )

    /**
     * Inserts an account entity with all 7 settings in one call.
     * Simplifies FK-dependent test setup.
     */
    suspend fun AccountDao.insertFullAccount(
        accountEntity: AccountEntity = account(),
    ) {
        insertAccount(accountEntity)
        insertWeightCompSettings(weightCompSettings(accountId = accountEntity.id))
        insertGoalSettings(goalSettings(accountId = accountEntity.id))
        insertStreaksSettings(streaksSettings(accountId = accountEntity.id))
        insertWeightlessSettings(weightlessSettings(accountId = accountEntity.id))
        insertNotificationSettings(notificationSettings(accountId = accountEntity.id))
        insertDashboardSettings(dashboardSettings(accountId = accountEntity.id))
        insertIntegrationsSettings(integrationsSettings(accountId = accountEntity.id))
    }

    // -------------------------------------------------------------------------
    // Device
    // -------------------------------------------------------------------------

    fun device(
        id: String = "dev-1",
        accountId: String = "acc-1",
        peripheralIdentifier: String? = "peripheral-1",
        nickname: String? = "My Scale",
        sku: String? = "SKU-001",
        mac: String? = "AA:BB:CC:DD:EE:FF",
        password: Long? = null,
        isDeleted: Boolean = false,
        deviceName: String? = "Weight Gurus Scale",
        deviceType: String? = "scale",
        broadcastId: Long? = 12345L,
        broadcastIdString: String? = "broadcast-1",
        userNumber: String? = "1",
        protocolType: String? = "ble",
        createdAt: String? = "2025-01-01T00:00:00.000Z",
        isSynced: Boolean = false,
        hasServerID: Boolean = false,
        token: String? = null,
    ) = DeviceEntity(
        id = id,
        accountId = accountId,
        peripheralIdentifier = peripheralIdentifier,
        nickname = nickname,
        sku = sku,
        mac = mac,
        password = password,
        isDeleted = isDeleted,
        deviceName = deviceName,
        deviceType = deviceType,
        broadcastId = broadcastId,
        broadcastIdString = broadcastIdString,
        userNumber = userNumber,
        protocolType = protocolType,
        createdAt = createdAt,
        isSynced = isSynced,
        hasServerID = hasServerID,
        token = token,
    )

    fun bodyScale(
        id: String = "dev-1",
        scaleType: String? = "body_scale",
        bodyComp: Boolean = true,
        isWeighOnlyModeEnabledByOthers: Boolean = false,
    ) = BodyScaleEntity(
        id = id,
        scaleType = scaleType,
        bodyComp = bodyComp,
        isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers,
    )

    fun bpmDevice(
        id: String = "dev-1",
        hasNumericUsers: Boolean = false,
    ) = BpmEntity(
        id = id,
        hasNumericUsers = hasNumericUsers,
    )

    fun deviceMeta(
        id: String = "dev-1",
        modelNumber: String? = "Model-A",
        serialNumber: String? = "SN-123",
        firmwareRevision: String? = "1.0.0",
        hardwareRevision: String? = "2.0",
        softwareRevision: String? = "1.0",
        manufacturerName: String? = "DMD Brands",
        systemId: String? = "sys-1",
        latestVersion: String? = "1.0.0",
    ) = DeviceMetaDataEntity(
        id = id,
        modelNumber = modelNumber,
        serialNumber = serialNumber,
        firmwareRevision = firmwareRevision,
        hardwareRevision = hardwareRevision,
        softwareRevision = softwareRevision,
        manufacturerName = manufacturerName,
        systemId = systemId,
        latestVersion = latestVersion,
    )

    fun r4Preference(
        id: String = "dev-1",
        displayName: String? = "R4 Scale",
        displayMetrics: List<String>? = listOf("weight", "bmi"),
        shouldFactoryReset: Boolean = false,
        shouldMeasureImpedance: Boolean = true,
        shouldMeasurePulse: Boolean = false,
        timeFormat: String? = "12h",
        tzOffset: Int? = -5,
        wifiFotaScheduleTime: Int? = null,
        isSynced: Boolean = false,
    ) = R4ScalePreferenceEntity(
        id = id,
        displayName = displayName,
        displayMetrics = displayMetrics,
        shouldFactoryReset = shouldFactoryReset,
        shouldMeasureImpedance = shouldMeasureImpedance,
        shouldMeasurePulse = shouldMeasurePulse,
        timeFormat = timeFormat,
        tzOffset = tzOffset,
        wifiFotaScheduleTime = wifiFotaScheduleTime,
        isSynced = isSynced,
    )

    fun deviceDetails(
        id: String = "dev-1",
        accountId: String = "acc-1",
        includeScale: Boolean = true,
        includeBpm: Boolean = false,
        includeMeta: Boolean = true,
        includeR4: Boolean = false,
    ) = DeviceDetails(
        device = device(id = id, accountId = accountId),
        scale = if (includeScale) bodyScale(id = id) else null,
        bpm = if (includeBpm) bpmDevice(id = id) else null,
        meta = if (includeMeta) deviceMeta(id = id) else null,
        r4Preference = if (includeR4) r4Preference(id = id) else null,
    )

    // -------------------------------------------------------------------------
    // Entry
    // -------------------------------------------------------------------------

    fun entryEntity(
        id: Long = 0L,
        accountId: String = "acc-1",
        entryTimestamp: String = "2025-06-15T12:00:00.000Z",
        serverTimestamp: String? = null,
        opTimestamp: String? = null,
        operationType: String = "create",
        deviceType: String = "scale",
        deviceId: String = "dev-1",
        attempts: Int = 0,
        unit: WeightUnit = WeightUnit.LB,
        isSynced: Boolean = false,
    ) = EntryEntity(
        id = id,
        accountId = accountId,
        entryTimestamp = entryTimestamp,
        serverTimestamp = serverTimestamp,
        opTimestamp = opTimestamp,
        operationType = operationType,
        deviceType = deviceType,
        deviceId = deviceId,
        attempts = attempts,
        unit = unit,
        isSynced = isSynced,
    )

    fun bodyScaleEntry(
        id: Long = 0L,
        weight: Double = 180.0,
        bodyFat: Double? = 20.0,
        muscleMass: Double? = 140.0,
        water: Double? = 55.0,
        bmi: Double? = 25.0,
        source: String? = "scale",
    ) = BodyScaleEntryEntity(
        id = id,
        weight = weight,
        bodyFat = bodyFat,
        muscleMass = muscleMass,
        water = water,
        bmi = bmi,
        source = source,
    )

    fun bodyScaleMetric(
        id: Long = 0L,
        bmr: Double? = 1800.0,
        metabolicAge: Int? = 30,
        proteinPercent: Double? = 16.0,
        pulse: Int? = 72,
        skeletalMusclePercent: Double? = 35.0,
        subcutaneousFatPercent: Double? = 18.0,
        visceralFatLevel: Double? = 8.0,
        boneMass: Double? = 3.5,
        impedance: Int? = 500,
    ) = BodyScaleEntryMetricEntity(
        id = id,
        bmr = bmr,
        metabolicAge = metabolicAge,
        proteinPercent = proteinPercent,
        pulse = pulse,
        skeletalMusclePercent = skeletalMusclePercent,
        subcutaneousFatPercent = subcutaneousFatPercent,
        visceralFatLevel = visceralFatLevel,
        boneMass = boneMass,
        impedance = impedance,
    )

    fun bpmEntryEntity(
        id: Long = 0L,
        systolic: Int = 120,
        diastolic: Int = 80,
        pulse: Int = 72,
        meanArterial: String = "93",
        note: String? = null,
    ) = BpmEntryEntity(
        id = id,
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
        meanArterial = meanArterial,
        note = note,
    )

    /**
     * Creates a [ScaleEntry] domain model suitable for [EntryDao.insert].
     */
    fun scaleEntry(
        id: Long = 0L,
        accountId: String = "acc-1",
        entryTimestamp: String = "2025-06-15T12:00:00.000Z",
        operationType: String = "create",
        weight: Double = 180.0,
        isSynced: Boolean = false,
        includeMetrics: Boolean = true,
    ) = ScaleEntry(
        entry = entryEntity(
            id = id,
            accountId = accountId,
            entryTimestamp = entryTimestamp,
            operationType = operationType,
            isSynced = isSynced,
        ),
        scale = ScaleEntryWithMetrics(
            scaleEntry = bodyScaleEntry(id = id, weight = weight),
            scaleEntryMetric = if (includeMetrics) bodyScaleMetric(id = id) else null,
        ),
    )

    /**
     * Creates a [BpmEntry] domain model suitable for [EntryDao.insert].
     */
    fun bpmEntry(
        id: Long = 0L,
        accountId: String = "acc-1",
        entryTimestamp: String = "2025-06-15T12:00:00.000Z",
        operationType: String = "create",
        isSynced: Boolean = false,
    ) = BpmEntry(
        entry = entryEntity(
            id = id,
            accountId = accountId,
            entryTimestamp = entryTimestamp,
            operationType = operationType,
            deviceType = "bpm",
            isSynced = isSynced,
        ),
        bpmEntry = bpmEntryEntity(id = id),
    )

    // -------------------------------------------------------------------------
    // Log
    // -------------------------------------------------------------------------

    fun logEntity(
        id: String = "log-1",
        accountId: String = "acc-1",
        sessionId: String = "session-1",
        tag: String = "TestTag",
        tagId: String = "testMethod",
        type: String = "i",
        message: String = "Test log message",
        timestamp: Long = 1000L,
        data: String? = null,
    ) = LogEntity(
        id = id,
        accountId = accountId,
        sessionId = sessionId,
        tag = tag,
        tagId = tagId,
        type = type,
        message = message,
        timestamp = timestamp,
        data = data,
    )
}
