//
//  HistoryStore.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//
// swiftlint:disable file_length
import Combine
import Foundation
import SwiftUI

/// Store / ViewModel that powers the History feature (monthly summaries, month detail, entry detail, metric info).
@MainActor
// swiftlint:disable:next type_body_length
final class HistoryStore: ObservableObject {

    // MARK: - Dependencies
    @Injector var entryService: EntryServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var accountService: AccountServiceProtocol
    @Injector var productTypeStore: ProductTypeStoreProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol

    // MARK: - Summary Screen State
    @Published private(set) var months: [HistoryMonth] = []

    // MARK: - Month Detail State
    @Published private(set) var selectedMonth: HistoryMonth?
    @Published private(set) var entries: [EntrySnapshot] = []

    /// Set of entry ids that are currently expanded in the Month Detail screen.
    @Published var expandedEntries: Set<String> = []

    // MARK: - Metric Info State
    @Published private(set) var selectedMetric: BodyMetric?

    // MARK: - Blood Pressure State
    @Published private(set) var bpMonths: [BPHistoryMonth] = []
    @Published private(set) var bpEntries: [BPHistoryEntry] = []
    @Published private(set) var selectedBPMonth: BPHistoryMonth?
    @Published var expandedBPEntries: Set<String> = []

    // MARK: - Baby State
    @Published private(set) var babyWeeks: [BabyHistoryWeek] = []
    @Published private(set) var babyEntries: [BabyHistoryEntry] = []
    @Published private(set) var selectedBabyDay: BabyHistoryDay?
    @Published var expandedBabyEntries: Set<String> = []

    // MARK: - Optimistic Delete / Undo State
    private var pendingBPDelete: BPHistoryEntry?
    private var pendingBabyDelete: BabyHistoryEntry?
    private var pendingWGDelete: EntrySnapshot?

    /// Whether the current product type selection is blood pressure.
    var isBloodPressureMode: Bool {
        productTypeStore.selectedItem == .myBloodPressure
    }

    /// Whether the current product type selection is a baby profile.
    var isBabyMode: Bool {
        if case .baby = productTypeStore.selectedItem { return true }
        return false
    }

    /// The baby id of the current selection, or `nil` when a non-baby product is selected.
    /// Used to scope unified `/v3/entries/` reads and CSV export to a single baby.
    private var selectedBabyId: String? {
        if case .baby(let profile) = productTypeStore.selectedItem { return profile.id }
        return nil
    }

    // MARK: - UI Flags
    @Published var isEmptyState: Bool = false

    /// Whether the History screen is currently on-screen. Set by `HistoryListScreen`
    /// on tab change. When false, `entrySaved`/`entryDeleted` only invalidate the months
    /// cache instead of eagerly re-reading the full ~10k dataset — the next History open
    /// reloads fresh. This removes a full-dataset read from every off-screen save on a
    /// large account (MOB-1433 §5c).
    var isHistoryScreenActive = false

    /// The local `DeviceType.rawValue`s of every currently paired device, mirrored from
    /// `deviceService.scalesPublisher` so the empty state re-renders when a device is
    /// paired/unpaired. Drives the "no device" vs. "no measurement" empty-state split.
    @Published private(set) var pairedDeviceTypes: Set<String> = []

    /// Whether a device matching the currently selected product is paired.
    /// `.myWeight` → weight scale, `.myBloodPressure` → BP monitor, `.baby` → baby scale.
    var hasPairedDeviceForCurrentProduct: Bool {
        pairedDeviceTypes.contains(productTypeStore.selectedItem.deviceType.rawValue)
    }

    // MARK: - Cursor Pagination State (Remote Read)
    /// Accumulated entries pulled from the unified `GET /v3/entries/` cursor endpoint.
    @Published private(set) var pagedEntries: [BathScaleOperationDTO] = []
    /// Whether the server reported more pages beyond what has been loaded.
    @Published private(set) var hasMorePages: Bool = false
    /// Whether a page request is currently in flight (drives the list footer spinner).
    @Published private(set) var isLoadingPage: Bool = false
    /// True once loadFirstPage has completed at least one fetch (even if it returned empty).
    /// Guards loadNextPage against re-fetching page 1 for accounts with no remote entries.
    @Published private(set) var hasLoadedFirstPage: Bool = false

    /// The cursor for the next page request — the `entryTimestamp` of the last loaded row.
    private var nextCursor: String?

    private var cancellables = Set<AnyCancellable>()

    // MARK: - Language Strings
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self

    /// Logger tag for this store
    private let tag = "HistoryStore"
    private var loadedProductTypes: Set<String> = []
    private var monthsLoadTask: Task<Void, Never>?

    /// MOB-516: coalesced-reload state. The entry-saved/deleted sinks used to
    /// `monthsLoadTask?.cancel()` + restart, but cancelling the Swift task does NOT stop the
    /// underlying `getMonthsAll` worker read — so a burst of triggers on one add stacked 2–3
    /// full-table reads on the serial worker (~6.6 s "stuck" loader). `requestMonthsReload`
    /// replaces that with a single driver that reruns at most once more. See below.
    private var monthsReloadPending = false
    private var monthsReloadDriver: Task<Void, Never>?

    // MARK: - Init ------------------------------------------------------

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    init() {
        // Seed + track paired devices so the empty state can distinguish "no device"
        // from "device paired, no measurement" and update live as devices are paired.
        pairedDeviceTypes = Set(deviceService.scales.compactMap { $0.deviceType })
        deviceService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                self?.pairedDeviceTypes = Set(scales.compactMap { $0.deviceType })
            }
            .store(in: &cancellables)

        entryService.entrySaved
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task { @MainActor in
                    // MOB-516: no cancel-and-restart here — cancelling the Swift task didn't stop
                    // the worker read, so bursts stacked full-table reads. `requestMonthsReload`
                    // below supersedes any in-flight load without orphaning it.
                    self.invalidateCacheForCurrentType()
                    // Baby entries may be assigned while a different product type is active,
                    // meaning invalidateCacheForCurrentType() won't clear the baby cache.
                    // Always flush baby history cache on any baby entry event so the next
                    // History open fetches fresh data regardless of current product mode.
                    if entry.entryType == EntryType.baby.rawValue {
                        self.loadedProductTypes = self.loadedProductTypes.filter { !$0.hasPrefix("baby_") }
                    }
                    // MOB-1433 §5c: only eagerly re-read the months list (a full ~10k read on
                    // a large account) when History is on screen. Off-screen — e.g. saving
                    // from the Entry tab — the cache invalidation above is enough; the next
                    // History open reloads fresh. Removes a full-dataset read from every
                    // off-screen save.
                    guard self.isHistoryScreenActive else { return }
                    // MOB-516: coalesced reload — reruns once if more changes arrive, never
                    // stacking concurrent worker reads (the ~6.6 s stuck loader).
                    self.requestMonthsReload(canShowLoader: false, reason: "entrySaved")
                    // If we're viewing a month and the saved entry belongs to that month, refresh entries inline
                    if let selectedMonth = self.selectedMonth {
                        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
                        if monthKey == selectedMonth.id {
                            await self.loadEntries(for: selectedMonth, showLoader: false)
                        }
                    }
                    // Refresh BP month detail if viewing one
                    if let selectedBPMonth = self.selectedBPMonth {
                        self.selectBPMonth(selectedBPMonth)
                    }
                    // Refresh baby day detail if viewing one
                    if let selectedBabyDay = self.selectedBabyDay {
                        self.selectBabyDay(selectedBabyDay)
                    }
                }
            }
            .store(in: &cancellables)
        entryService.entryDeleted
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task { @MainActor in
                    // MOB-516: no cancel-and-restart here — see requestMonthsReload.
                    self.invalidateCacheForCurrentType()
                    // MOB-1433 §5c: eager months-list reload only when History is on screen
                    // (deletes happen from within History, so this stays correct); otherwise
                    // just invalidate and let the next open reload fresh.
                    guard self.isHistoryScreenActive else { return }
                    // MOB-516: coalesced reload (see requestMonthsReload).
                    self.requestMonthsReload(canShowLoader: false, reason: "entryDeleted")
                    // If we're viewing a month and the deleted entry belongs to that month, refresh entries inline
                    if let selectedMonth = self.selectedMonth {
                        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
                        if monthKey == selectedMonth.id {
                            await self.loadEntries(for: selectedMonth, showLoader: false)
                        }
                    }
                    // Refresh BP month detail if viewing one
                    if let selectedBPMonth = self.selectedBPMonth {
                        self.selectBPMonth(selectedBPMonth)
                    }
                    // Refresh baby day detail if viewing one
                    if let selectedBabyDay = self.selectedBabyDay {
                        self.selectBabyDay(selectedBabyDay)
                    }
                }
            }
            .store(in: &cancellables)

        // Reload history when the user switches product type in the header dropdown
        productTypeStore.selectedItemPublisher
            .dropFirst()
            .debounce(for: .milliseconds(300), scheduler: DispatchQueue.main)
            .sink { [weak self] newItem in
                guard let self = self else { return }
                self.monthsLoadTask?.cancel()
                self.monthsLoadTask = nil
                // Always force a fresh load when product type changes — the selected baby
                // may have received new entries since we last viewed it.
                self.loadedProductTypes.remove(newItem.id)
                // Reload now only when History is on screen; otherwise the invalidation
                // above is enough and the next open reloads fresh (MOB-1433 §5c).
                guard self.isHistoryScreenActive else { return }
                self.loadMonths(reason: "productTypeChange")
            }
            .store(in: &cancellables)

        // Reload history when the unit type changes so pre-formatted display strings
        // (baby weightDisplay/lengthDisplay) reflect the newly selected unit.
        accountService.activeAccountPublisher
            .dropFirst()
            .compactMap { $0 }
            .removeDuplicates {
                $0.weightUnit == $1.weightUnit && $0.measurementUnits == $1.measurementUnits
            }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.monthsLoadTask?.cancel()
                self.monthsLoadTask = nil
                self.loadedProductTypes.removeAll()
                // The account refresh at launch republishes the account while the user is on
                // the Dashboard — reloading the full months list here fired a stray ~10k read
                // that contended with the dashboard load (MOB-1433 §5c). Only reload when
                // History is on screen (e.g. a live unit change while viewing it).
                guard self.isHistoryScreenActive else { return }
                self.loadMonths(reason: "accountUnitChange")
                if let selectedBabyDay = self.selectedBabyDay {
                    self.selectBabyDay(selectedBabyDay)
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Public API --------------------------------------------------
    /// Coalesced months reload (MOB-516). Lets any load already running finish (its data may
    /// predate this change), then does ONE fresh load, rerunning exactly once more if another
    /// change arrived meanwhile. It never spawns concurrent full-table reads — that stacking on
    /// the serial worker was the ~6.6 s "stuck" History loader after adding an entry. Use this
    /// from the reactive sinks instead of cancel-and-restart.
    private func requestMonthsReload(canShowLoader: Bool, reason: String = "unknown") {
        monthsReloadPending = true
        guard monthsReloadDriver == nil else { return }
        monthsReloadDriver = Task { [weak self] in
            guard let self else { return }
            await self.monthsLoadTask?.value
            while self.monthsReloadPending {
                self.monthsReloadPending = false
                await self.loadMonthsInternal(canShowLoader: canShowLoader, reason: reason)
            }
            self.monthsReloadDriver = nil
        }
    }

    /// Call onAppear of History list screen.
    func loadMonths(reason: String = "loadMonths") {
        let currentId = productTypeStore.selectedItem.id
        guard !loadedProductTypes.contains(currentId) else {
            updateEmptyStateFromCache()
            return
        }
        loadedProductTypes.insert(currentId)
        Task { [weak self] in await self?.loadMonthsInternal(reason: reason) }
    }

    /// User tapped a month row.
    func selectMonth(_ month: HistoryMonth) {
        selectedMonth = month
        Task { [weak self] in
            await self?.loadEntries(for: month)
        }
    }

    func setSelectedMonth(selectedMonth: HistoryMonth) {
        self.selectedMonth = selectedMonth
        entries = []
    }

    func resetSelectedMonth() {
        selectedMonth = nil
        entries = []
    }

    /// User tapped a metric inside an expanded entry.
    func selectMetric(_ metric: BodyMetric) {
        selectedMetric = metric
    }

    func refreshAllEntries() async {
        invalidateCacheForCurrentType()
        // Refresh account data to ensure we have latest unit settings
        try? await accountService.refreshAccount()
        await entryService.syncAllEntriesWithRemote()
        await loadMonthsInternal(canShowLoader: false)
        // Refresh whichever product detail is currently open so pull-to-refresh
        // updates the visible detail screen for weight, BP, and baby alike.
        if let selectedMonth {
            await loadEntries(for: selectedMonth, showLoader: false)
        }
        if let selectedBPMonth {
            selectBPMonth(selectedBPMonth)
        }
        if let selectedBabyDay {
            selectBabyDay(selectedBabyDay)
        }
    }

    /// Presents a delete entry confirmation alert.
    /// - Parameters:
    ///   - entry: The entry to be deleted.
    ///   - onConfirm: Executed when user confirms deletion.
    ///   - onCancel:  Executed when user cancels (optional).
    func showDeleteEntryAlert(entry: EntrySnapshot, onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    onCancel?()
                    self.confirmWGDelete(entry)
                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    self.notificationService.dismissAlert()
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Handle export
    func handleExport() {
        let title = isBabyMode
            ? HistoryListStrings.sendBabyHistory
            : isBloodPressureMode
                ? HistoryListStrings.sendBPHistory
                : alertLang.CsvExportAlert.title
        let alert = AlertModel(
            title: title,
            message: alertLang.CsvExportAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.CsvExportAlert.sendButton, type: .primary) { _ in
                    self.exportData()
                },
                AlertButtonModel(title: alertLang.CsvExportAlert.cancelButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Internal helpers -------------------------------------------

    private func updateEmptyStateFromCache() {
        if isBloodPressureMode {
            isEmptyState = bpMonths.isEmpty
        } else if isBabyMode {
            isEmptyState = babyWeeks.isEmpty
        } else {
            isEmptyState = months.isEmpty
        }
    }

    private func invalidateCacheForCurrentType() {
        let currentId = productTypeStore.selectedItem.id
        loadedProductTypes.remove(currentId)
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func loadMonthsInternal(canShowLoader: Bool = true, reason: String = "unknown") async {
        // If a load is already in progress, wait for it to complete rather than
        // returning early. This prevents a stale empty-state flash when a tab-switch
        // triggers loadMonths() while an entrySaved reload is still fetching data.
        if let existingTask = monthsLoadTask {
            await existingTask.value
            return
        }

        // Blood pressure mode — load from local BPM entries
        if isBloodPressureMode {
            monthsLoadTask = Task { [weak self] in
                guard let self else { return }
                do {
                    let dtos = try await self.entryService.fetchBpmEntries()
                    let result = self.mapBpmDTOsToMonths(dtos)
                    self.bpMonths = result
                    self.isEmptyState = result.isEmpty
                } catch {
                    self.logger.log(level: .error, tag: self.tag, message: "Failed to load BP history (reason=\(reason)): \(error.localizedDescription)")
                    self.bpMonths = []
                    self.isEmptyState = true
                }
                self.monthsLoadTask = nil
            }
            await monthsLoadTask?.value
            return
        }

        // Baby mode — load from local baby entries
        if isBabyMode {
            monthsLoadTask = Task { [weak self] in
                guard let self else { return }
                do {
                    let allEntries = try await self.entryService.fetchAllEntrySnapshots()
                    let babyProfile: BabyProfile? = {
                        if case .baby(let profile) = self.productTypeStore.selectedItem { return profile }
                        return nil
                    }()
                    let babyEntries = self.babyCreateEntries(from: allEntries, profile: babyProfile)
                    let result = self.mapBabyEntriesToWeeks(babyEntries, profile: babyProfile)
                    self.babyWeeks = result
                    self.isEmptyState = result.isEmpty
                } catch {
                    self.logger.log(level: .error, tag: self.tag, message: "Failed to load baby history (reason=\(reason)): \(error.localizedDescription)")
                    self.babyWeeks = []
                    self.isEmptyState = true
                }
                self.monthsLoadTask = nil
            }
            await monthsLoadTask?.value
            return
        }

        monthsLoadTask = Task { [weak self] in
            guard let self else { return }
            if canShowLoader { self.notificationService.showLoader(LoaderModel(text: loaderLang.loading)) }
            defer {
                if canShowLoader { self.notificationService.dismissLoader() }
                self.monthsLoadTask = nil
            }

            do {
                let result = try await self.entryService.getMonthsAll()
                self.months = result
                self.isEmptyState = result.isEmpty
                self.logger.log(
                    level: .info,
                    tag: self.tag,
                    message: "Loaded weight history months: count=\(result.count), isEmptyState=\(result.isEmpty), reason=\(reason)"
                )
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to load history months (reason=\(reason)): \(error.localizedDescription)")
                self.months = []
                self.isEmptyState = true
            }
        }
        await monthsLoadTask?.value
    }

    func loadEntries(for month: HistoryMonth? = nil, showLoader: Bool = true) async {
        let selectedMonth = month ?? self.selectedMonth
        guard let selectedMonth else { return }

        do {
            let fetched = try await entryService.fetchEntrySnapshots(forMonth: selectedMonth.id)

            // UI-level deduplication:
            // Group by entry identity (serverEntryId when present, else the unique local id)
            // and keep the latest operation by serverTimestamp. Keying on entry identity —
            // NOT entryTimestamp — ensures distinct entries that share an entryTimestamp are
            // each shown, while multiple operations of the same entry still collapse to one.
            let grouped = Dictionary(grouping: fetched) { $0.serverEntryId ?? $0.id.uuidString }
            let latestPerEntry: [EntrySnapshot] = grouped.compactMap { _, values in
                values.max { ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "") }
            }
            // Show only final creates; hide deletes
            let visible = latestPerEntry.filter { $0.operationType == OperationType.create.rawValue }
            // Sort newest first by entryTimestamp
            let pairs = visible.map { entry -> (EntrySnapshot, Int64) in
                (entry, DateTimeTools.getTimestamp(entry.entryTimestamp))
            }
            let sorted = pairs.sorted { $0.1 > $1.1 }.map { $0.0 }
            self.entries = sorted
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load month entries: monthId=\(selectedMonth.id), error=\(error.localizedDescription)")
            self.entries = []
        }
    }

    private func deleteEntryInternal(entryId: UUID) async {
        do {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingEntry))
            try await entryService.deleteEntry(entryId: entryId)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete entry:", data: error.localizedDescription)
        }
        notificationService.dismissLoader()
    }

    // MARK: - BP Delete (optimistic + 3-second UNDO)

    func showDeleteBPEntryAlert(entry: BPHistoryEntry) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    self.confirmBPDelete(entry)
                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    self.notificationService.dismissAlert()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func confirmBPDelete(_ entry: BPHistoryEntry) {
        bpEntries.removeAll { $0.id == entry.id }
        pendingBPDelete = entry

        let label = bpEntryLabel(entry)
        notificationService.showToast(ToastModel(
            message: "\(label) \(HistoryListStrings.readingDeleted)",
            btnTextView: AnyView(Text(HistoryListStrings.undo).fontOpenSans(.button1)),
            onClick: { Task { @MainActor in self.undoBPDelete() } },
            onDismiss: { Task { @MainActor in self.commitBPDelete() } }
        ))
    }

    func undoBPDelete() {
        guard let entry = pendingBPDelete else { return }
        pendingBPDelete = nil
        bpEntries.append(entry)
        bpEntries.sort { $0.entryTimestamp > $1.entryTimestamp }
        let label = bpEntryLabel(entry)
        notificationService.showToast(ToastModel(message: "\(label) \(HistoryListStrings.readingRestored)"))
    }

    private func commitBPDelete() {
        guard let entry = pendingBPDelete else { return }
        pendingBPDelete = nil
        Task { await deleteBPEntryInternal(entry) }
    }

    private func deleteBPEntryInternal(_ entry: BPHistoryEntry) async {
        do {
            try await entryService.deleteBpmEntry(entryTimestamp: entry.entryTimestamp)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete BP entry: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(
                message: HistoryListStrings.couldntDelete,
                btnTextView: AnyView(Text(HistoryListStrings.tryAgain).fontOpenSans(.button1)),
                onClick: { Task { @MainActor in self.confirmBPDelete(entry) } },
                isError: true
            ))
        }
    }

    private func bpEntryLabel(_ entry: BPHistoryEntry) -> String {
        let date = DateTimeTools.getFormattedDay(entry.entryTimestamp)
        let time = DateTimeTools.getFormattedTime(entry.entryTimestamp)
        return "\(date) (\(time))"
    }

    // MARK: - Baby Delete (optimistic + 3-second UNDO)

    func showDeleteBabyEntryAlert(entry: BabyHistoryEntry) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    self.confirmBabyDelete(entry)
                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    self.notificationService.dismissAlert()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func confirmBabyDelete(_ entry: BabyHistoryEntry) {
        babyEntries.removeAll { $0.id == entry.id }
        pendingBabyDelete = entry

        let label = DateTimeTools.getArrivalRelativeTime(fromISOString: entry.entryTimestamp)
            ?? DateTimeTools.getFormattedTime(entry.entryTimestamp)

        notificationService.showToast(ToastModel(
            message: "\(label): \(HistoryListStrings.readingDeleted)",
            btnTextView: AnyView(Text(HistoryListStrings.undo).fontOpenSans(.button1)),
            onClick: { Task { @MainActor in self.undoBabyDelete() } },
            onDismiss: { Task { @MainActor in self.commitBabyDelete() } }
        ))
    }

    func undoBabyDelete() {
        guard let entry = pendingBabyDelete else { return }
        pendingBabyDelete = nil
        babyEntries.append(entry)
        babyEntries.sort { $0.entryTimestamp > $1.entryTimestamp }
        notificationService.showToast(ToastModel(message: HistoryListStrings.readingRestored))
    }

    private func commitBabyDelete() {
        guard let entry = pendingBabyDelete else { return }
        pendingBabyDelete = nil
        Task { await deleteBabyEntryInternal(entry) }
    }

    private func deleteBabyEntryInternal(_ babyEntry: BabyHistoryEntry) async {
        do {
            try await entryService.deleteEntry(entryId: babyEntry.id)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete baby entry: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(
                message: HistoryListStrings.couldntDelete,
                btnTextView: AnyView(Text(HistoryListStrings.tryAgain).fontOpenSans(.button1)),
                onClick: { Task { @MainActor in self.confirmBabyDelete(babyEntry) } },
                isError: true
            ))
        }
    }

    // MARK: - WG Delete (optimistic + 3-second UNDO)

    private func confirmWGDelete(_ entry: EntrySnapshot) {
        entries.removeAll { $0.id == entry.id }
        pendingWGDelete = entry

        let time = DateTimeTools.getFormattedTime(entry.entryTimestamp)
        notificationService.showToast(ToastModel(
            message: "\(time) \(HistoryListStrings.readingDeleted)",
            btnTextView: AnyView(Text(HistoryListStrings.undo).fontOpenSans(.button1)),
            onClick: { Task { @MainActor in self.undoWGDelete() } },
            onDismiss: { Task { @MainActor in self.commitWGDelete() } }
        ))
    }

    func undoWGDelete() {
        guard let entry = pendingWGDelete else { return }
        pendingWGDelete = nil
        entries.append(entry)
        entries.sort { $0.entryTimestamp > $1.entryTimestamp }
        notificationService.showToast(ToastModel(message: HistoryListStrings.readingRestored))
    }

    private func commitWGDelete() {
        guard let entry = pendingWGDelete else { return }
        pendingWGDelete = nil
        Task { await deleteWGEntryInternal(entry) }
    }

    private func deleteWGEntryInternal(_ entry: EntrySnapshot) async {
        do {
            try await entryService.deleteEntry(entryId: entry.id)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete WG entry: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(
                message: HistoryListStrings.couldntDelete,
                btnTextView: AnyView(Text(HistoryListStrings.tryAgain).fontOpenSans(.button1)),
                onClick: { Task { @MainActor in self.confirmWGDelete(entry) } },
                isError: true
            ))
        }
    }

    // MARK: - BP Edit (delete-old + create-new)

    // swiftlint:disable:next function_parameter_count
    func updateBPEntry(
        old: BPHistoryEntry,
        systolic: Int,
        diastolic: Int,
        pulse: Int,
        note: String,
        entryTimestamp: String
    ) async {
        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer { notificationService.dismissLoader() }
        do {
            let meanArterial = String(Int(round(Double(diastolic) + (Double(systolic) - Double(diastolic)) / 3.0)))
            let dto = BpmOperationDTO(
                accountId: nil,
                systolic: Double(systolic),
                diastolic: Double(diastolic),
                pulse: Double(pulse),
                meanArterial: meanArterial,
                note: note.isEmpty ? nil : note,
                // Preserve the original source: a device-synced reading is edited note-only,
                // so re-saving must not relabel it as manual (MOB-1172).
                source: old.source ?? EntrySource.manual.rawValue,
                unit: nil,
                entryTimestamp: entryTimestamp,
                operationType: nil,
                serverTimestamp: nil
            )
            // Delete first so that a shared timestamp (user kept the original)
            // doesn't cause deleteBpmEntry to match and remove the freshly created entry.
            try await entryService.deleteBpmEntry(entryTimestamp: old.entryTimestamp)
            do {
                try await entryService.createBpmEntry(dto)
            } catch {
                // Delete succeeded but create failed — the original entry is gone.
                // Bubble a distinct log so support can distinguish this from a plain save error.
                logger.log(level: .error, tag: tag, message: "BP entry create failed after delete (entry lost): \(error.localizedDescription)")
                notificationService.showToast(ToastModel(message: toastLang.errorSavingEntry))
                return
            }
            logger.log(level: .info, tag: tag, message: "BP entry updated: \(entryTimestamp)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete BP entry during update: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(message: toastLang.errorSavingEntry))
        }
    }

    // MARK: - Weight Edit (delete-old + create-new)

    /// Trims a note and collapses a blank/whitespace-only value to nil, matching the manual
    /// create path (EntryStore.saveEntry) so an empty edit clears the note rather than storing "".
    private static func normalizedNote(_ note: String) -> String? {
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    // swiftlint:disable function_parameter_count
    /// Updates a weight-scale entry. Editable core fields (weight + bmi/body-fat/muscle-mass/
    /// body-water) come from the edit sheet; all other body metrics on the original entry are
    /// preserved verbatim. The original `source` is kept so a device-synced reading edited
    /// note-only is not relabeled as manual (MOB-1172).
    ///
    /// No PATCH endpoint exists, so this creates the replacement first, then deletes the
    /// original by its id (a distinct UUID, so a shared timestamp can't collapse them).
    ///
    /// Returns `true` only when the replacement saved AND the original was deleted, so the caller
    /// can gate post-save navigation (e.g. popping back to the list) on a confirmed success.
    @discardableResult
    func updateWGEntry(
        old: EntrySnapshot,
        weight: Int,
        bmi: Int?,
        bodyFat: Int?,
        muscleMass: Int?,
        water: Int?,
        note: String,
        entryTimestamp: String
    ) async -> Bool {
        guard let accountId = accountService.activeAccount?.accountId else { return false }
        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer { notificationService.dismissLoader() }

        let scaleEntry = BathScaleEntry(
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: old.scaleEntry?.source ?? EntrySource.manual.rawValue
        )

        // Preserve every non-core metric from the original entry — the edit sheet does not
        // expose them, so they must round-trip untouched.
        let scaleMetric = Self.preservedMetric(from: old.scaleEntryMetric)

        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue,
            entryType: EntryType.scale.rawValue,
            isSynced: false
        )
        entry.note = Self.normalizedNote(note)
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric

        do {
            try await entryService.saveNewEntry(entry)
            do {
                try await entryService.deleteEntry(entryId: old.id)
            } catch {
                // Save succeeded but the delete of the original failed — both the old and the
                // replacement now exist. Bubble a distinct log so support can distinguish this
                // duplicate from a plain save error (a blind retry would create a third copy).
                let duplicateMessage = "Weight entry delete-after-save failed (duplicate created), "
                    + "original id \(old.id): \(error.localizedDescription)"
                logger.log(level: .error, tag: tag, message: duplicateMessage)
                notificationService.showToast(ToastModel(message: toastLang.errorSavingEntry))
                return false
            }
            logger.log(level: .info, tag: tag, message: "Weight entry updated: \(entryTimestamp)")
            return true
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update weight entry: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(message: toastLang.errorSavingEntry))
            return false
        }
    }
    // swiftlint:enable function_parameter_count

    /// Rebuilds a `BathScaleMetric` from the original entry's snapshot so the non-core metrics the
    /// weight edit sheet does not expose round-trip untouched onto the replacement entry.
    private static func preservedMetric(from oldMetric: BathScaleMetricSnapshot?) -> BathScaleMetric {
        BathScaleMetric(
            bmr: oldMetric?.bmr,
            metabolicAge: oldMetric?.metabolicAge,
            proteinPercent: oldMetric?.proteinPercent,
            pulse: oldMetric?.pulse,
            skeletalMusclePercent: oldMetric?.skeletalMusclePercent,
            subcutaneousFatPercent: oldMetric?.subcutaneousFatPercent,
            visceralFatLevel: oldMetric?.visceralFatLevel,
            boneMass: oldMetric?.boneMass,
            impedance: oldMetric?.impedance,
            unit: oldMetric?.unit
        )
    }

    // MARK: - Baby Edit (delete-old + create-new)

    func updateBabyEntry(
        old: BabyHistoryEntry,
        note: String,
        weightDecigrams: Int,
        lengthMm: Int,
        entryTimestamp: String
    ) async {
        guard case .baby(let profile) = productTypeStore.selectedItem,
              !profile.isPendingSelection else { return }

        notificationService.showLoader(LoaderModel(text: loaderLang.savingEntry))
        defer { notificationService.dismissLoader() }
        do {
            try await entryService.createBabyEntry(
                babyId: profile.id,
                weight: weightDecigrams,
                length: lengthMm,
                note: note,
                entryTimestamp: entryTimestamp,
                // Preserve the original source so a device-synced (baby-scale) reading edited
                // note-only is not relabeled as manual (MOB-1172).
                source: old.source
            )
            try await entryService.deleteEntry(entryId: old.id)
            logger.log(level: .info, tag: tag, message: "Baby entry updated: \(entryTimestamp)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update baby entry: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(message: toastLang.errorSavingEntry))
        }
    }

    // MARK: - Cursor Pagination (Remote Read)

    /// Loads the first page of remote entries for the current product selection,
    /// resetting any previously accumulated pages.
    func loadFirstPage() async {
        nextCursor = nil
        pagedEntries = []
        hasMorePages = false
        hasLoadedFirstPage = false
        await loadNextPage()
    }

    /// Loads the next page of remote entries and appends it to `pagedEntries`.
    ///
    /// No-ops while a request is already in flight, or once the server has reported there
    /// are no more pages (after at least one page has been fetched).
    func loadNextPage() async {
        guard !isLoadingPage else { return }
        guard hasMorePages || !hasLoadedFirstPage else { return }

        isLoadingPage = true
        defer { isLoadingPage = false }

        do {
            let category = productTypeStore.selectedItem.entriesCategory
            let page = try await entryService.fetchEntriesPage(
                cursor: nextCursor,
                limit: EntriesPagination.defaultLimit,
                category: category,
                babyId: selectedBabyId
            )
            pagedEntries.append(contentsOf: page.entries)
            nextCursor = page.nextCursor
            hasMorePages = page.hasMore
            hasLoadedFirstPage = true
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load entries page: \(error.localizedDescription)")
            hasMorePages = false
            hasLoadedFirstPage = true
        }
    }

    // MARK: - Export Data
    private func exportData() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingCsv))
            do {
                try await entryService.exportCSV(category: productTypeStore.selectedItem.entriesCategory, babyId: selectedBabyId)
                notificationService.showToast(ToastModel(message: toastLang.csvExported))
            } catch {
                logger.log(level: .error, tag: tag, message: "CSV export failed:", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet:
                    break
                default:
                    notificationService.showToast(ToastModel(
                        message: toastLang.csvExportError)
                    )
                }
            }
            notificationService.dismissLoader()
        }
    }

    // MARK: - Blood Pressure API

    /// User tapped a BP month row.
    func selectBPMonth(_ month: BPHistoryMonth) {
        selectedBPMonth = month
        Task {
            do {
                let dtos = try await entryService.fetchBpmEntries()
                bpEntries = mapBpmDTOsToEntries(dtos, monthId: month.id)
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to load BP entries for month \(month.id): \(error.localizedDescription)")
                bpEntries = []
            }
        }
    }

    func resetSelectedBPMonth() {
        selectedBPMonth = nil
        bpEntries = []
    }

    // MARK: - Baby API

    /// Whether the active account uses metric units for baby entries.
    /// Uses the baby-specific `measurementUnits` field (set via "My Kids Unit Type"),
    /// not the adult weight unit, so baby history reflects the correct unit selection.
    var isMetric: Bool {
        accountService.activeAccount?.measurementUnits == MeasurementUnits.metric.rawValue
    }

    /// Adult weight unit (kg vs lb) for the active account — drives weight-entry display/edit
    /// conversions. Distinct from `isMetric`, which reflects the baby measurement-unit selection.
    var isWeightMetric: Bool {
        accountService.activeAccount?.weightUnit == .kg
    }

    private var currentMeasurementUnits: MeasurementUnits {
        guard let raw = accountService.activeAccount?.measurementUnits,
              let units = MeasurementUnits(rawValue: raw) else { return .imperialLbOz }
        return units
    }

    /// User tapped a baby day row.
    func selectBabyDay(_ day: BabyHistoryDay) { // swiftlint:disable:this function_body_length
        selectedBabyDay = day
        Task {
            do {
                let allEntries = try await entryService.fetchAllEntrySnapshots()
                let babyProfile: BabyProfile? = {
                    if case .baby(let profile) = productTypeStore.selectedItem { return profile }
                    return nil
                }()
                let dayEntries = self.babyCreateEntries(from: allEntries, profile: babyProfile).filter {
                    self.localDayString(from: $0.entryTimestamp) == day.id
                }
                let units = self.currentMeasurementUnits
                let metric = units == .metric
                babyEntries = dayEntries.compactMap { entry -> BabyHistoryEntry? in
                    guard let baby = entry.babyEntry else { return nil }
                    let decigrams = baby.weight
                    let source = baby.source
                    let displayUnit: ConversionTools.BabyDisplayUnit = metric ? .kg : .lbOz
                    let graduatedDecigrams = ConversionTools.convertToDisplayWeightBase(
                        decigrams: decigrams, source: source, unit: displayUnit, isBabyScaleEntry: true
                    )
                    let lbsOz = ConversionTools.convertBabyDecigramsToLbsOz(graduatedDecigrams)
                    let kg = ConversionTools.convertBabyDecigramsToKg(graduatedDecigrams)
                    let lbDecimal = ConversionTools.convertBabyDecigramsToLb(graduatedDecigrams)
                    let mm = baby.length
                    let lengthInches = ConversionTools.convertBabyMmToInches(mm)
                    let lengthCm = ConversionTools.convertBabyMmToCm(mm)
                    let pct = BabyWeightPercentileCalculator.calculatePercentile(
                        weightDecigrams: decigrams,
                        biologicalSex: babyProfile?.biologicalSex,
                        birthday: babyProfile?.birthday,
                        entryDate: DateTimeTools.parse(entry.entryTimestamp) ?? Date()
                    )
                    return BabyHistoryEntry(
                        id: entry.id,
                        entryTimestamp: entry.entryTimestamp,
                        weightLbs: lbsOz.lbs,
                        weightOz: lbsOz.oz,
                        weightKg: kg,
                        weightLb: lbDecimal,
                        lengthInches: lengthInches,
                        lengthCm: lengthCm,
                        percentile: pct,
                        notes: entry.note?.isEmpty == false ? entry.note : nil,
                        weightDisplay: self.formatBabyWeightDisplay(decigrams: decigrams, source: source, units: units),
                        lengthDisplay: self.formatBabyLengthDisplay(mm: mm, isMetric: metric),
                        source: source
                    )
                }.sorted { $0.entryTimestamp > $1.entryTimestamp }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to load baby entries for day \(day.id): \(error.localizedDescription)")
                babyEntries = []
            }
        }
    }

    func resetSelectedBabyDay() {
        selectedBabyDay = nil
        babyEntries = []
    }

    // MARK: - Baby Display Formatting

    /// Formats baby weight in decigrams as a display string based on the active measurement units.
    /// When source is provided (e.g. "0220", "0222"), applies graduation rounding to match scale LCD.
    private func formatBabyWeightDisplay(decigrams: Int, source: String? = nil, units: MeasurementUnits) -> String {
        guard decigrams > 0 else { return "--" }
        let displayUnit: ConversionTools.BabyDisplayUnit = units == .metric ? .kg : .lbOz
        let graduatedDecigrams = ConversionTools.convertToDisplayWeightBase(
            decigrams: decigrams, source: source, unit: displayUnit, isBabyScaleEntry: true
        )
        switch units {
        case .metric:
            let kg = ConversionTools.convertBabyDecigramsToKg(graduatedDecigrams)
            return "\(String(format: "%.3f", kg)) \(HistoryListStrings.kg)"
        case .imperialLbDecimal:
            let lb = ConversionTools.convertBabyDecigramsToLb(graduatedDecigrams)
            return "\(String(format: "%.1f", lb)) \(HistoryListStrings.lb)"
        case .imperialLbOz:
            let lbsOz = ConversionTools.convertBabyDecigramsToLbsOz(graduatedDecigrams)
            return "\(lbsOz.lbs) \(HistoryListStrings.lb) \(String(format: "%.1f", lbsOz.oz)) \(HistoryListStrings.oz)"
        }
    }

    /// Formats baby length in millimeters as a display string based on unit preference.
    private func formatBabyLengthDisplay(mm: Int, isMetric: Bool) -> String {
        guard mm > 0 else { return "--" }
        if isMetric {
            let cm = ConversionTools.convertBabyMmToCm(mm)
            return "\(String(format: "%.1f", cm)) \(HistoryListStrings.cm)"
        } else {
            let inches = ConversionTools.convertBabyMmToInches(mm)
            return "\(String(format: "%.1f", inches)) \(HistoryListStrings.inUnit)"
        }
    }

    // MARK: - Date Helpers

    /// Converts a UTC ISO8601 timestamp to a local-timezone day string (yyyy-MM-dd).
    private func localDayString(from timestamp: String) -> String {
        guard let date = DateTimeTools.parse(timestamp) else { return "" }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone.current
        return formatter.string(from: date)
    }

    // MARK: - Mapping Helpers

    /// Groups BPM DTOs by month and builds monthly summaries.
    private func mapBpmDTOsToMonths(_ dtos: [BpmOperationDTO]) -> [BPHistoryMonth] {
        let grouped = Dictionary(grouping: dtos) { dto -> String in
            guard let timestamp = dto.entryTimestamp else { return "" }
            return DateTimeTools.getLocalMonthStringFromUTCDate(timestamp)
        }.filter { !$0.key.isEmpty }

        return grouped.map { monthId, entries in
            let count = entries.count
            let avgSys = count > 0 ? Int(entries.compactMap { $0.systolic }.reduce(0, +) / Double(count)) : 0
            let avgDia = count > 0 ? Int(entries.compactMap { $0.diastolic }.reduce(0, +) / Double(count)) : 0
            let avgPulse = count > 0 ? Int(entries.compactMap { $0.pulse }.reduce(0, +) / Double(count)) : 0
            let parts = monthId.split(separator: "-")
            return BPHistoryMonth(
                id: monthId,
                count: count,
                avgSystolic: avgSys,
                avgDiastolic: avgDia,
                avgPulse: avgPulse,
                month: parts.count > 1 ? String(parts[1]) : "",
                year: !parts.isEmpty ? String(parts[0]) : ""
            )
        }.sorted { $0.id > $1.id }
    }

    /// Filters BPM DTOs to a specific month and maps to history entries.
    private func mapBpmDTOsToEntries(_ dtos: [BpmOperationDTO], monthId: String) -> [BPHistoryEntry] {
        return dtos.compactMap { dto -> BPHistoryEntry? in
            guard let timestamp = dto.entryTimestamp,
                  DateTimeTools.getLocalMonthStringFromUTCDate(timestamp) == monthId else { return nil }
            return BPHistoryEntry(
                id: UUID(uuidString: dto.id) ?? UUID(),
                entryTimestamp: timestamp,
                systolic: Int(dto.systolic ?? 0),
                diastolic: Int(dto.diastolic ?? 0),
                pulse: Int(dto.pulse ?? 0),
                notes: dto.note,
                source: dto.source
            )
        }.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    /// Returns `true` when a day (`"yyyy-MM-dd"`) matches the baby's birthday
    /// month + day. Returns `false` when no birthday is set (MOB-1164).
    /// `nonisolated`: a pure function of its arguments (no actor state), so it can be
    /// called from any context — including the synchronous, nonisolated unit tests.
    nonisolated static func isBirthday(dayId: String, birthdayComponents: DateComponents?) -> Bool {
        guard let birthdayComponents,
              let birthMonth = birthdayComponents.month,
              let birthDay = birthdayComponents.day else { return false }
        let parts = dayId.split(separator: "-")
        guard parts.count == 3,
              let month = Int(parts[1]),
              let day = Int(parts[2]) else { return false }
        return month == birthMonth && day == birthDay
    }

    // MARK: - Baby Entries

    /// Baby `create` entries for `profile`. Birth weight/length recorded on the baby profile
    /// are intentionally NOT injected as a synthetic history entry — history reflects only
    /// real recorded entries.
    private func babyCreateEntries(from all: [EntrySnapshot], profile: BabyProfile?) -> [EntrySnapshot] {
        all.filter {
            $0.entryType == EntryType.baby.rawValue
            && $0.operationType == OperationType.create.rawValue
            && $0.babyEntry?.babyId == profile?.id
        }
    }

    /// Groups baby entries by day, then by week, building weekly summaries.
    private func mapBabyEntriesToWeeks(_ entries: [EntrySnapshot], profile: BabyProfile? = nil) -> [BabyHistoryWeek] { // swiftlint:disable:this function_body_length
        // Group by local day
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return self.localDayString(from: entry.entryTimestamp)
        }.filter { !$0.key.isEmpty }

        // Build days sorted newest first
        let units = self.currentMeasurementUnits
        let metric = units == .metric
        // Birthday match is anniversary-aware (month + day) so the balloon appears on
        // the birth day and every subsequent birthday. Nil when no birthday is set.
        let birthdayComponents: DateComponents? = profile?.birthday.map {
            Calendar.current.dateComponents([.month, .day], from: $0)
        }
        let days: [BabyHistoryDay] = grouped.map { dayId, dayEntries in
            let count = dayEntries.count
            let weights = dayEntries.compactMap { $0.babyEntry?.weight }
            let avgWeight = weights.isEmpty ? 0 : weights.reduce(0, +) / weights.count
            let lengths = dayEntries.compactMap { $0.babyEntry?.length }
            let avgMm = lengths.isEmpty ? 0 : lengths.reduce(0, +) / lengths.count
            let lbsOz = ConversionTools.convertBabyDecigramsToLbsOz(avgWeight)
            let kg = ConversionTools.convertBabyDecigramsToKg(avgWeight)
            let lbDecimal = ConversionTools.convertBabyDecigramsToLb(avgWeight)
            let lengthInches = ConversionTools.convertBabyMmToInches(avgMm)
            let lengthCm = ConversionTools.convertBabyMmToCm(avgMm)
            let pct: Int = {
                let fmt = DateFormatter()
                fmt.dateFormat = "yyyy-MM-dd"
                let entryDate = fmt.date(from: dayId) ?? Date()
                return BabyWeightPercentileCalculator.calculatePercentile(
                    weightDecigrams: avgWeight,
                    biologicalSex: profile?.biologicalSex,
                    birthday: profile?.birthday,
                    entryDate: entryDate
                )
            }()
            return BabyHistoryDay(
                id: dayId,
                entryCount: count,
                weightLbs: lbsOz.lbs,
                weightOz: lbsOz.oz,
                weightKg: kg,
                weightLb: lbDecimal,
                lengthInches: lengthInches,
                lengthCm: lengthCm,
                percentile: pct,
                weightDisplay: self.formatBabyWeightDisplay(decigrams: avgWeight, units: units),
                lengthDisplay: self.formatBabyLengthDisplay(mm: avgMm, isMetric: metric),
                isBirthday: Self.isBirthday(dayId: dayId, birthdayComponents: birthdayComponents)
            )
        }.sorted { $0.id > $1.id }

        // Group days into weeks anchored to the baby's date of birth — 1:1 with the Baby app:
        //   week = (whole days between birthday and entry day) / 7 + 1
        // so the birth week (days 0–6) is Week 1, days 7–13 are Week 2, and so on. This is
        // independent of how many days actually have entries (a sparse log no longer collapses
        // distinct baby-age weeks into one). Weeks are ordered newest-first (highest number on top).
        guard let birthday = profile?.birthday else {
            // No birthday on the profile — fall back to legacy 7-recorded-day chunking so the
            // list still renders when the baby's date of birth is unavailable.
            var weeks: [BabyHistoryWeek] = []
            let chunks = days.chunked(into: 7)
            let totalWeeks = chunks.count
            for (index, chunk) in chunks.enumerated() {
                let weekNumber = totalWeeks - index
                weeks.append(BabyHistoryWeek(
                    id: "week-\(weekNumber)",
                    weekNumber: weekNumber,
                    days: chunk
                ))
            }
            return weeks
        }

        let calendar = Calendar.current
        let birthdayStart = calendar.startOfDay(for: birthday)
        let dayFormatter = DateFormatter()
        dayFormatter.dateFormat = "yyyy-MM-dd"
        dayFormatter.timeZone = TimeZone.current // match localDayString so the day round-trips exactly

        func weekNumber(forDayId dayId: String) -> Int {
            guard let dayDate = dayFormatter.date(from: dayId) else { return 1 }
            let dayStart = calendar.startOfDay(for: dayDate)
            let dayDiff = calendar.dateComponents([.day], from: birthdayStart, to: dayStart).day ?? 0
            // Clamp to 1 so any stray entry dated on/before the birthday still lands in Week 1.
            return max(1, dayDiff / 7 + 1)
        }

        let weekGroups = Dictionary(grouping: days) { weekNumber(forDayId: $0.id) }
        return weekGroups
            .map { number, weekDays in
                BabyHistoryWeek(
                    id: "week-\(number)",
                    weekNumber: number,
                    days: weekDays.sorted { $0.id > $1.id }
                )
            }
            .sorted { $0.weekNumber > $1.weekNumber }
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
