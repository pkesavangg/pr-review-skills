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

    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var babyService: BabyServiceProtocol
    @Injector private var kvStorage: KvStorageServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var logger: LoggerServiceProtocol

    // MARK: - Public State

    /// The item currently selected in the header dropdown.
    @Published private(set) var selectedItem: ProductSelection = .myWeight

    var selectedItemPublisher: Published<ProductSelection>.Publisher { $selectedItem }

    /// Ordered list of items for the dropdown, rebuilt whenever devices or baby profiles change.
    /// - Note: Initialized with sample data until real device registration is in place.
    @Published private(set) var availableItems: [ProductSelection] = [
        .myWeight,
        .myBloodPressure,
        .baby(profile: BabyProfile(id: "sample_1", name: "Emma", deviceId: nil)),
        .baby(profile: BabyProfile(id: "sample_2", name: "Liam", deviceId: nil))
    ]

    private var cancellables = Set<AnyCancellable>()
    private var restoredForAccountId: String?
    private let tag = "ProductTypeStore"

    // MARK: - Singleton

    static let shared = ProductTypeStore()

    private init() {
        // Uncomment once real device registration is in place:
        // subscribeToChanges()

        // ProductTypeStore is created inside registerSessionServices(), which is called
        // from AccountService's $activeAccount sink (fires on willSet). At this point the
        // property hasn't been stored yet, so both a direct read and the publisher's
        // initial value are nil. Deferring to the next run-loop iteration ensures
        // activeAccount is fully set.
        DispatchQueue.main.async { [weak self] in
            guard let self, let accountId = self.accountService.activeAccount?.accountId else { return }
            self.restorePersistedSelection(for: accountId)
        }

        // Handle future account switches (e.g. multi-account)
        accountService.activeAccountPublisher
            .compactMap { $0?.accountId }
            .removeDuplicates()
            .sink { [weak self] accountId in
                self?.restorePersistedSelection(for: accountId)
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

    // MARK: - Persistence

    private func persistSelection(_ item: ProductSelection) {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        kvStorage.setValue(item.id, forKey: key)
    }

    private func restorePersistedSelection(for accountId: String) {
        guard restoredForAccountId != accountId else { return }
        restoredForAccountId = accountId

        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        guard let savedId = kvStorage.getValue(forKey: key) as? String,
            let match = availableItems.first(where: { $0.id == savedId }) else { return }
        selectedItem = match
    }

    // MARK: - Rebuild on Changes

    private func subscribeToChanges() {
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.rebuild() }
            .store(in: &cancellables)

        babyService.babiesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.rebuild() }
            .store(in: &cancellables)
    }

    private func rebuild() {
        let devices = scaleService.scales
        let babies = babyService.currentBabies

        var items: [ProductSelection] = []

        // 1. My Weight — present if any scale-type device is registered
        if devices.contains(where: { $0.deviceType == DeviceType.scale.rawValue }) {
            items.append(.myWeight)
        }

        // 2. My Blood Pressure — present if any BPM device is registered
        if devices.contains(where: { $0.deviceType == DeviceType.bpm.rawValue }) {
            items.append(.myBloodPressure)
        }

        // 3. Individual babies — listed whenever baby profiles exist.
        for baby in babies {
            let profile = BabyProfile(id: baby.id, name: baby.name, deviceId: baby.deviceId)
            items.append(.baby(profile: profile))
        }

        // Fallback: always show at least "My Weight"
        if items.isEmpty { items = [.myWeight] }

        availableItems = items

        // Restore persisted selection on first rebuild for this account
        if let accountId = accountService.activeAccount?.accountId,
            restoredForAccountId != accountId {
            restorePersistedSelection(for: accountId)
        }

        // If the current selection is no longer valid, fall back to the first item
        if !items.contains(selectedItem) {
            selectedItem = items[0]
        }
    }
}
