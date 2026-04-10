package com.dmdbrands.gurus.weight.testutil

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics

/**
 * Shared test fixtures for unit tests across the project.
 *
 * Use the pre-built instances for the common cases, and the builder
 * functions (`anAccount`, `aWeightEntry`, etc.) when you need custom values.
 */
object TestFixtures {

    // -------------------------------------------------------------------------
    // Accounts — three canonical states
    // -------------------------------------------------------------------------

    /** The currently signed-in account managing the session. */
    val activeAccount: Account = anAccount(
        id = "active-account-id",
        email = "active@example.com",
        isActiveAccount = true,
        isLoggedIn = true,
    )

    /** A linked account that is signed in but not the current session owner. */
    val secondaryAccount: Account = anAccount(
        id = "secondary-account-id",
        email = "secondary@example.com",
        isActiveAccount = false,
        isLoggedIn = true,
    )

    /** An account that has been added but is not signed in. */
    val inactiveAccount: Account = anAccount(
        id = "inactive-account-id",
        email = "inactive@example.com",
        isActiveAccount = false,
        isLoggedIn = false,
    )

    fun anAccount(
        id: String = "test-account-id",
        firstName: String = "Test",
        lastName: String = "User",
        email: String = "test@example.com",
        weightUnit: WeightUnit = WeightUnit.LB,
        isActiveAccount: Boolean = false,
        isLoggedIn: Boolean = false,
        isSynced: Boolean = true,
    ): Account = Account(
        id = id,
        firstName = firstName,
        lastName = lastName,
        dob = "1990-01-01",
        email = email,
        gender = "male",
        weightUnit = weightUnit,
        zipcode = "12345",
        height = 1700,
        activityLevel = "normal",
        isActiveAccount = isActiveAccount,
        isLoggedIn = isLoggedIn,
        isSynced = isSynced,
    )

    // -------------------------------------------------------------------------
    // Entries — weight, body-fat, and blood-pressure
    // -------------------------------------------------------------------------

    /** A basic weight-only scale entry at 75.0 lbs. */
    val weightEntry: ScaleEntry = aWeightEntry()

    /** A scale entry with full body-composition metrics. */
    val bodyFatEntry: ScaleEntry = aWeightEntry(
        weight = 80.0,
        bodyFat = 22.5,
        muscleMass = 45.0,
        water = 55.0,
        bmi = 24.8,
    )

    /** A blood-pressure entry (120/80, pulse 72). */
    val bpmEntry: BpmEntry = aBpmEntry()

    fun aWeightEntry(
        accountId: String = "test-account-id",
        entryTimestamp: String = "2024-01-01T12:00:00.000Z",
        weight: Double = 75.0,
        bodyFat: Double? = null,
        muscleMass: Double? = null,
        water: Double? = null,
        bmi: Double? = null,
        unit: WeightUnit = WeightUnit.LB,
    ): ScaleEntry {
        val entryEntity = EntryEntity(
            id = 1L,
            accountId = accountId,
            entryTimestamp = entryTimestamp,
            operationType = "create",
            deviceType = "scale",
            deviceId = "device-scale-1",
            unit = unit,
        )
        val scaleEntryEntity = BodyScaleEntryEntity(
            id = 1L,
            weight = weight,
            bodyFat = bodyFat,
            muscleMass = muscleMass,
            water = water,
            bmi = bmi,
            source = null,
        )
        return ScaleEntry(
            entry = entryEntity,
            scale = ScaleEntryWithMetrics(
                scaleEntry = scaleEntryEntity,
                scaleEntryMetric = null,
            ),
        )
    }

    fun aBpmEntry(
        accountId: String = "test-account-id",
        entryTimestamp: String = "2024-01-01T12:00:00.000Z",
        systolic: Int = 120,
        diastolic: Int = 80,
        pulse: Int = 72,
    ): BpmEntry {
        val entryEntity = EntryEntity(
            id = 2L,
            accountId = accountId,
            entryTimestamp = entryTimestamp,
            operationType = "create",
            deviceType = "bpm",
            deviceId = "device-bpm-1",
        )
        val bpmEntryEntity = BpmEntryEntity(
            entryId = 2L,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            meanArterial = "${(systolic + 2 * diastolic) / 3}",
            note = null,
        )
        return BpmEntry(
            entry = entryEntity,
            bpmEntry = bpmEntryEntity,
        )
    }

    // -------------------------------------------------------------------------
    // Devices — BLE scale and WiFi scale
    // -------------------------------------------------------------------------

    /** A Bluetooth scale that has been paired and synced. */
    val bleDevice: Device = aDevice(
        id = "ble-device-id",
        deviceType = "bluetooth",
        nickname = "BLE Scale",
    )

    /** A WiFi-connected scale that has been paired and synced. */
    val wifiDevice: Device = aDevice(
        id = "wifi-device-id",
        deviceType = "wifi",
        nickname = "WiFi Scale",
    )

    fun aDevice(
        id: String = "test-device-id",
        deviceType: String = "bluetooth",
        nickname: String = "Test Scale",
        hasServerID: Boolean = true,
        isSynced: Boolean = true,
        isDeleted: Boolean = false,
    ): Device = Device(
        id = id,
        device = null,
        deviceType = deviceType,
        nickname = nickname,
        hasServerID = hasServerID,
        isSynced = isSynced,
        isDeleted = isDeleted,
    )
}
