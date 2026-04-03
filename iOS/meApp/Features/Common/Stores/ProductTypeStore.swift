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

    /// Ordered list of items for the dropdown, rebuilt whenever devices or baby profiles change.
    @Published private(set) var availableItems: [ProductSelection] = [.myWeight, .myBloodPressure]

    /// The item currently selected in the header dropdown.
    /// Initialized to `.myWeight` as a placeholder; immediately corrected in `init()`
    /// to match the first item in `availableItems`.
    @Published private(set) var selectedItem: ProductSelection = .myWeight

    var selectedItemPublisher: Published<ProductSelection>.Publisher { $selectedItem }

    private var cancellables = Set<AnyCancellable>()
    private var restoredForAccountId: String?
    private let tag = "ProductTypeStore"

    private static func fallbackBabyProfiles(calendar: Calendar = .current) -> [BabyProfile] {
        let today = calendar.startOfDay(for: Date())
        let liamBirthday = calendar.date(byAdding: .day, value: -112, to: today)
        let stacyBirthday = calendar.date(byAdding: .day, value: -84, to: today)

        return [
            BabyProfile(
                id: "fallback-stacy",
                name: "Stacy",
                birthday: stacyBirthday,
                biologicalSex: "female",
                birthLengthInches: 19.0,
                birthWeightLbs: 6,
                birthWeightOz: 14
            ),
            BabyProfile(
                id: "fallback-liam",
                name: "Liam",
                birthday: liamBirthday,
                biologicalSex: "male",
                birthLengthInches: 20.0,
                birthWeightLbs: 7,
                birthWeightOz: 8
            )
        ]
    }

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

    private func rebuild() {
        let devices = scaleService.scales
        let babies = babyService.currentBabies

        var items: [ProductSelection] = []
        let hasNoLoadedDevices = devices.isEmpty
        let hasWeightDashboard = hasNoLoadedDevices || devices.contains {
            $0.deviceType == DeviceType.scale.rawValue || $0.deviceType == DeviceType.babyScale.rawValue
        }
        let hasBpmDashboard = hasNoLoadedDevices || devices.contains {
            $0.deviceType == DeviceType.bpm.rawValue
        }

        // 1. My Weight — present if any scale-type device is registered
        if hasWeightDashboard {
            items.append(.myWeight)
        }

        // 2. My Blood Pressure — present if any BPM device is registered
        if hasBpmDashboard {
            items.append(.myBloodPressure)
        }

        // 3. Individual babies — listed whenever baby profiles exist.
        if babies.isEmpty {
            Self.fallbackBabyProfiles().forEach { items.append(.baby(profile: $0)) }
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
