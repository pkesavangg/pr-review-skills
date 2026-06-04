//
//  ProductTypeStore.swift
//  meApp
//

import Combine
import Foundation

/// Global store that manages the currently selected product type / baby profile
/// and the ordered list of available items in the header dropdown.
///
/// Dropdown order (per Figma):
///   1. My Weight        (if user has a scale device)
///   2. My Blood Pressure (if user has a BPM device)
///   3. [Baby names...]  (one per registered baby profile, sorted by name)
///
/// Registered via ServiceRegistry after login.
/// Access via: @Injector var productTypeStore: ProductTypeStoreProtocol
@MainActor
final class ProductTypeStore: ObservableObject, ProductTypeStoreProtocol {
    private struct AccountSelectionSnapshot: Equatable {
        let accountId: String?
        let productTypes: [String]
    }

    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var babyService: BabyServiceProtocol
    @Injector private var kvStorage: KvStorageServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var logger: LoggerServiceProtocol

    // MARK: - Public State

    /// Ordered list of items for the dropdown, rebuilt whenever devices or baby profiles change.
    @Published private(set) var availableItems: [ProductSelection] = [.myWeight]

    /// The item currently selected in the header dropdown.
    /// Initialized to `.myWeight` as a placeholder; immediately corrected in `init()`
    /// to match the first item in `availableItems`.
    @Published private(set) var selectedItem: ProductSelection = .myWeight

    var selectedItemPublisher: Published<ProductSelection>.Publisher { $selectedItem }
    var availableItemsPublisher: Published<[ProductSelection]>.Publisher { $availableItems }

    private var cancellables = Set<AnyCancellable>()
    private var restoredForAccountId: String?
    private let tag = "ProductTypeStore"

    private static let placeholderBabyProfile = BabyProfile(
        id: BabyProfile.pendingSelectionId,
        name: ProductTypeStrings.babyScale
    )

    // MARK: - Singleton

    static let shared = ProductTypeStore()

    private init() {
        // Ensure selectedItem matches the first available item (not the placeholder default)
        if let first = availableItems.first, selectedItem != first {
            selectedItem = first
        }

        subscribeToChanges()
        rebuild()

        // ProductTypeStore is created inside registerSessionServices(), which is called
        // from AccountService's $activeAccount sink (fires on willSet). At this point the
        // property hasn't been stored yet, so both a direct read and the publisher's
        // initial value are nil. Deferring to the next run-loop iteration ensures
        // activeAccount is fully set.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            Task { @MainActor in
                await self.handleAccountChange(self.accountService.activeAccount?.accountId)
            }
        }

        // Handle future account switches (e.g. multi-account)
        accountService.activeAccountPublisher
            .map { $0?.accountId }
            .removeDuplicates()
            .sink { [weak self] accountId in
                guard let self else { return }
                Task { @MainActor in
                    await self.handleAccountChange(accountId)
                }
            }
            .store(in: &cancellables)

        accountService.activeAccountPublisher
            .map {
                AccountSelectionSnapshot(
                    accountId: $0?.accountId,
                    productTypes: $0?.productTypes ?? []
                )
            }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.rebuild()
            }
            .store(in: &cancellables)
    }

    // MARK: - Public API

    func select(_ item: ProductSelection) {
        guard item != selectedItem else { return }
        selectedItem = item
        persistSelection(item)
        logger.log(level: .info, tag: tag, message: "Switched to \(item.displayName)")
    }

    func autoSelectBaby(babyId: String) {
        if let babyItem = availableItems.first(where: { $0.id == "baby_\(babyId)" }) {
            select(babyItem)
        }
    }

    func selectLastAdded(_ item: ProductSelection) {
        select(item)
    }

    func resetToDefault() {
        if let first = availableItems.first {
            select(first)
        }
    }

    var hasPersistedSelection: Bool {
        guard let accountId = accountService.activeAccount?.accountId else { return false }
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        return kvStorage.getValue(forKey: key) != nil
    }

    // MARK: - Persistence

    private func persistSelection(_ item: ProductSelection) {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        kvStorage.setValue(item.id, forKey: key)
    }

    private func restorePersistedSelection(for accountId: String) {
        guard restoredForAccountId != accountId else { return }

        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        guard let savedId = kvStorage.getValue(forKey: key) as? String else {
            // No persisted selection — mark done so we don't retry on every rebuild.
            restoredForAccountId = accountId
            return
        }
        // The saved item may not yet be in availableItems (e.g. babies still loading).
        // Don't set restoredForAccountId here — leave it unset so rebuild() retries
        // after the next Combine update populates availableItems with the missing item.
        guard let match = availableItems.first(where: { $0.id == savedId }) else { return }

        restoredForAccountId = accountId
        selectedItem = match
    }

    // MARK: - Rebuild on Changes

    private func subscribeToChanges() {
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                self?.syncProductTypesFromDevices(devices)
                self?.rebuild()
            }
            .store(in: &cancellables)

        babyService.babiesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] babies in
                self?.syncProductTypesFromBabies(babies)
                self?.rebuild()
            }
            .store(in: &cancellables)
    }

    /// Ensures account.productTypes stays in sync when devices are added.
    private func syncProductTypesFromDevices(_ devices: [DeviceSnapshot]) {
        guard let account = accountService.activeAccount,
              !account.productTypes.isEmpty else { return }

        var types = account.productTypes
        var changed = false

        let hasWeightScale = devices.contains { $0.deviceType == DeviceType.scale.rawValue }
        let hasBpm = devices.contains { $0.deviceType == DeviceType.bpm.rawValue }
        let hasBabyScale = devices.contains { $0.deviceType == DeviceType.babyScale.rawValue }

        if hasWeightScale && !types.contains("myWeight") {
            types.append("myWeight")
            changed = true
        }

        if hasBpm && !types.contains("myBloodPressure") {
            types.append("myBloodPressure")
            changed = true
        }

        if hasBabyScale && !types.contains("baby") {
            types.append("baby")
            changed = true
        }

        if changed {
            Task {
                try? await accountService.updateProductTypes(types)
            }
            logger.log(
                level: .info,
                tag: tag,
                message: "Synced productTypes=\(types) for accountId=\(account.accountId)"
            )
        }
    }

    private func syncProductTypesFromBabies(_ babies: [Baby]) {
        guard let account = accountService.activeAccount,
              !babies.isEmpty,
              !account.productTypes.contains("baby") else { return }

        var types = account.productTypes
        types.append("baby")
        Task {
            try? await accountService.updateProductTypes(types)
        }
        logger.log(
            level: .info,
            tag: tag,
            message: "Synced baby productType for accountId=\(account.accountId)"
        )
    }

    private func handleAccountChange(_ accountId: String?) async {
        guard let accountId else {
            rebuild()
            return
        }

        do {
            try await babyService.loadBabies(for: accountId)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load babies for selector: \(error)")
        }

        rebuild()
        restorePersistedSelection(for: accountId)
    }

    /// Maps server-side raw product type strings to the app-internal values used by rebuild().
    /// The server stores "weight" / "bpm"; the app checks for "myWeight" / "myBloodPressure".
    private func normalizeProductTypes(_ types: [String]) -> [String] {
        var seen = Set<String>()
        return types.compactMap { type in
            let normalized: String
            switch type {
            case "weight":          normalized = "myWeight"
            case "bpm":             normalized = "myBloodPressure"
            default:                normalized = type
            }
            return seen.insert(normalized).inserted ? normalized : nil
        }
    }

    /// Returns the authoritative product types for the current account.
    ///
    /// Primary path: when `account.productTypes` is non-empty, use it directly
    /// after mapping server-side raw values ("weight" → "myWeight", "bpm" → "myBloodPressure").
    /// Reconstruction path: when `productTypes` is empty (reinstall/new device),
    /// derive from server-synced devices, save back to the account, then return.
    private func resolveProductTypes() -> [String] {
        guard let account = accountService.activeAccount else { return ["myWeight"] }

        if !account.productTypes.isEmpty {
            // The server stores "weight" / "bpm"; the app checks for "myWeight" / "myBloodPressure".
            // Normalize here so rebuild()'s contains() checks always succeed.
            return normalizeProductTypes(account.productTypes)
        }

        // Reconstruction: derive from server-synced devices.
        let devices = scaleService.scales
        var reconstructed: [String] = []

        if devices.contains(where: { $0.deviceType == DeviceType.scale.rawValue }) {
            reconstructed.append("myWeight")
        }
        if devices.contains(where: { $0.deviceType == DeviceType.bpm.rawValue }) {
            reconstructed.append("myBloodPressure")
        }
        if devices.contains(where: { $0.deviceType == DeviceType.babyScale.rawValue }) || !babyService.currentBabies.isEmpty {
            reconstructed.append("baby")
        }
        if reconstructed.isEmpty {
            reconstructed = ["myWeight"]
        }

        Task {
            try? await accountService.updateProductTypes(reconstructed)
        }
        logger.log(
            level: .info,
            tag: tag,
            message: "Reconstructed productTypes=\(reconstructed) for accountId=\(account.accountId)"
        )

        return reconstructed
    }

    private func rebuild() {
        let babies = babyService.currentBabies

        var items: [ProductSelection] = []
        let productTypes = resolveProductTypes()

        // 1. My Weight — present if productTypes contains "myWeight"
        if productTypes.contains("myWeight") {
            items.append(.myWeight)
        }

        // 2. My Blood Pressure — present if productTypes contains "myBloodPressure"
        if productTypes.contains("myBloodPressure") {
            items.append(.myBloodPressure)
        }

        // 3. Individual babies — listed when productTypes contains "baby"
        if productTypes.contains("baby") {
            if babies.isEmpty {
                items.append(.baby(profile: Self.placeholderBabyProfile))
            } else {
                for baby in babies {
                    let profile = BabyProfile(
                        id: baby.id,
                        name: baby.name,
                        deviceId: baby.deviceId,
                        birthday: baby.birthday,
                        biologicalSex: baby.biologicalSex,
                        birthLengthInches: baby.birthLengthInches,
                        birthWeightLbs: baby.birthWeightLbs,
                        birthWeightOz: baby.birthWeightOz
                    )
                    items.append(.baby(profile: profile))
                }
            }
        }

        // Fallback: always show at least "My Weight"
        if items.isEmpty { items = [.myWeight] }

        availableItems = items

        // Restore persisted selection on first rebuild for this account
        if let accountId = accountService.activeAccount?.accountId,
            restoredForAccountId != accountId {
            restorePersistedSelection(for: accountId)
        }

        // Validate the current selection against the new availableItems.
        // Use ID-based lookup rather than full equality so that profile-data updates
        // (e.g. a baby's name or birthday changing) don't silently drop the selection.
        if let refreshed = items.first(where: { $0.id == selectedItem.id }) {
            // Keep the same logical selection but use the freshest profile data.
            if refreshed != selectedItem { selectedItem = refreshed }
        } else {
            // The selected product is no longer available — default to the first item,
            // which respects the Weight → BPM → Baby hierarchy encoded in items order.
            selectedItem = items[0]
        }
    }
}
