// swiftlint:disable file_length
// This service intentionally aggregates all HealthKit integration logic
// to maintain a single source of truth for health data synchronization.
// Splitting would fragment the integration flow and reduce maintainability.

import Foundation
import ggHealthKitPackage
import HealthKit
import SwiftData
import SwiftUI

@MainActor
// swiftlint:disable:next type_body_length
final class HealthKitService: HealthKitServiceProtocol {
    static let shared = HealthKitService()
    private let integrationService: IntegrationServiceProtocol
    private let logger: LoggerServiceProtocol
    private let accountService: AccountServiceProtocol
    private let entryService: EntryServiceProtocol
    private let kvStore: KvStorageServiceProtocol
    private let hkPackage: HealthKitHandlerProtocol
    private let tag = "HealthKitService"
    private let addHKModalFlagKeyBase = KvStorageKeys.addAppleHealthModalBase
    /// Local storage flag indicating the *Finish Adding Apple Health* prompt has already been shown on this device.
    private let finishHKModalFlagKeyBase = KvStorageKeys.finishAppleHealthModalBase
    /// Local storage flag indicating the *Out of Sync* Apple Health prompt has already been shown on this device.
    private let outOfSyncHKModalFlagKeyBase = KvStorageKeys.outOfSyncAppleHealthModalBase
    /// Local storage flag indicating we're waiting for permissions to be restored after out-of-sync.
    private let waitingForHKPermissionsRestoredBase = KvStorageKeys.waitingForHKPermissionsRestoredBase

    /// MOB-405: Apple Health authorization is requested for the full data-type set
    /// (Weight Gurus + Balance Health) upfront at first integration, regardless of which device
    /// is paired first. This union drives both the initial permission request and the sync scope,
    /// so no incremental native prompts are shown when another device type is paired later.
    private let fullPermissionScopeDeviceTypes: Set<String> = [
        DeviceType.scale.rawValue,
        DeviceType.bpm.rawValue
    ]
    /// Number of Weight Gurus permissions (weight, lean body mass, body fat, BMI, heart rate).
    /// Permission counting always runs under the Weight Gurus app type — it covers the weight
    /// metrics plus heart rate, while blood pressure is written unconditionally during sync.
    private let weightGurusPermissionCount = 5

    // MARK: - Initialization

    init(
        integrationService: IntegrationServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        accountService: AccountServiceProtocol? = nil,
        entryService: EntryServiceProtocol? = nil,
        kvStore: KvStorageServiceProtocol? = nil,
        healthKitHandler: HealthKitHandlerProtocol? = nil
    ) {
        self.integrationService = integrationService ?? IntegrationsService.shared
        self.logger = logger ?? LoggerService.shared
        self.accountService = accountService ?? AccountService.shared
        self.entryService = entryService ?? EntryService.shared
        self.kvStore = kvStore ?? KvStorageService.shared
        self.hkPackage = healthKitHandler ?? AppleHealthHandlerAdapter()
    }

    // MARK: - Helpers
    private func getActiveAccountId() async -> String? {
        await MainActor.run {
            accountService.activeAccount?.accountId
        }
    }

    private func isHealthKitEnabledForActiveAccount() async -> Bool {
        await MainActor.run {
            accountService.activeAccount?.isHealthKitOn ?? false
        }
    }

    private func persistHealthKitPermissionScopeDeviceTypes(_ deviceTypes: Set<String>, for accountId: String) {
        let key = KvStorageKeys.healthKitPermissionScopeDeviceTypesKey(for: accountId)
        kvStore.setCodable(Array(deviceTypes).sorted(), forKey: key)
    }

    private func clearStoredHealthKitPermissionScopeDeviceTypes(for accountId: String?) {
        guard let accountId else { return }
        let key = KvStorageKeys.healthKitPermissionScopeDeviceTypesKey(for: accountId)
        kvStore.clearValue(forKey: key)
    }

    /// MOB-405: The permission scope is always the full data-type set — the user is authorized for
    /// Weight Gurus + Balance Health upfront, so sync covers every paired device type.
    private func getConfiguredHealthKitPermissionScopeDeviceTypes() async -> Set<String> {
        fullPermissionScopeDeviceTypes
    }

    private func updateAppType(for deviceTypes: Set<String>, context: String) {
        hkPackage.updateAppType(for: deviceTypes)
        logger.log(level: .info, tag: tag, message: "Updated HealthKit app type for \(context): \(deviceTypes)")
    }

    private func updateAppTypeForConfiguredPermissionScope() async -> Set<String> {
        let deviceTypes = await getConfiguredHealthKitPermissionScopeDeviceTypes()
        updateAppType(for: deviceTypes, context: "configured permission scope")
        return deviceTypes
    }

    /// Requests the complete Apple Health permission set upfront so all six data types
    /// (Weight, BMI, Body Fat, Lean Body Mass, Blood Pressure, Heart Rate) are presented at first
    /// integration. ggHealthKitPackage requests one app type per call, so the Weight Gurus and
    /// Balance Health sets are requested back-to-back; iOS only prompts for types that are still
    /// undetermined. Afterwards we settle on the Weight Gurus app type, which is what permission
    /// counting and sync run under. (MOB-405)
    private func requestFullAuthorization() async -> Bool {
        updateAppType(for: [DeviceType.scale.rawValue], context: "initial authorization (Weight Gurus)")
        let weightGurusResult = await hkPackage.requestAuthorization()
        updateAppType(for: [DeviceType.bpm.rawValue], context: "initial authorization (Balance Health)")
        let balanceHealthResult = await hkPackage.requestAuthorization()
        // Settle on the Weight Gurus app type for permission counting and sync.
        updateAppType(for: [DeviceType.scale.rawValue], context: "post-authorization default")
        return weightGurusResult || balanceHealthResult
    }

    // MARK: - HealthKitServiceProtocol

    /// Integrates or de-integrates Apple Health based on `turnOn`. Returns `true` when integration remains enabled after the call.
    public func integrate(turnOn: Bool) async throws -> Bool { // swiftlint:disable:this function_body_length
        let accountId = accountService.activeAccount?.accountId ?? "nil"
        logger.log(level: .info, tag: tag, message: "HealthKit integrate requested. turnOn=\(turnOn), accountId=\(accountId)")
        if turnOn {
            do {
                let isAlreadyIntegrated = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                if isAlreadyIntegrated {
                    logger.log(level: .error, tag: tag, message: "HealthKit integrate blocked due to user conflict. accountId=\(accountId)")
                    throw IntegrationError.userConflict
                }
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "HealthKit integrate user conflict check failed. accountId=\(accountId), error=\(error.localizedDescription)"
                )
                throw IntegrationError.userConflict
            }
        }

        if turnOn {
            let isAvailable = hkPackage.available()
            if !isAvailable {
                logger.log(level: .error, tag: tag, message: "HealthKit integrate failed: HealthKit unavailable on device. accountId=\(accountId)")
                return false
            }
            // MOB-405: request the full data-type set (Weight Gurus + Balance Health) upfront.
            let authorizationResult = await requestFullAuthorization()
            if !authorizationResult {
                logger.log(level: .error, tag: tag, message: "HealthKit authorization failed.")
                return false
            }
            let permissions = getApprovedPermissionList()
            if permissions.isEmpty {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "HealthKit integrate failed: no permissions approved after authorization. accountId=\(accountId)"
                )
                return false
            }
            let accountID = accountService.activeAccount?.accountId ?? ""
            let integrationInfo = IntegrationInfo(
                type: .healthKit,
                isIntegrated: true,
                assignedTo: accountID
            )
            do {
                try await self.integrationService.setStoredIntegrationData(integrationInfo)
                persistHealthKitPermissionScopeDeviceTypes(fullPermissionScopeDeviceTypes, for: accountID)
                logger.log(
                    level: .success,
                    tag: tag,
                    message: "HealthKit integrate succeeded. accountId=\(accountID), permissionsCount=\(permissions.count)"
                )
                return true
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "HealthKit integrate failed while persisting integration data. "
                        + "accountId=\(accountID), error=\(error.localizedDescription)"
                )
                return false
            }
        } else {
            logger.log(level: .info, tag: tag, message: "HealthKit de-integration requested. accountId=\(accountId)")
            try await clearHealthKit()
            logger.log(level: .success, tag: tag, message: "HealthKit de-integration completed. accountId=\(accountId)")
            return false
        }
    }

    public func isHKOutOfSync() async -> Bool {
        do {
            let result = try await self.integrationService.getStoredIntegrationData()
            let isIntegrated = result?.isIntegrated ?? false
            let approvedPermissions = self.getApprovedPermissionList()
            return isIntegrated && approvedPermissions.isEmpty
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load integration data", data: error.localizedDescription)
            return false
        }
    }

    /// Pushes the entire local entry history into Apple Health.
    public func syncAllData() async throws { // swiftlint:disable:this function_body_length
        // Get accountId on main actor first
        let accountId = await getActiveAccountId()
        guard let accountId else { return }
        logger.log(level: .info, tag: tag, message: "HealthKit full sync started. accountId=\(accountId)")

        // Use the configured permission scope so initial Balance Health integrations
        // do not silently expand into Weight Gurus until we explicitly re-request.
        _ = await updateAppTypeForConfiguredPermissionScope()

        // Materialize simple export values off the main actor to avoid cross-context @Model access
        let (scaleExports, bpmExports): ([HealthKitExport], [HealthKitExportExtended]) = try await Task.detached(priority: .userInitiated) {
            let container = await PersistenceController.shared.container
            let bgContext = ModelContext(container)
            let opCreate = OperationType.create.rawValue

            // Fetch all entries for this account
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                $0.accountId == accountId && $0.operationType == opCreate
            })
            let entries = try bgContext.fetch(descriptor)

            var scaleItems: [HealthKitExport] = []
            var bpmItems: [HealthKitExportExtended] = []
            let bpmType = EntryType.bpm.rawValue

            for entry in entries {
                if entry.entryType == bpmType, let bpmEntry = entry.bpmEntry {
                    // BPM entries → systolic, diastolic, pulse
                    bpmItems.append(HealthKitExportExtended(
                        timestamp: entry.entryTimestamp,
                        weight: nil,
                        bodyFat: nil,
                        muscleMass: nil,
                        bmi: nil,
                        pulse: bpmEntry.pulse,
                        systolic: bpmEntry.systolic,
                        diastolic: bpmEntry.diastolic
                    ))
                } else if entry.scaleEntry != nil {
                    // Scale entries → weight, body fat, muscle mass, BMI
                    scaleItems.append(HealthKitExport(
                        timestamp: entry.entryTimestamp,
                        weight: entry.scaleEntry?.weight,
                        bodyFat: entry.scaleEntry?.bodyFat,
                        muscleMass: entry.scaleEntry?.muscleMass,
                        bmi: entry.scaleEntry?.bmi,
                        pulse: entry.scaleEntryMetric?.pulse
                    ))
                }
            }
            return (scaleItems, bpmItems)
        }.value

        // MA-3941: commit the full-sync payload in fixed-size entry chunks so the in-flight
        // HealthKit payload stays bounded regardless of how many entries the account has.
        // A single 9k-entry payload (~45k HK samples) previously exhausted memory and tripped
        // the main-thread watchdog before the sync completed.
        let entryChunkSize = 1000
        let scaleTotal = scaleExports.count
        let bpmTotal = bpmExports.count
        logger.log(
            level: .info,
            tag: tag,
            message: "HealthKit full sync fetched entries. accountId=\(accountId), scaleEntries=\(scaleTotal), bpmEntries=\(bpmTotal)"
        )

        var scaleProcessed = 0
        while scaleProcessed < scaleTotal {
            let end = min(scaleProcessed + entryChunkSize, scaleTotal)
            let chunk = Array(scaleExports[scaleProcessed..<end])
            try await saveHealthKitData(finalData: buildHealthKitData(from: chunk))
            scaleProcessed = end
            logger.log(
                level: .info,
                tag: tag,
                message: "HealthKit full sync progress (scale). accountId=\(accountId), processed=\(scaleProcessed)/\(scaleTotal)"
            )
        }

        var bpmProcessed = 0
        while bpmProcessed < bpmTotal {
            let end = min(bpmProcessed + entryChunkSize, bpmTotal)
            var chunkPayload: [HealthKitData] = []
            for bpmExport in bpmExports[bpmProcessed..<end] {
                chunkPayload.append(contentsOf: buildHealthKitData(from: bpmExport))
            }
            try await saveHealthKitData(finalData: chunkPayload)
            bpmProcessed = end
            logger.log(
                level: .info,
                tag: tag,
                message: "HealthKit full sync progress (bpm). accountId=\(accountId), processed=\(bpmProcessed)/\(bpmTotal)"
            )
        }

        logger.log(
            level: .success,
            tag: tag,
            message: "HealthKit full sync completed. accountId=\(accountId), scaleEntries=\(scaleTotal), bpmEntries=\(bpmTotal)"
        )
    }

    /// Opens the Apple Health app so the user can review permissions.
    public func openAppleHealth() {
        logger.log(level: .info, tag: tag, message: "Opening Apple Health app from integration flow")
        Task {
            await hkPackage.openAppleHealth()
        }
    }

    /// Writes a single `Entry` into Apple Health.
    /// - Note: Prefer `syncNewData(notification:)` when crossing actor boundaries.
    ///   Caller must own `entry`'s `ModelContext` — we snapshot it immediately
    ///   so downstream code never reads SwiftData properties off-actor (MA-3898).
    func syncNewData(entry: Entry) async throws {
        let snapshot = entry.toSnapshot()
        let healthKitData = buildHealthKitData(from: [snapshot])
        try await hkPackage.saveData(healthKitData)
    }

    /// Writes entry data into Apple Health using an EntryNotification.
    /// This method is safe to call from any actor as it uses extracted data.
    func syncNewData(notification: EntryNotification) async throws {
        logger.log(
            level: .info,
            tag: tag,
            message: "HealthKit sync new entry started. timestamp=\(notification.entryTimestamp), entryType=\(notification.entryType)"
        )
        let export = HealthKitExportExtended(
            timestamp: notification.entryTimestamp,
            weight: notification.weight,
            bodyFat: notification.bodyFat,
            muscleMass: notification.muscleMass,
            bmi: notification.bmi,
            pulse: notification.pulse,
            systolic: notification.systolic,
            diastolic: notification.diastolic
        )
        let healthKitData = buildHealthKitData(from: export)
        try await hkPackage.saveData(healthKitData)
        logger.log(
            level: .success,
            tag: tag,
            message: "HealthKit sync new entry completed. timestamp=\(notification.entryTimestamp), "
                + "payloadCount=\(healthKitData.count)"
        )
    }

    /// Deletes a single `Entry` previously written to Apple Health.
    /// - Note: Prefer `deleteEntry(notification:)` when crossing actor boundaries.
    ///   Caller must own `entry`'s `ModelContext` — we snapshot it immediately
    ///   so downstream code never reads SwiftData properties off-actor (MA-3898).
    func deleteEntry(entry: Entry) async throws -> Bool {
        let snapshot = entry.toSnapshot()
        let healthKitData = buildHealthKitData(from: [snapshot])
        try await hkPackage.deleteEntry(healthKitData)
        return true
    }

    /// Deletes entry data from Apple Health using an EntryNotification.
    /// This method is safe to call from any actor as it uses extracted data.
    func deleteEntry(notification: EntryNotification) async throws -> Bool {
        logger.log(level: .info, tag: tag, message: "HealthKit delete entry started. timestamp=\(notification.entryTimestamp)")
        let export = HealthKitExportExtended(
            timestamp: notification.entryTimestamp,
            weight: notification.weight,
            bodyFat: notification.bodyFat,
            muscleMass: notification.muscleMass,
            bmi: notification.bmi,
            pulse: notification.pulse,
            systolic: notification.systolic,
            diastolic: notification.diastolic
        )
        let healthKitData = buildHealthKitData(from: export)
        try await hkPackage.deleteEntry(healthKitData)
        logger.log(
            level: .success,
            tag: tag,
            message: "HealthKit delete entry completed. timestamp=\(notification.entryTimestamp), "
                + "payloadCount=\(healthKitData.count)"
        )
        return true
    }

    /// Returns the expected permission count used to decide whether the user has granted the full
    /// Apple Health permission set. Counting always runs under the Weight Gurus app type, which
    /// covers the weight metrics plus heart rate; blood pressure is written unconditionally during
    /// sync, so it does not factor into the count. (MOB-405)
    public func expectedPermissionCount() async -> Int {
        weightGurusPermissionCount
    }

    /// Removes all Apple Health records previously generated by the app.
    public func clearHealthKit() async throws {
        let accountId = accountService.activeAccount?.accountId ?? "nil"
        logger.log(level: .info, tag: tag, message: "HealthKit clear requested. accountId=\(accountId)")
        do {
            try await self.integrationService.clearIntegrationStatus(integrationType: .healthKit)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to clear integration status", data: error.localizedDescription)
        }
        clearStoredHealthKitPermissionScopeDeviceTypes(for: accountService.activeAccount?.accountId)
        do {
            try await hkPackage.deleteAllData()
            logger.log(level: .success, tag: tag, message: "HealthKit clear completed. accountId=\(accountId)")
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "HealthKit clear failed during deleteAllData. accountId=\(accountId), error=\(error.localizedDescription)"
            )
            throw error
        }
    }

    /// Returns `true` if at least one HealthKit permission is granted.
    func checkAuthorizationStatus() -> Bool {
        let approvedPermissionList = self.getApprovedPermissionList()
        return !approvedPermissionList.isEmpty
    }

    /// Lists the granted HealthKit permission identifiers.
    func getApprovedPermissionList() -> [String] {
        hkPackage.getApprovedPermissionList()
    }

    // MARK: - Private Helpers ------------------------------------------------

    /// Fetches all entries from the local database.
    private func fetchAllEntries() async throws -> [EntrySnapshot] {
        do {
           let entries = try await entryService.getAllEntriesAsSnapshots()
           return entries
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch entries", data: error.localizedDescription)
            throw error
        }
    }

    /// Normalizes timestamp to include fractional seconds if missing
    private func normalizeTimestamp(_ timestamp: String) -> String {
        // If timestamp ends with 'Z' and does NOT already contain fractional seconds
        if timestamp.hasSuffix("Z") && !timestamp.contains(".") {
            return timestamp.replacingOccurrences(of: "Z", with: ".000Z")
        }
        return timestamp
    }

    /// Converts entries into `HealthKitData` payloads ready for saving.
    /// Takes `EntrySnapshot` (value type) so we never read SwiftData @Model
    /// properties across actor boundaries — see MA-3898.
    private func buildHealthKitData(from entries: [EntrySnapshot]) -> [HealthKitData] {
        var healthKitData: [HealthKitData] = []
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        for entry in entries {
          guard let scaleEntry = entry.scaleEntry else { continue }

          // Normalize timestamp to include fractional seconds if missing
          let normalizedTimestamp = normalizeTimestamp(entry.entryTimestamp)
          guard let timestamp = formatter.date(from: normalizedTimestamp) else {
            continue
          }

          if let weight = scaleEntry.weight {
            healthKitData.append(HealthKitData(
              type: .weight,
              value: ConversionTools.convertStoredToLbs(weight),
              timestamp: timestamp
            ))
          }

          if let bodyFat = scaleEntry.bodyFat {
            healthKitData.append(HealthKitData(
              type: .bodyFat,
              value: ConversionTools.convertStoredToLbs(bodyFat),
              timestamp: timestamp
            ))
          }

          if let pulse = entry.scaleEntryMetric?.pulse {
            healthKitData.append(HealthKitData(
              type: .heartRate,
              value: Double(pulse),
              timestamp: timestamp
            ))
          }

          if let weight = scaleEntry.weight, let bodyFat = scaleEntry.bodyFat {
            let convertedWeight = ConversionTools.convertStoredToLbs(weight)
            let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
            let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
            healthKitData.append(HealthKitData(
              type: .leanBodyMass,
              value: leanBodyMass,
              timestamp: timestamp
            ))
          }

          if let bmi = scaleEntry.bmi {
            healthKitData.append(HealthKitData(
              type: .bmi,
              value: ConversionTools.convertStoredToLbs(bmi),
              timestamp: timestamp
            ))
          }

        }

        return healthKitData
      }

    /// Converts export items into `HealthKitData` payloads without touching SwiftData models.
    private func buildHealthKitData(from exports: [HealthKitExport]) -> [HealthKitData] {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return exports.flatMap { item -> [HealthKitData] in
            guard let timestamp = formatter.date(from: item.timestamp) else { return [] }
            return healthKitSamples(for: item, timestamp: timestamp)
        }
    }

    /// Builds the HealthKit samples for a single export row. A stored `0` for any metric
    /// means the scale could not measure it — treated as missing so we don't push bogus
    /// samples or compute derived values from them.
    private func healthKitSamples(for item: HealthKitExport, timestamp: Date) -> [HealthKitData] {
        var samples: [HealthKitData] = []

        if let weight = item.weight, weight > 0 {
            samples.append(HealthKitData(
                type: .weight,
                value: ConversionTools.convertStoredToLbs(weight),
                timestamp: timestamp
            ))
        }

        if let bodyFat = item.bodyFat, bodyFat > 0 {
            samples.append(HealthKitData(
                type: .bodyFat,
                value: ConversionTools.convertStoredToLbs(bodyFat),
                timestamp: timestamp
            ))
        }

        // Prefer the scale's measured muscleMass; only fall back to the
        // weight×bodyFat derivation when measured value is unavailable.
        // Emitting both produces duplicate HealthKit samples at the same
        // timestamp and roughly doubles the leanBodyMass payload.
        if let muscleMass = item.muscleMass, muscleMass > 0 {
            samples.append(HealthKitData(
                type: .leanBodyMass,
                value: ConversionTools.convertStoredToLbs(muscleMass),
                timestamp: timestamp
            ))
        } else if let weight = item.weight, weight > 0,
                  let bodyFat = item.bodyFat, bodyFat > 0 {
            let convertedWeight = ConversionTools.convertStoredToLbs(weight)
            let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
            let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
            samples.append(HealthKitData(
                type: .leanBodyMass,
                value: leanBodyMass,
                timestamp: timestamp
            ))
        }

        if let bmi = item.bmi, bmi > 0 {
            samples.append(HealthKitData(
                type: .bmi,
                value: ConversionTools.convertStoredToLbs(bmi),
                timestamp: timestamp
            ))
        }

        // MOB-819: scale entries can carry a measured heart rate on
        // scaleEntryMetric.pulse. The full-history push dropped it, so
        // existing entries never wrote heart rate to Apple Health. A stored
        // `0` means the scale couldn't measure it — skip, like the other metrics.
        if let pulse = item.pulse, pulse > 0 {
            samples.append(HealthKitData(
                type: .heartRate,
                value: Double(pulse),
                timestamp: timestamp
            ))
        }

        return samples
    }

    // MARK: - Local Helper DTO -----------------------------------------------
    private struct HealthKitExport {
        let timestamp: String
        let weight: Int?
        let bodyFat: Int?
        let muscleMass: Int?
        let bmi: Int?
        let pulse: Int?
    }

    /// Extended export struct that includes pulse and BPM data for EntryNotification conversions.
    private struct HealthKitExportExtended {
        let timestamp: String
        let weight: Int?
        let bodyFat: Int?
        let muscleMass: Int?
        let bmi: Int?
        let pulse: Int?
        let systolic: Int?
        let diastolic: Int?
    }

    /// Converts a single extended export into HealthKitData payloads.
    private func buildHealthKitData(from export: HealthKitExportExtended) -> [HealthKitData] { // swiftlint:disable:this function_body_length
        var healthKitData: [HealthKitData] = []
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        let normalizedTimestamp = normalizeTimestamp(export.timestamp)
        guard let timestamp = formatter.date(from: normalizedTimestamp) else {
            return healthKitData
        }

        // A stored `0` for any metric means the scale could not measure it — treat as
        // missing so we don't push bogus samples or compute derived values from them.
        if let weight = export.weight, weight > 0 {
            healthKitData.append(HealthKitData(
                type: .weight,
                value: ConversionTools.convertStoredToLbs(weight),
                timestamp: timestamp
            ))
        }

        if let bodyFat = export.bodyFat, bodyFat > 0 {
            healthKitData.append(HealthKitData(
                type: .bodyFat,
                value: ConversionTools.convertStoredToLbs(bodyFat),
                timestamp: timestamp
            ))
        }

        if let pulse = export.pulse, pulse > 0 {
            healthKitData.append(HealthKitData(
                type: .heartRate,
                value: Double(pulse),
                timestamp: timestamp
            ))
        }

        if let weight = export.weight, weight > 0,
           let bodyFat = export.bodyFat, bodyFat > 0 {
            let convertedWeight = ConversionTools.convertStoredToLbs(weight)
            let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
            let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
            healthKitData.append(HealthKitData(
                type: .leanBodyMass,
                value: leanBodyMass,
                timestamp: timestamp
            ))
        }

        if let bmi = export.bmi, bmi > 0 {
            healthKitData.append(HealthKitData(
                type: .bmi,
                value: ConversionTools.convertStoredToLbs(bmi),
                timestamp: timestamp
            ))
        }

        // BPM data (blood pressure)
        if let systolic = export.systolic {
            healthKitData.append(HealthKitData(
                type: .systolic,
                value: Double(systolic),
                timestamp: timestamp
            ))
        }

        if let diastolic = export.diastolic {
            healthKitData.append(HealthKitData(
                type: .diastolic,
                value: Double(diastolic),
                timestamp: timestamp
            ))
        }

        return healthKitData
    }

    /// Writes the provided dataset to Apple Health and logs the outcome.
    private func saveHealthKitData(finalData: [HealthKitData]) async throws {
        if finalData.isEmpty {
            logger.log(level: .info, tag: tag, message: "HealthKit: No data to save")
            return
        }

        // Offload the actual HealthKit write off the main actor to avoid UI hangs.
        let handler = hkPackage
        do {
            try await Task.detached(priority: .userInitiated) {
                try await handler.saveData(finalData)
            }.value
        } catch {
            logger.log(level: .error, tag: tag, message: "HealthKit: saveHealthKitData error", data: error)
            throw error
        }
    }

    // MARK: - Integration Helper ------------------------------------------------
    /// Determines which Apple Health integration modal (if any) should be presented on app launch.
    /// - Returns: A `HKIntegrationModalState` value (`.addIntegration` / `.finishAdding`) when a prompt
    ///            should be shown, or `nil` when no prompt is required.
    public func shouldShowHKIntegrationModal() async throws -> HKIntegrationModalState? { // swiftlint:disable:this cyclomatic_complexity function_body_length
        do {
            // ------------------------------------------------------------
            // 0️⃣  Out of Sync
            // ------------------------------------------------------------
            // Show when the integration is marked as enabled but *no* HealthKit permissions remain.
            // This means the user has disabled permissions in Apple Health while the integration is
            // still considered active in Weight Gurus.
            let approvedPermissions = getApprovedPermissionList()
            if approvedPermissions.isEmpty {
                if let integrationInfo = try await integrationService.getStoredIntegrationData(),
                   integrationInfo.isIntegrated,
                   integrationInfo.type == .healthKit {
                    let accountId = await getActiveAccountId()
                    let scopedOutOfSyncKey = KvStorageKeys.scopedHealthKitModalKey(outOfSyncHKModalFlagKeyBase, accountId: accountId)
                    if (kvStore.getValue(forKey: scopedOutOfSyncKey) as? Bool) != true {
                        kvStore.setValue(true, forKey: scopedOutOfSyncKey)
                        logger.log(level: .info, tag: tag, message: "HealthKit launch modal decision: outOfSync")
                        return .outOfSync
                    }
                }
            }

            // ------------------------------------------------------------
            // 1️⃣  Finish Adding Apple Health
            // ------------------------------------------------------------
            // Show when HealthKit permissions have been granted (≥1) but we don't yet
            // have a stored integration record for the current device/account.
            let accountId = await getActiveAccountId()
            let scopedFinishKey = KvStorageKeys.scopedHealthKitModalKey(finishHKModalFlagKeyBase, accountId: accountId)
            if (kvStore.getValue(forKey: scopedFinishKey) as? Bool) != true {
                let approvedPermissions = getApprovedPermissionList()
                if !approvedPermissions.isEmpty {
                    let storedIntegrationData = try await integrationService.getStoredIntegrationData()
                    if storedIntegrationData == nil {
                        // Ensure no other account on this device is already integrated with Apple Health
                        let isUsedByAnotherAccount = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                        if !isUsedByAnotherAccount {
                            // Another account is already integrated; skip showing the Finish Adding prompt
                            kvStore.setValue(true, forKey: scopedFinishKey)
                            logger.log(level: .info, tag: tag, message: "HealthKit launch modal decision: finishAdding")
                            return .finishAdding
                        }
                    }
                }
            }

            // ------------------------------------------------------------
            // 2️⃣  Add Apple Health Integration (fresh install / new device)
            // ------------------------------------------------------------
            let scopedAddKey = KvStorageKeys.scopedHealthKitModalKey(addHKModalFlagKeyBase, accountId: accountId)
            if (kvStore.getValue(forKey: scopedAddKey) as? Bool) != true {
                guard accountId != nil else {
                    return nil
                }

                // Account level flag from backend indicating HealthKit was enabled previously.
                let isHealthKitOn = await isHealthKitEnabledForActiveAccount()
                if isHealthKitOn {
                    let storedIntegrationData = try await integrationService.getStoredIntegrationData()
                    if storedIntegrationData == nil {
                        let isUsedByAnotherAccount = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                        if !isUsedByAnotherAccount {
                            // Another account is already integrated; skip showing the Add Integration prompt
                            kvStore.setValue(true, forKey: scopedAddKey)
                            logger.log(level: .info, tag: tag, message: "HealthKit launch modal decision: addIntegration")
                            return .addIntegration
                        }
                    }
                }
            }

            return nil
        } catch {
            logger.log(level: .error, tag: tag, message: "shouldShowHKIntegrationModal check failed", data: error.localizedDescription)
            throw error
        }
    }

    // MARK: - Out of Sync Permission Restoration Tracking

    /// Sets a flag indicating we're waiting for permissions to be restored after out-of-sync.
    /// Called when user taps "OPEN APPLE HEALTH" from the out-of-sync modal.
    public func setWaitingForPermissionsRestored() {
        let accountId = accountService.activeAccount?.accountId
        let scopedKey = KvStorageKeys.scopedHealthKitModalKey(waitingForHKPermissionsRestoredBase, accountId: accountId)
        kvStore.setValue(true, forKey: scopedKey)
        logger.log(level: .info, tag: tag, message: "Set waiting-for-permissions-restored flag. accountId=\(accountId ?? "nil")")
    }

    /// Clears the flag indicating we're waiting for permissions to be restored.
    public func clearWaitingForPermissionsRestored() {
        let accountId = accountService.activeAccount?.accountId
        let scopedKey = KvStorageKeys.scopedHealthKitModalKey(waitingForHKPermissionsRestoredBase, accountId: accountId)
        kvStore.clearValue(forKey: scopedKey)
        logger.log(level: .info, tag: tag, message: "Cleared waiting-for-permissions-restored flag. accountId=\(accountId ?? "nil")")
    }

    /// Checks if permissions were restored after being out of sync.
    /// Returns `true` if we were waiting for permissions and they are now restored.
    /// This should be called on app launch to show the success toast.
    public func checkIfPermissionsRestoredAfterOutOfSync() async -> Bool {
        let accountId = accountService.activeAccount?.accountId
        let scopedKey = KvStorageKeys.scopedHealthKitModalKey(waitingForHKPermissionsRestoredBase, accountId: accountId)

        // Check if we were waiting for permissions to be restored
        guard (kvStore.getValue(forKey: scopedKey) as? Bool) == true else {
            return false
        }

        // Check if permissions are now restored (at least one permission granted)
        let approvedPermissions = getApprovedPermissionList()
        if !approvedPermissions.isEmpty {
            // Permissions restored - clear the flag and return true
            kvStore.clearValue(forKey: scopedKey)
            logger.log(
                level: .success,
                tag: tag,
                message: "HealthKit permissions restored after out-of-sync. "
                    + "accountId=\(accountId ?? "nil"), permissionsCount=\(approvedPermissions.count)"
            )
            return true
        }

        logger.log(level: .info, tag: tag, message: "HealthKit permissions still not restored after out-of-sync. accountId=\(accountId ?? "nil")")
        return false
    }

}
