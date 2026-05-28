// swiftlint:disable file_length
// This service intentionally aggregates all HealthKit integration logic
// to maintain a single source of truth for health data synchronization.
// Splitting would fragment the integration flow and reduce maintainability.

import Combine
import Foundation
import ggHealthKitPackage
import HealthKit
import SwiftData
import SwiftUI

@MainActor
final class HealthKitService: HealthKitServiceProtocol { // swiftlint:disable:this type_body_length
    private struct PendingPermissionExpansion {
        let accountId: String
        let deviceTypes: Set<String>
    }

    static let shared = HealthKitService()
    private let integrationService: IntegrationServiceProtocol
    private let logger: LoggerServiceProtocol
    private let accountService: AccountServiceProtocol
    private let entryService: EntryServiceProtocol
    private let kvStore: KvStorageServiceProtocol
    private let hkPackage: HealthKitHandlerProtocol
    private let deviceService: DeviceServiceProtocol
    private let bluetoothService: BluetoothServiceProtocol
    private let notificationService: NotificationHelperServiceProtocol
    private let tag = "HealthKitService"
    private let context: ModelContext
    private var devicePairingObserver: NSObjectProtocol?
    private var cancellables = Set<AnyCancellable>()
    private var pendingPermissionExpansion: PendingPermissionExpansion?
    private var isPresentingPermissionExpansionAlert = false
    private let addHKModalFlagKeyBase = KvStorageKeys.addAppleHealthModalBase
    /// Local storage flag indicating the *Finish Adding Apple Health* prompt has already been shown on this device.
    private let finishHKModalFlagKeyBase = KvStorageKeys.finishAppleHealthModalBase
    /// Local storage flag indicating the *Out of Sync* Apple Health prompt has already been shown on this device.
    private let outOfSyncHKModalFlagKeyBase = KvStorageKeys.outOfSyncAppleHealthModalBase
    /// Local storage flag indicating we're waiting for permissions to be restored after out-of-sync.
    private let waitingForHKPermissionsRestoredBase = KvStorageKeys.waitingForHKPermissionsRestoredBase
    private let permissionExpansionAlertDelayNs: UInt64 = 1_500_000_000
    private let permissionExpansionAlertRetryDelayNs: UInt64 = 500_000_000

    // MARK: - Initialization

    init(
        integrationService: IntegrationServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        accountService: AccountServiceProtocol? = nil,
        entryService: EntryServiceProtocol? = nil,
        kvStore: KvStorageServiceProtocol? = nil,
        healthKitHandler: HealthKitHandlerProtocol? = nil,
        deviceService: DeviceServiceProtocol? = nil,
        bluetoothService: BluetoothServiceProtocol? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil
    ) {
        self.integrationService = integrationService ?? IntegrationsService.shared
        self.logger = logger ?? LoggerService.shared
        self.accountService = accountService ?? AccountService.shared
        self.entryService = entryService ?? EntryService.shared
        self.kvStore = kvStore ?? KvStorageService.shared
        self.hkPackage = healthKitHandler ?? AppleHealthHandlerAdapter()
        self.deviceService = deviceService ?? ScaleService.shared
        self.bluetoothService = bluetoothService ?? BluetoothService.shared
        self.notificationService = notificationService ?? NotificationHelperService.shared
        self.context = PersistenceController.shared.context
        observeDevicePairing()
        observeSetupProgress()
    }

    deinit {
        if let devicePairingObserver {
            NotificationCenter.default.removeObserver(devicePairingObserver)
        }
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

    /// Returns the set of device type raw values for paired devices on the active account.
    private func getPairedDeviceTypes() async -> Set<String> {
        do {
            let devices = try await deviceService.getDevices()
            return Set(devices.compactMap { $0.deviceType })
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch paired device types: \(error)")
            return []
        }
    }

    /// Maps a raw DeviceType value to its HealthKit permission category.
    /// babyScale has no distinct HealthKit data types from scale — both use Weight Gurus permissions.
    private func healthKitPermissionCategory(for deviceType: String) -> String {
        deviceType == DeviceType.babyScale.rawValue ? DeviceType.scale.rawValue : deviceType
    }

    private func getSignupSelectedDeviceType(for accountId: String) -> SignupDeviceType? {
        let key = KvStorageKeys.selectedSignupDeviceTypeKey(for: accountId)
        guard let rawValue = kvStore.getValue(forKey: key) as? String else { return nil }
        return SignupDeviceType(rawValue: rawValue)
    }

    private func persistSignupSelectedDeviceType(_ signupDeviceType: SignupDeviceType, for accountId: String) {
        let key = KvStorageKeys.selectedSignupDeviceTypeKey(for: accountId)
        kvStore.setValue(signupDeviceType.rawValue, forKey: key)
    }

    private func getRecentSignupSelectedDeviceType(for accountId: String) -> SignupDeviceType? {
        guard let recentSignupAccountId = kvStore.getValue(forKey: KvStorageKeys.recentSignupAccountId.rawValue) as? String,
              recentSignupAccountId == accountId,
              let rawValue = kvStore.getValue(forKey: KvStorageKeys.recentSignupDeviceType.rawValue) as? String else {
            return nil
        }
        return SignupDeviceType(rawValue: rawValue)
    }

    private func resolveSignupSelectedDeviceType(for accountId: String) -> SignupDeviceType? {
        if let signupDeviceType = getSignupSelectedDeviceType(for: accountId) {
            logger.log(
                level: .info,
                tag: tag,
                message: "Resolved signup-selected device type from account-scoped storage. "
                    + "accountId=\(accountId), deviceType=\(signupDeviceType.rawValue)"
            )
            return signupDeviceType
        }

        guard let recentSignupDeviceType = getRecentSignupSelectedDeviceType(for: accountId) else {
            logger.log(
                level: .info,
                tag: tag,
                message: "No signup-selected device type found in storage. accountId=\(accountId)"
            )
            return nil
        }

        persistSignupSelectedDeviceType(recentSignupDeviceType, for: accountId)
        logger.log(
            level: .info,
            tag: tag,
            message: "Recovered signup-selected device type from recent-signup fallback. "
                + "accountId=\(accountId), deviceType=\(recentSignupDeviceType.rawValue)"
        )
        return recentSignupDeviceType
    }

    private func getSignupSelectedDeviceTypes() async -> Set<String> {
        guard let accountId = await getActiveAccountId() else {
            logger.log(level: .info, tag: tag, message: "Unable to resolve signup-selected device types: active account is nil")
            return []
        }

        guard let signupDeviceType = resolveSignupSelectedDeviceType(for: accountId) else {
            return []
        }

        let signupDeviceTypes = signupDeviceType.healthKitFallbackDeviceTypes
        logger.log(
            level: .info,
            tag: tag,
            message: "Using signup-selected device types for HealthKit permission scope. "
                + "accountId=\(accountId), deviceTypes=\(signupDeviceTypes.sorted())"
        )
        return signupDeviceTypes
    }

    private func getStoredHealthKitPermissionScopeDeviceTypes(for accountId: String) -> Set<String>? {
        let key = KvStorageKeys.healthKitPermissionScopeDeviceTypesKey(for: accountId)
        guard let storedDeviceTypes = kvStore.getCodable(forKey: key, as: [String].self) else { return nil }
        return Set(storedDeviceTypes)
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

    private func getInitialAuthorizationDeviceTypes() async -> Set<String> {
        let signupDeviceTypes = await getSignupSelectedDeviceTypes()
        let pairedDeviceTypes = await getPairedDeviceTypes()

        // Balance Health users (BPM-only signup) should only request BPM permissions.
        // Don't expand the scope with weight scale types from paired devices.
        // Weight Gurus users get their signup types expanded with any paired BPM.
        let isBalanceHealthSignup = signupDeviceTypes.contains(DeviceType.bpm.rawValue)
            && !signupDeviceTypes.contains(DeviceType.scale.rawValue)
        let initialDeviceTypes = isBalanceHealthSignup
            ? signupDeviceTypes
            : signupDeviceTypes.union(pairedDeviceTypes)

        guard !initialDeviceTypes.isEmpty else {
            logger.log(
                level: .info,
                tag: tag,
                message: "Initial HealthKit permission scope is empty. signupDeviceTypes=[], pairedDeviceTypes=[]"
            )
            return []
        }

        logger.log(
            level: .info,
            tag: tag,
            message: "Using initial HealthKit permission scope from signup + paired devices. "
                + "signupDeviceTypes=\(signupDeviceTypes.sorted()), "
                + "pairedDeviceTypes=\(pairedDeviceTypes.sorted()), "
                + "isBalanceHealthSignup=\(isBalanceHealthSignup), "
                + "resolvedDeviceTypes=\(initialDeviceTypes.sorted())"
        )
        return initialDeviceTypes
    }

    private func getConfiguredHealthKitPermissionScopeDeviceTypes() async -> Set<String> {
        guard let accountId = await getActiveAccountId() else {
            return await getInitialAuthorizationDeviceTypes()
        }

        if let storedDeviceTypes = getStoredHealthKitPermissionScopeDeviceTypes(for: accountId),
           !storedDeviceTypes.isEmpty {
            return storedDeviceTypes
        }

        return await getInitialAuthorizationDeviceTypes()
    }

    private func updateAppType(for deviceTypes: Set<String>, context: String) {
        hkPackage.updateAppType(for: deviceTypes)
        logger.log(level: .info, tag: tag, message: "Updated HealthKit app type for \(context): \(deviceTypes)")
    }

    private func updateAppTypeForInitialAuthorization() async -> Set<String> {
        let deviceTypes = await getInitialAuthorizationDeviceTypes()
        updateAppType(for: deviceTypes, context: "initial authorization")
        return deviceTypes
    }

    private func updateAppTypeForConfiguredPermissionScope() async -> Set<String> {
        let deviceTypes = await getConfiguredHealthKitPermissionScopeDeviceTypes()
        updateAppType(for: deviceTypes, context: "configured permission scope")
        return deviceTypes
    }

    private func observeSetupProgress() {
        bluetoothService.isSetupInProgressPublisher
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isSetupInProgress in
                guard let self, !isSetupInProgress else { return }
                Task { @MainActor in
                    await self.processPendingPermissionExpansionIfNeeded()
                }
            }
            .store(in: &cancellables)
    }

    private func isHealthKitIntegrated() async -> Bool {
        do {
            guard let info = try await integrationService.getStoredIntegrationData() else { return false }
            return info.isIntegrated && info.type == .healthKit
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to check HealthKit integration status",
                data: error.localizedDescription
            )
            return false
        }
    }

    private func resolvePermissionExpansionIfNeeded() async -> PendingPermissionExpansion? {
        guard await isHealthKitIntegrated() else { return nil }
        guard hkPackage.available() else { return nil }
        guard let accountId = await getActiveAccountId() else { return nil }

        let pairedDeviceTypes = await getPairedDeviceTypes()
        guard !pairedDeviceTypes.isEmpty else { return nil }

        let currentPermissionScope = await getConfiguredHealthKitPermissionScopeDeviceTypes()
        let expandedDeviceTypes = currentPermissionScope.union(pairedDeviceTypes)
        guard expandedDeviceTypes != currentPermissionScope else { return nil }

        // babyScale maps to the same HealthKit permissions as scale — adding a baby scale
        // should not trigger a permission expansion modal.
        let currentCategories = Set(currentPermissionScope.map { healthKitPermissionCategory(for: $0) })
        let expandedCategories = Set(expandedDeviceTypes.map { healthKitPermissionCategory(for: $0) })
        guard expandedCategories != currentCategories else { return nil }

        logger.log(
            level: .info,
            tag: tag,
            message: "Detected additional HealthKit permissions needed. "
                + "accountId=\(accountId), currentScope=\(currentPermissionScope.sorted()), "
                + "pairedDeviceTypes=\(pairedDeviceTypes.sorted()), expandedScope=\(expandedDeviceTypes.sorted())"
        )
        return PendingPermissionExpansion(accountId: accountId, deviceTypes: expandedDeviceTypes)
    }

    private func queuePermissionExpansion(_ expansion: PendingPermissionExpansion) {
        if let pendingPermissionExpansion, pendingPermissionExpansion.accountId == expansion.accountId {
            self.pendingPermissionExpansion = PendingPermissionExpansion(
                accountId: expansion.accountId,
                deviceTypes: pendingPermissionExpansion.deviceTypes.union(expansion.deviceTypes)
            )
        } else {
            pendingPermissionExpansion = expansion
        }

        logger.log(
            level: .info,
            tag: tag,
            message: "Queued HealthKit permission expansion. accountId=\(expansion.accountId), "
                + "deviceTypes=\(self.pendingPermissionExpansion?.deviceTypes.sorted() ?? [])"
        )
    }

    private func clearPendingPermissionExpansion(accountId: String? = nil) {
        guard let pendingPermissionExpansion else { return }
        guard accountId == nil || pendingPermissionExpansion.accountId == accountId else { return }

        self.pendingPermissionExpansion = nil
        isPresentingPermissionExpansionAlert = false
    }

    private func waitForNotificationSurfaceToClear() async -> Bool {
        for _ in 0..<20 {
            if !notificationService.isAlertVisible &&
                !notificationService.isLoaderVisible &&
                !notificationService.isModalVisible {
                return true
            }
            try? await Task.sleep(nanoseconds: permissionExpansionAlertRetryDelayNs)
        }

        return !notificationService.isAlertVisible &&
            !notificationService.isLoaderVisible &&
            !notificationService.isModalVisible
    }

    private func schedulePermissionExpansionRetry() {
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: self?.permissionExpansionAlertRetryDelayNs ?? 500_000_000)
            await self?.processPendingPermissionExpansionIfNeeded()
        }
    }

    private func showPermissionExpansionModal(for expansion: PendingPermissionExpansion) {
        let modalView = HKIntegrationModalView(
            state: .updatePermissions,
            onClose: { [weak self] in
                guard let self else { return }
                self.logger.log(
                    level: .info,
                    tag: self.tag,
                    message: "Dismissed HealthKit permission expansion modal. accountId=\(expansion.accountId)"
                )
                self.notificationService.dismissModal()
                self.clearPendingPermissionExpansion(accountId: expansion.accountId)
            },
            onPrimaryTap: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                Task { @MainActor [weak self] in
                    await self?.requestQueuedPermissionExpansionAuthorization(accountId: expansion.accountId)
                }
            },
            onSecondaryTap: { [weak self] in
                guard let self else { return }
                self.logger.log(
                    level: .info,
                    tag: self.tag,
                    message: "Deferred HealthKit permission expansion from modal. accountId=\(expansion.accountId)"
                )
                self.notificationService.dismissModal()
                self.clearPendingPermissionExpansion(accountId: expansion.accountId)
            }
        )
        logger.log(level: .info, tag: tag, message: "Presenting queued HealthKit permission expansion modal. accountId=\(expansion.accountId)")
        notificationService.showModal(ModalData(presentedView: AnyView(modalView), backdropDismiss: false))
    }

    private func processPendingPermissionExpansionIfNeeded() async {
        guard !bluetoothService.isSetupInProgress else { return }
        guard !isPresentingPermissionExpansionAlert else { return }
        guard let pendingPermissionExpansion else { return }

        guard let activeAccountId = await getActiveAccountId(), activeAccountId == pendingPermissionExpansion.accountId else {
            logger.log(
                level: .info,
                tag: tag,
                message: "Discarding queued HealthKit permission expansion because the active account changed."
            )
            clearPendingPermissionExpansion()
            return
        }

        isPresentingPermissionExpansionAlert = true
        try? await Task.sleep(nanoseconds: permissionExpansionAlertDelayNs)

        guard !bluetoothService.isSetupInProgress else {
            isPresentingPermissionExpansionAlert = false
            return
        }

        guard await waitForNotificationSurfaceToClear() else {
            isPresentingPermissionExpansionAlert = false
            schedulePermissionExpansionRetry()
            return
        }

        guard let latestPendingPermissionExpansion = self.pendingPermissionExpansion,
              latestPendingPermissionExpansion.accountId == pendingPermissionExpansion.accountId else {
            isPresentingPermissionExpansionAlert = false
            return
        }

        showPermissionExpansionModal(for: latestPendingPermissionExpansion)
    }

    private func requestQueuedPermissionExpansionAuthorization(accountId: String) async {
        defer {
            isPresentingPermissionExpansionAlert = false
        }

        guard let latestExpansion = await resolvePermissionExpansionIfNeeded(),
              latestExpansion.accountId == accountId else {
            clearPendingPermissionExpansion(accountId: accountId)
            return
        }

        updateAppType(for: latestExpansion.deviceTypes, context: "permission expansion")
        let result = await hkPackage.requestAuthorization()
        if result {
            persistHealthKitPermissionScopeDeviceTypes(latestExpansion.deviceTypes, for: latestExpansion.accountId)
        }

        logger.log(
            level: result ? .success : .error,
            tag: tag,
            message: "Additional HealthKit permissions request completed. "
                + "accountId=\(latestExpansion.accountId), success=\(result), "
                + "deviceTypes=\(latestExpansion.deviceTypes.sorted())"
        )
        clearPendingPermissionExpansion(accountId: latestExpansion.accountId)
    }

    /// Listens for `.scaleAddedOrUpdated` notifications to request additional HealthKit permissions
    /// when a new device type is paired while HealthKit is already integrated.
    private func observeDevicePairing() {
        devicePairingObserver = NotificationCenter.default.addObserver(
            forName: .scaleAddedOrUpdated,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.requestAdditionalPermissionsIfNeeded()
            }
        }
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
            let permissionScopeDeviceTypes = await updateAppTypeForInitialAuthorization()
            let authorizationResult = await hkPackage.requestAuthorization()
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
                persistHealthKitPermissionScopeDeviceTypes(permissionScopeDeviceTypes, for: accountID)
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
                        bmi: entry.scaleEntry?.bmi
                    ))
                }
            }
            return (scaleItems, bpmItems)
        }.value

        // Build HealthKit payloads for scale entries
        var healthKitData = buildHealthKitData(from: scaleExports)

        // Build HealthKit payloads for BPM entries
        for bpmExport in bpmExports {
            healthKitData.append(contentsOf: buildHealthKitData(from: bpmExport))
        }

        logger.log(
            level: .info,
            tag: tag,
            message: "HealthKit full sync prepared payload. accountId=\(accountId), "
                + "scaleEntries=\(scaleExports.count), bpmEntries=\(bpmExports.count), "
                + "payloadCount=\(healthKitData.count)"
        )
        try await saveHealthKitData(finalData: healthKitData)
        logger.log(level: .success, tag: tag, message: "HealthKit full sync completed. accountId=\(accountId), payloadCount=\(healthKitData.count)")
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
        let snapshot = EntrySnapshot(from: entry)
        let healthKitData = buildHealthKitData(from: [snapshot])
        try await hkPackage.saveData(healthKitData)
    }

    /// Writes entry data into Apple Health using an EntryNotification.
    /// This method is safe to call from any actor as it uses extracted data.
    func syncNewData(notification: EntryNotification) async throws {
        logger.log(level: .info, tag: tag, message: "HealthKit sync new entry started. timestamp=\(notification.entryTimestamp), entryType=\(notification.entryType)")
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
        let snapshot = EntrySnapshot(from: entry)
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

    /// Requests additional HealthKit permissions if the paired device types have expanded
    /// since the last authorization (e.g., user added a weight scale after only having BPM).
    /// iOS only prompts for types with `.notDetermined` status — already-granted types are silently skipped.
    public func requestAdditionalPermissionsIfNeeded() async {
        guard let expansion = await resolvePermissionExpansionIfNeeded() else { return }
        queuePermissionExpansion(expansion)
        await processPendingPermissionExpansionIfNeeded()
    }

    /// Returns the expected total permission count based on the user's paired device types.
    public func expectedPermissionCount() async -> Int {
        let deviceTypes = await getConfiguredHealthKitPermissionScopeDeviceTypes()
        let hasBpm = deviceTypes.contains(DeviceType.bpm.rawValue)
        let hasScale = deviceTypes.contains(DeviceType.scale.rawValue)

        switch (hasScale, hasBpm) {
        case (true, true): return 7   // WG (5) + BH (3) - heartRate overlap (1)
        case (false, true): return 3  // Balance Health: heartRate, systolic, diastolic
        default: return 5             // Weight Gurus: weight, leanBodyMass, bodyFat, BMI, heartRate
        }
    }

    /// Removes all Apple Health records previously generated by the app.
    public func clearHealthKit() async throws {
        let accountId = accountService.activeAccount?.accountId ?? "nil"
        logger.log(level: .info, tag: tag, message: "HealthKit clear requested. accountId=\(accountId)")
        clearPendingPermissionExpansion(accountId: accountService.activeAccount?.accountId)
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
    private func fetchAllEntries() async throws -> [Entry] {
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

            // A stored `0` for any metric means the scale could not measure it — treat
            // as missing so we don't push bogus samples or compute derived values from them.
            if let weight = item.weight, weight > 0 {
                healthKitData.append(HealthKitData(
                    type: .weight,
                    value: ConversionTools.convertStoredToLbs(weight),
                    timestamp: timestamp
                ))
            }

            if let bodyFat = item.bodyFat, bodyFat > 0 {
                healthKitData.append(HealthKitData(
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
                healthKitData.append(HealthKitData(
                    type: .leanBodyMass,
                    value: ConversionTools.convertStoredToLbs(muscleMass),
                    timestamp: timestamp
                ))
            } else if let weight = item.weight, weight > 0,
                      let bodyFat = item.bodyFat, bodyFat > 0 {
                let convertedWeight = ConversionTools.convertStoredToLbs(weight)
                let convertedBodyFat = ConversionTools.convertStoredToLbs(bodyFat)
                let leanBodyMass = convertedWeight - (convertedWeight * (convertedBodyFat / 100))
                healthKitData.append(HealthKitData(
                    type: .leanBodyMass,
                    value: leanBodyMass,
                    timestamp: timestamp
                ))
            }

            if let bmi = item.bmi, bmi > 0 {
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
