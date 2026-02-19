// swiftlint:disable type_body_length
// This service intentionally aggregates all HealthKit integration logic
// to maintain a single source of truth for health data synchronization.
// Splitting would fragment the integration flow and reduce maintainability.

import Foundation
import HealthKit
import ggHealthKitPackage
import SwiftData

@MainActor
public final class HealthKitService: HealthKitServiceProtocol {
    static let shared = HealthKitService()
    @Injector private var integrationService: IntegrationsService
    @Injector private var logger: LoggerService
    @Injector private var accountService: AccountService
    @Injector private var entryService: EntryService
    private let hkPackage = ggHealthKitPackage.AppleHealthHandler.shared
    private let tag = "HealthKitService"
    private let context: ModelContext
    private let kvStore = KvStorageService.shared
    private let addHKModalFlagKeyBase = KvStorageKeys.addAppleHealthModalBase
    /// Local storage flag indicating the *Finish Adding Apple Health* prompt has already been shown on this device.
    private let finishHKModalFlagKeyBase = KvStorageKeys.finishAppleHealthModalBase
    /// Local storage flag indicating the *Out of Sync* Apple Health prompt has already been shown on this device.
    private let outOfSyncHKModalFlagKeyBase = KvStorageKeys.outOfSyncAppleHealthModalBase
    /// Local storage flag indicating we're waiting for permissions to be restored after out-of-sync.
    private let waitingForHKPermissionsRestoredBase = KvStorageKeys.waitingForHKPermissionsRestoredBase
    
    // MARK: - Initialization
    
    init() {
        hkPackage.setAppType(appType: .WEIGHT_GURUS)
        self.context = PersistenceController.shared.context
    }
    
    // MARK: - HealthKitServiceProtocol
    
    /// Integrates or de-integrates Apple Health based on `turnOn`. Returns `true` when integration remains enabled after the call.
    public func integrate(turnOn: Bool) async throws -> Bool {
        if turnOn {
            do {
                let isAlreadyIntegrated = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                if isAlreadyIntegrated {
                    throw IntegrationError.userConflict
                }
            } catch {
                throw IntegrationError.userConflict
            }
        }
        
        if turnOn {
            let isAvailable = hkPackage.available();
            if !isAvailable {
                return false
            }
            let authorizationResult = await hkPackage.requestAuthorization()
            if !authorizationResult {
                logger.log(level: .error, tag: tag, message: "HealthKit authorization failed.")
                return false
            }
            let permissions = getApprovedPermissionList()
            if permissions.isEmpty {
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
                return true
            } catch {
                return false
            }
        } else {
            try await clearHealthKit()
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
    public func syncAllData() async throws {
        // Get accountId on main actor first
        let accountId = try await accountService.getActiveAccount()?.accountId
        guard let accountId else { return }

        // Materialize simple export values off the main actor to avoid cross-context @Model access
        let exports: [HealthKitExport] = try await Task.detached(priority: .userInitiated) {
            let container = await PersistenceController.shared.container
            let bgContext = ModelContext(container)
            // Avoid referencing enum cases inside #Predicate; compare to a captured String constant instead.
            let opCreate = OperationType.create.rawValue
            let descriptor = FetchDescriptor<Entry>(predicate: #Predicate {
                $0.accountId == accountId && $0.operationType == opCreate
            })
            let entries = try bgContext.fetch(descriptor)
            var items: [HealthKitExport] = []
            items.reserveCapacity(entries.count)
            for entry in entries {
                items.append(HealthKitExport(
                    timestamp: entry.entryTimestamp,
                    weight: entry.scaleEntry?.weight,
                    bodyFat: entry.scaleEntry?.bodyFat,
                    muscleMass: entry.scaleEntry?.muscleMass,
                    bmi: entry.scaleEntry?.bmi
                ))
            }
            return items
        }.value

        let healthKitData = buildHealthKitData(from: exports)
        try await saveHealthKitData(finalData: healthKitData)
    }
    
    /// Opens the Apple Health app so the user can review permissions.
    public func openAppleHealth() {
        Task {
            await hkPackage.openAppleHealth()
        }
    }
    
    /// Writes a single `Entry` into Apple Health.
    /// - Note: Prefer `syncNewData(notification:)` when crossing actor boundaries.
    func syncNewData(entry: Entry) async throws {
        let healthKitData = buildHealthKitData(from: [entry])
        try await hkPackage.saveData(healthKitData)
    }

    /// Writes entry data into Apple Health using an EntryNotification.
    /// This method is safe to call from any actor as it uses extracted data.
    func syncNewData(notification: EntryNotification) async throws {
        let export = HealthKitExportExtended(
            timestamp: notification.entryTimestamp,
            weight: notification.weight,
            bodyFat: notification.bodyFat,
            muscleMass: notification.muscleMass,
            bmi: notification.bmi,
            pulse: notification.pulse
        )
        let healthKitData = buildHealthKitData(from: export)
        try await hkPackage.saveData(healthKitData)
    }

    /// Deletes a single `Entry` previously written to Apple Health.
    /// - Note: Prefer `deleteEntry(notification:)` when crossing actor boundaries.
    func deleteEntry(entry: Entry) async throws -> Bool {
        let healthKitData = buildHealthKitData(from: [entry])
        try await hkPackage.deleteEntry(healthKitData)
        return true
    }

    /// Deletes entry data from Apple Health using an EntryNotification.
    /// This method is safe to call from any actor as it uses extracted data.
    func deleteEntry(notification: EntryNotification) async throws -> Bool {
        let export = HealthKitExportExtended(
            timestamp: notification.entryTimestamp,
            weight: notification.weight,
            bodyFat: notification.bodyFat,
            muscleMass: notification.muscleMass,
            bmi: notification.bmi,
            pulse: notification.pulse
        )
        let healthKitData = buildHealthKitData(from: export)
        try await hkPackage.deleteEntry(healthKitData)
        return true
    }
    
    /// Removes all Apple Health records previously generated by the app.
    public func clearHealthKit() async throws {
        do {
            try await self.integrationService.clearIntegrationStatus(integrationType: .healthKit)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to clear integration status", data: error.localizedDescription)
        }
        try await hkPackage.deleteAllData()
    }
    
    /// Returns `true` if at least one HealthKit permission is granted.
    func checkAuthorizationStatus() -> Bool {
        let approvedPermissionList = self.getApprovedPermissionList();
        return !approvedPermissionList.isEmpty
    }
    
    /// Lists the granted HealthKit permission identifiers.
    func getApprovedPermissionList() -> [String] {
        hkPackage.getApprovedPermissionList()
    }
    
    // MARK: - Private Helpers ------------------------------------------------
    
    /// Fetches all entries from the local database.
    private func fetchAllEntries() async throws -> [Entry] {
        do {
           let entries = try await entryService.getAllEntries()
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
    private func buildHealthKitData(from entries: [Entry]) -> [HealthKitData] {
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
        var healthKitData: [HealthKitData] = []
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        for item in exports {
            guard let timestamp = formatter.date(from: item.timestamp) else { continue }

            if let weight = item.weight {
                healthKitData.append(HealthKitData(
                    type: .weight,
                    value: ConversionTools.convertStoredToLbs(weight),
                    timestamp: timestamp
                ))
            }

            if let bodyFat = item.bodyFat {
                healthKitData.append(HealthKitData(
                    type: .bodyFat,
                    value: ConversionTools.convertStoredToLbs(bodyFat),
                    timestamp: timestamp
                ))
            }

            if let muscleMass = item.muscleMass {
                healthKitData.append(HealthKitData(
                    type: .leanBodyMass,
                    value: ConversionTools.convertStoredToLbs(muscleMass),
                    timestamp: timestamp
                ))
            }

            if let weight = item.weight, let bodyFat = item.bodyFat {
                let convertedWeight = ConversionTools.convertStoredToLbs(weight)
                let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
                let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
                healthKitData.append(HealthKitData(
                    type: .leanBodyMass,
                    value: leanBodyMass,
                    timestamp: timestamp
                ))
            }

            if let bmi = item.bmi {
                healthKitData.append(HealthKitData(
                    type: .bmi,
                    value: ConversionTools.convertStoredToLbs(bmi),
                    timestamp: timestamp
                ))
            }
        }
        return healthKitData
    }

    // MARK: - Local Helper DTO -----------------------------------------------
    private struct HealthKitExport {
        let timestamp: String
        let weight: Int?
        let bodyFat: Int?
        let muscleMass: Int?
        let bmi: Int?
    }

    /// Extended export struct that includes pulse for EntryNotification conversions.
    private struct HealthKitExportExtended {
        let timestamp: String
        let weight: Int?
        let bodyFat: Int?
        let muscleMass: Int?
        let bmi: Int?
        let pulse: Int?
    }

    /// Converts a single extended export into HealthKitData payloads.
    private func buildHealthKitData(from export: HealthKitExportExtended) -> [HealthKitData] {
        var healthKitData: [HealthKitData] = []
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        let normalizedTimestamp = normalizeTimestamp(export.timestamp)
        guard let timestamp = formatter.date(from: normalizedTimestamp) else {
            return healthKitData
        }

        if let weight = export.weight {
            healthKitData.append(HealthKitData(
                type: .weight,
                value: ConversionTools.convertStoredToLbs(weight),
                timestamp: timestamp
            ))
        }

        if let bodyFat = export.bodyFat {
            healthKitData.append(HealthKitData(
                type: .bodyFat,
                value: ConversionTools.convertStoredToLbs(bodyFat),
                timestamp: timestamp
            ))
        }

        if let pulse = export.pulse {
            healthKitData.append(HealthKitData(
                type: .heartRate,
                value: Double(pulse),
                timestamp: timestamp
            ))
        }

        if let weight = export.weight, let bodyFat = export.bodyFat {
            let convertedWeight = ConversionTools.convertStoredToLbs(weight)
            let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
            let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
            healthKitData.append(HealthKitData(
                type: .leanBodyMass,
                value: leanBodyMass,
                timestamp: timestamp
            ))
        }

        if let bmi = export.bmi {
            healthKitData.append(HealthKitData(
                type: .bmi,
                value: ConversionTools.convertStoredToLbs(bmi),
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
    public func shouldShowHKIntegrationModal() async throws -> HKIntegrationModalState? {
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
                    let accountId = try? await accountService.getActiveAccount()?.accountId
                    let scopedOutOfSyncKey = KvStorageKeys.scopedHealthKitModalKey(outOfSyncHKModalFlagKeyBase, accountId: accountId)
                    if (kvStore.getValue(forKey: scopedOutOfSyncKey) as? Bool) != true {
                        kvStore.setValue(true, forKey: scopedOutOfSyncKey)
                        return .outOfSync
                    }
                }
            }

            // ------------------------------------------------------------
            // 1️⃣  Finish Adding Apple Health
            // ------------------------------------------------------------
            // Show when HealthKit permissions have been granted (≥1) but we don't yet
            // have a stored integration record for the current device/account.
            let accountId = try? await accountService.getActiveAccount()?.accountId
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
                guard let account = try await accountService.getActiveAccount() else {
                    return nil
                }

                // Account level flag from backend indicating HealthKit was enabled previously.
                let isHealthKitOn = account.integrationSettings?.isHealthKitOn ?? false
                if isHealthKitOn {
                    let storedIntegrationData = try await integrationService.getStoredIntegrationData()
                    if storedIntegrationData == nil {
                        let isUsedByAnotherAccount = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                        if !isUsedByAnotherAccount {
                            // Another account is already integrated; skip showing the Add Integration prompt
                            kvStore.setValue(true, forKey: scopedAddKey)
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
    }
    
    /// Clears the flag indicating we're waiting for permissions to be restored.
    public func clearWaitingForPermissionsRestored() {
        let accountId = accountService.activeAccount?.accountId
        let scopedKey = KvStorageKeys.scopedHealthKitModalKey(waitingForHKPermissionsRestoredBase, accountId: accountId)
        kvStore.clearValue(forKey: scopedKey)
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
            return true
        }
        
        return false
    }

}

