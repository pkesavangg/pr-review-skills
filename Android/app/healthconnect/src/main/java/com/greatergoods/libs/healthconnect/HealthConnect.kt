package com.greatergoods.libs.healthconnect

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
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
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.interfaces.IHealthConnect
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.reflect.KClass
import android.app.Activity
import android.content.Intent
import android.util.Log


/**
 * Default implementation of [com.greater goods.libs.health connect.interfaces.IHealthConnect] for interacting with Health Connect APIs.
 * @param activity Application context.
 */
class HealthConnect(
    private val activity: ComponentActivity,
) : IHealthConnect {

    companion object {
        private const val TAG = "HealthConnect"

        // Intent actions for privacy policy handling
        const val ACTION_SHOW_PERMISSIONS_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
        const val ACTION_VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"

        // Health Connect max chunk size is 5MB. 500 records per batch is a safe limit
        // to stay well within the threshold even with all metrics populated.
        private const val HEALTH_CONNECT_CHUNK_SIZE = 500
    }

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.Companion.getOrCreate(activity)
    }

    // Permission handling properties
    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>
    private lateinit var permissionList: Set<String>
    private val callbackTime = 1000L // Callback timeout for privacy policy link handling

    // Permission request callback
    private var authorizationCallback: ((HealthConnectRequestStatus) -> Unit)? = null

    init {
        load()
    }

    /**
     * Loads and initializes Health Connect with permission handling.
     * This method sets up the ActivityResultLauncher for permission requests.
     * Must be called before requesting permissions.
     */
    private fun load() {
        if (HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE ||
            HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {

            try {
                val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
                requestPermissions = activity.registerForActivityResult(requestPermissionActivityContract) { grantedPermissions ->
                    activity.lifecycleScope.launch {
                        try {
                            val hasAnyPermission = hasAnyPermissions(grantedPermissions)

                            val result = when {
                                grantedPermissions.intersect(permissionList).isNotEmpty() -> {
                                    HealthConnectRequestStatus.CONNECTED
                                }
                                hasAnyPermission -> {
                                    HealthConnectRequestStatus.PARTIAL
                                }
                                else -> {
                                    HealthConnectRequestStatus.CANCELLED
                                }
                            }
                            delay(callbackTime)
                            sendAuthorizationStatus(result)

                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process permission result", e)
                            sendAuthorizationStatus(HealthConnectRequestStatus.CANCELLED)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register for activity result", e)
            }
        }
    }

    /**
     * Sets up permission handling with ActivityResultLauncher.
     */
    override suspend fun requestAuthorization(
        options: HealthConnectOptions,
        callback: (HealthConnectRequestStatus) -> Unit
    ) {
        try {
            authorizationCallback = callback
            permissionList = buildPermissionSet(options)
            val isPermissionGranted = hasAllPermissions(permissionList)
            if (isPermissionGranted) {
                sendAuthorizationStatus(HealthConnectRequestStatus.CONNECTED)
            } else {
                requestPermissions.launch(permissionList)
            }

        } catch (e: Exception) {
            callback(HealthConnectRequestStatus.CANCELLED)
        }
    }

    /**
     * Sends authorization status to callback (same as Capacitor plugin's sendAuthorizationStatus).
     */
    private fun sendAuthorizationStatus(status: HealthConnectRequestStatus) {
        authorizationCallback?.let { callback ->
            callback(status)
            authorizationCallback = null // Clear callback after use
        }
    }

    /**
     * Handles new intents for privacy policy and permissions rationale.
     * This should be called from the app's activity when receiving new intents.
     * The actual intent filters are declared in the app's manifest.
     *
     * @param intent The new intent to handle
     */
    override fun handleOnNewIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_PERMISSIONS_RATIONALE,
            ACTION_VIEW_PERMISSION_USAGE -> {
                authorizationCallback?.let { callback ->
                  sendAuthorizationStatus(HealthConnectRequestStatus.PRIVACY_POLICY)
                    // Don't clear the callback here as the user might return to the permission flow
                }
            }
        }
    }

    /**
     * Deletes all health data for the given options.
     */
    override suspend fun deleteAllData(options: HealthConnectOptions): HealthConnectResult<Unit> =
        try {
            val now = Instant.now()
            val start = now.minusSeconds(365 * 100 * 24 * 60 * 60L) // 100 years
            val end = now
            val allTypes = options.writeTypes.toSet()
            allTypes.forEach { type ->
                val recordType = getRecordKClass(type)
                if (recordType != null) {
                    healthConnectClient.deleteRecords(
                        recordType,
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
                        recordType,
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
              TAG,
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
    override fun getRequestedPermissions(options: HealthConnectOptions): Set<String> {
        return buildPermissionSet(options)
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
        val status = HealthConnectClient.Companion.getSdkStatus(activity)
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
     * Checks if Health Connect is installed (SDK_AVAILABLE).
     */
    override fun isAppInstalled(): Boolean =
        HealthConnectClient.Companion.getSdkStatus(activity) == HealthConnectClient.Companion.SDK_AVAILABLE

    /**
     * Checks if Health Connect is available on the device.
     */
    override suspend fun isAvailable(): Boolean {
        val status = HealthConnectClient.Companion.getSdkStatus(activity)
        return status != HealthConnectClient.Companion.SDK_UNAVAILABLE
    }

    /**
     * Checks if a specific package is installed (by package name).
     * @param packageName The package name to check.
     */
    override fun isHealthConnectInstalled(packageName: String): Boolean =
        try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
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
     * Records are inserted in chunks to stay within Health Connect's 5MB per-chunk limit.
     */
    override suspend fun saveData(data: List<HealthConnectData>): HealthConnectResult<Unit> =
        try {
            val records = data.mapNotNull { mapToRecord(it) }
            records.chunked(HEALTH_CONNECT_CHUNK_SIZE).forEach { chunk ->
                healthConnectClient.insertRecords(chunk)
            }
            HealthConnectResult.Success(Unit)
        } catch (e: Exception) {
            HealthConnectResult.Error(e)
        }

    /**
     * Launches the Health Connect app or settings screen. If not installed, optionally opens the Play Store.
     * @param activity The Activity context to use for launching the intent.
     * @param forcePlayStore If true, always open the Play Store even if installed.
     * @return true if an intent was started, false otherwise.
     */
    override fun launchHealthConnect(
        activity: Activity,
        forcePlayStore: Boolean,
    ): Boolean =
        try {
            val isInstalled = isAppInstalled()
            if (isInstalled && !forcePlayStore) {
                val intent =
                  Intent(
                      HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS,
                  )
                activity.startActivity(intent)
                true
            } else {
                val providerPackageName = HealthConnectConfig.HealthConnectPackageName
                val uriString =
                    "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = uriString.toUri()
                    }
                activity.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Health Connect: ${e.message}")
            false
        }

    // --- Private/helper functions (following Capacitor plugin pattern) ---

    /**
     * Helper to build the set of Health Connect permission strings from options.
     * Same as Capacitor plugin's getPermissions() method.
     */
    private fun buildPermissionSet(options: HealthConnectOptions): Set<String> {
        val writePermissions = options.writeTypes.mapNotNull { dataType ->
            getRecordKClass(dataType)?.let { recordClass ->
                HealthPermission.getWritePermission(recordClass)
            }
        }
        return writePermissions.toSet()
    }

    /**
     * Checks if all permissions are granted (same as Capacitor plugin's hasPermissions).
     */
    override suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Get the Record class for a Health Connect Record type from DataType.
     */
    private fun getRecordKClass(type: DataType): KClass<out Record>? =
        when (type) {
            DataType.BasalMetabolicRate -> BasalMetabolicRateRecord::class
            DataType.BloodPressure -> BloodPressureRecord::class
            DataType.RestingHeartRate -> RestingHeartRateRecord::class
            DataType.BodyFat -> BodyFatRecord::class
            DataType.BodyWaterMass -> BodyWaterMassRecord::class
            DataType.BoneMass -> BoneMassRecord::class
            DataType.Height -> HeightRecord::class
            DataType.LeanBodyMass -> LeanBodyMassRecord::class
            DataType.Weight -> WeightRecord::class
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
                                      clientRecordId = time.epochSecond.toString()
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString()
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
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
                                    clientRecordId = time.epochSecond.toString(),
                                ),
                        ) as Record
                    }
            }
        } catch (e: Exception) {
            null
        }
}
