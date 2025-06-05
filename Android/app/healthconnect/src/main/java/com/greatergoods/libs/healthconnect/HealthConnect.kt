package com.greatergoods.libs.healthconnect

import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import com.dmdbrands.healthconnectplugin.config.HealthConnectConfig
import com.greatergoods.libs.healthconnect.enum.DataType
import com.greatergoods.libs.healthconnect.enum.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enum.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enum.HealthConnectStatus
import com.greatergoods.libs.healthconnect.interfaces.IHealthConnect
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import java.time.Instant
import kotlin.reflect.KClass
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Default implementation of [com.greatergoods.libs.healthconnect.interfaces.IHealthConnect] for interacting with Health Connect APIs.
 * @param context Application context.
 */
@RequiresApi(Build.VERSION_CODES.O)
class HealthConnect(
    private val context: Context,
) : IHealthConnect {
    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.Companion.getOrCreate(context)
    }
    private val Tag = "HealthConnect"

    // --- Public API (sorted in ascending order) ---

    /**
     * Deletes all health data for the given options.
     */
    override suspend fun deleteAllData(options: HealthConnectOptions): HealthConnectResult<Unit> =
        try {
            val now = Instant.now()
            val start = now.minusSeconds(365 * 100 * 24 * 60 * 60L) // 100 years
            val end = now
            val allTypes = (options.writeTypes + options.readTypes).toSet()
            allTypes.forEach { type ->
                val recordType = getRecordKClass(type)
                if (recordType != null) {
                    healthConnectClient.deleteRecords(
                        recordType.kotlin,
                        timeRangeFilter =
                            TimeRangeFilter.Companion
                                .between(start, end),
                    )
                }
            }
            HealthConnectResult.Success(Unit)
        } catch (e: Exception) {
            HealthConnectResult.Error(e)
        }

    /**
     * Deletes specific health data entries.
     */
    override suspend fun deleteEntry(data: List<HealthConnectData>): HealthConnectResult<Unit> =
        try {
            // For demo: delete by time range for each entry (real implementation may need record IDs)
            data.forEach { entry ->
                val recordType = getRecordKClass(entry.type)
                if (recordType != null) {
                    healthConnectClient.deleteRecords(
                        recordType.kotlin,
                        timeRangeFilter =
                            TimeRangeFilter.between(
                                entry.timeStamp.minusSeconds(1),
                                entry.timeStamp.plusSeconds(1),
                            ),
                    )
                }
            }
            HealthConnectResult.Success(Unit)
        } catch (e: Exception) {
            HealthConnectResult.Error(e)
        }

    /**
     * Deletes records by unique client record IDs for the given types.
     */
    override suspend fun deleteEntryByUniqueIdentifier(
        recordTypes: Set<KClass<out Record>>,
        clientRecordIdsList: List<String>,
    ) {
        try {
            for (recordType in recordTypes) {
                healthConnectClient.deleteRecords(
                    recordType = recordType,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = clientRecordIdsList,
                )
            }
        } catch (e: Exception) {
            // Log or handle error as needed
            Log.e(
                Tag,
                "Failed while deleting synced entry by using unique identifier: ${e.message}",
            )
            HealthConnectResult.Error(e)
        }
    }

    /**
     * Gets the list of currently approved Health Connect permissions.
     */
    override suspend fun getApprovedPermissionList(): Set<String> =
        healthConnectClient.permissionController.getGrantedPermissions()

    /**
     * Returns the set of permission strings for given options.
     */
    override fun getPermissions(options: HealthConnectOptions): Set<String> {
        val readPermission = convertToReadPermissions(options.readTypes)
        val writePermission = convertToWritePermissions(options.writeTypes)
        return readPermission + writePermission
    }

    /**
     * Gets the permission status for the given options.
     */
    override suspend fun getPermissionStatus(options: HealthConnectOptions): HealthConnectPermissionStatus =
        try {
            val allPermissions = buildPermissionSet(options)
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            when {
                granted.containsAll(allPermissions) -> HealthConnectPermissionStatus.ALL
                granted.intersect(allPermissions).isNotEmpty() -> HealthConnectPermissionStatus.PARTIAL
                else -> HealthConnectPermissionStatus.NONE
            }
        } catch (e: Exception) {
            HealthConnectPermissionStatus.NONE
        }

    /**
     * Gets the current Health Connect status on the device, distinguishing INSTALL_REQUIRED and UPDATE_REQUIRED.
     */
    override suspend fun getStatus(): HealthConnectStatus {
        val status = HealthConnectClient.Companion.getSdkStatus(context)
        val providerPackageName = HealthConnectConfig.HealthConnectPackageName // Default provider
        return when (status) {
            HealthConnectClient.Companion.SDK_UNAVAILABLE -> HealthConnectStatus.UNAVAILABLE
            HealthConnectClient.Companion.SDK_AVAILABLE -> HealthConnectStatus.INSTALLED
            HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                if (!isHealthConnectInstalled(providerPackageName)) {
                    HealthConnectStatus.INSTALL_REQUIRED
                } else {
                    HealthConnectStatus.UPDATE_REQUIRED
                }
            }

            else -> HealthConnectStatus.UNAVAILABLE
        }
    }

    /**
     * Checks if any permission in a set is granted.
     */
    override suspend fun hasAnyPermissions(permissions: Set<String>): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.intersect(permissions).isNotEmpty()
    }

    /**
     * Checks if all permissions in a set are granted.
     */
    override suspend fun hasPermissions(permissions: Set<String>): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Checks if Health Connect is installed (SDK_AVAILABLE).
     */
    override fun isAppInstalled(): Boolean =
        HealthConnectClient.Companion.getSdkStatus(context) == HealthConnectClient.Companion.SDK_AVAILABLE

    /**
     * Checks if Health Connect is available on the device.
     */
    override suspend fun isAvailable(): Boolean {
        val status = HealthConnectClient.Companion.getSdkStatus(context)
        return status != HealthConnectClient.Companion.SDK_UNAVAILABLE
    }

    /**
     * Checks if a specific package is installed (by package name).
     * @param packageName The package name to check.
     */
    override fun isHealthConnectInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }

    /**
     * Requests authorization for the given options.
     */
    override suspend fun requestAuthorization(options: HealthConnectOptions): HealthConnectRequestStatus =
        try {
            val allPermissions = buildPermissionSet(options)
            // In a real app, you must launch an Activity for result to request permissions.
            // Here, we simulate the check only (no UI flow in this repo-only implementation).
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            when {
                granted.containsAll(allPermissions) -> HealthConnectRequestStatus.CONNECTED
                granted.intersect(allPermissions).isNotEmpty() -> HealthConnectRequestStatus.PARTIAL
                else -> HealthConnectRequestStatus.CANCELLED
            }
        } catch (e: Exception) {
            Log.e("sdfsdf", e.toString())
            HealthConnectRequestStatus.CANCELLED
        }

    /**
     * Revokes all Health Connect permissions.
     */
    override suspend fun revokeAllPermissions(): HealthConnectResult<Unit> =
        try {
            healthConnectClient.permissionController.revokeAllPermissions()
            HealthConnectResult.Success(Unit)
        } catch (e: Exception) {
            HealthConnectResult.Error(e)
        }

    /**
     * Saves a list of health data entries.
     */
    override suspend fun saveData(data: List<HealthConnectData>): HealthConnectResult<Unit> =
        try {
            val records = data.mapNotNull { mapToRecord(it) }
            healthConnectClient.insertRecords(records)
            HealthConnectResult.Success(Unit)
        } catch (e: Exception) {
            HealthConnectResult.Error(e)
        }

    // --- Private/helper functions (sorted alphabetically) ---

    /**
     * Helper to build the set of Health Connect permission strings from options.
     */
    private fun buildPermissionSet(options: HealthConnectOptions): Set<String> {
        val write = options.writeTypes.map { "androidx.health.connect.permission.WRITE_${it.name.uppercase()}" }
        val read = options.readTypes.map { "androidx.health.connect.permission.READ_${it.name.uppercase()}" }
        return (write + read).toSet()
    }

    /**
     * Converts a set of DataType to read permission strings.
     */
    private fun convertToReadPermissions(types: Set<DataType>): Set<String> =
        types.map { "androidx.health.connect.permission.READ_${it.name.uppercase()}" }.toSet()

    /**
     * Converts a set of DataType to write permission strings.
     */
    private fun convertToWritePermissions(types: Set<DataType>): Set<String> =
        types.map { "androidx.health.connect.permission.WRITE_${it.name.uppercase()}" }.toSet()

    /**
     * Get the Record class for a Health Connect Record type from DataType.
     */
    private fun getRecordKClass(type: DataType): Class<out Record>? =
        when (type) {
            DataType.BasalMetabolicRate -> BasalMetabolicRateRecord::class.java
            DataType.BloodPressure -> BloodPressureRecord::class.java
            DataType.RestingHeartRate -> RestingHeartRateRecord::class.java
            DataType.BodyFat -> BodyFatRecord::class.java
            DataType.BodyWaterMass -> BodyWaterMassRecord::class.java
            DataType.BoneMass -> BoneMassRecord::class.java
            DataType.Height -> HeightRecord::class.java
            DataType.LeanBodyMass -> LeanBodyMassRecord::class.java
            DataType.Weight -> WeightRecord::class.java
        }

    /**
     * Map HealthConnectData to Health Connect Record for all supported types.
     */
    private fun mapToRecord(data: HealthConnectData): Record? =
        try {
            when (data.type) {
                DataType.BasalMetabolicRate ->
                    data.value?.let {
                        val time = data.timeStamp
                        BasalMetabolicRateRecord(
                            time = time,
                            zoneOffset = null,
                            basalMetabolicRate =
                                Power.Companion
                                    .kilocaloriesPerDay(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.BloodPressure ->
                    data.bloodPressure?.let {
                        val time = data.timeStamp
                        BloodPressureRecord(
                            systolic =
                                Pressure.Companion
                                    .millimetersOfMercury(it.systolic),
                            diastolic =
                                Pressure.Companion
                                    .millimetersOfMercury(it.diastolic),
                            time = time,
                            zoneOffset = null,
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_UNKNOWN),
                                ),
                        )
                    }

                DataType.RestingHeartRate ->
                    data.value?.let {
                        val time = data.timeStamp
                        RestingHeartRateRecord(
                            time = time,
                            zoneOffset = null,
                            beatsPerMinute = it.toLong(),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_UNKNOWN),
                                ),
                        )
                    }

                DataType.BodyFat ->
                    data.value?.let {
                        val time = data.timeStamp
                        BodyFatRecord(
                            time = time,
                            zoneOffset = null,
                            percentage =
                                Percentage(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.BodyWaterMass ->
                    data.value?.let {
                        val time = data.timeStamp
                        BodyWaterMassRecord(
                            time = time,
                            zoneOffset = null,
                            mass =
                                Mass
                                    .pounds(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.BoneMass ->
                    data.value?.let {
                        val time = data.timeStamp
                        BoneMassRecord(
                            time = time,
                            zoneOffset = null,
                            mass =
                                Mass
                                    .pounds(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.Height ->
                    data.value?.let {
                        val time = data.timeStamp
                        HeightRecord(
                            time = time,
                            zoneOffset = null,
                            height =
                                Length
                                    .feet(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.LeanBodyMass ->
                    data.value?.let {
                        val time = data.timeStamp
                        LeanBodyMassRecord(
                            time = time,
                            zoneOffset = null,
                            mass =
                                Mass
                                    .pounds(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }

                DataType.Weight ->
                    data.value?.let {
                        val time = data.timeStamp
                        WeightRecord(
                            time = time,
                            zoneOffset = null,
                            weight =
                                Mass
                                    .pounds(it),
                            metadata =
                                Metadata.Companion.autoRecorded(
                                    device = Device(type = Device.Companion.TYPE_SCALE),
                                ),
                        )
                    }
            }
        } catch (e: Exception) {
            null
        }
}
