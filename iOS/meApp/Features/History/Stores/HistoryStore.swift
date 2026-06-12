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

    // MARK: - Init ------------------------------------------------------

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    init() {
        entryService.entrySaved
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task { @MainActor in
                    // Cancel any in-flight month load so the new entry is always picked up
                    self.monthsLoadTask?.cancel()
                    self.monthsLoadTask = nil
                    self.invalidateCacheForCurrentType()
                    await self.loadMonthsInternal(canShowLoader: false)
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
                    // Cancel any in-flight month load so the deleted entry is always reflected
                    self.monthsLoadTask?.cancel()
                    self.monthsLoadTask = nil
                    self.invalidateCacheForCurrentType()
                    await self.loadMonthsInternal(canShowLoader: false)
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
                self.loadMonths()
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
                self.loadMonths()
                if let selectedBabyDay = self.selectedBabyDay {
                    self.selectBabyDay(selectedBabyDay)
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Public API --------------------------------------------------
    /// Call onAppear of History list screen.
    func loadMonths() {
        let currentId = productTypeStore.selectedItem.id
        guard !loadedProductTypes.contains(currentId) else {
            updateEmptyStateFromCache()
            return
        }
        loadedProductTypes.insert(currentId)
        Task { [weak self] in await self?.loadMonthsInternal() }
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
        if let selectedMonth {
            await loadEntries(for: selectedMonth, showLoader: false)
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
            ? HistoryListStrings.downloadBabyHistory
            : isBloodPressureMode
                ? HistoryListStrings.downloadBPHistory
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
    private func loadMonthsInternal(canShowLoader: Bool = true) async {
        guard monthsLoadTask == nil else { return }            // prevent overlap

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
                    self.logger.log(level: .error, tag: self.tag, message: "Failed to load BP history: \(error.localizedDescription)")
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
                    let babyEntries = allEntries.filter {
                        $0.entryType == EntryType.baby.rawValue
                        && $0.operationType == OperationType.create.rawValue
                        && $0.babyEntry?.babyId == babyProfile?.id
                    }
                    let result = self.mapBabyEntriesToWeeks(babyEntries, profile: babyProfile)
                    self.babyWeeks = result
                    self.isEmptyState = result.isEmpty
                } catch {
                    self.logger.log(level: .error, tag: self.tag, message: "Failed to load baby history: \(error.localizedDescription)")
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
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to load history months: \(error.localizedDescription)")
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
            // Group by entryTimestamp and keep the latest operation by serverTimestamp.
            let grouped = Dictionary(grouping: fetched) { $0.entryTimestamp }
            let latestPerTimestamp: [EntrySnapshot] = grouped.compactMap { _, values in
                values.max { ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "") }
            }
            // Show only final creates; hide deletes
            let visible = latestPerTimestamp.filter { $0.operationType == OperationType.create.rawValue }
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
            duration: 3,
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
            duration: 3,
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
            duration: 3,
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
                source: EntrySource.manual.rawValue,
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
                source: nil
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
                let babyId = babyProfile?.id
                let dayEntries = allEntries.filter {
                    $0.entryType == EntryType.baby.rawValue
                    && $0.operationType == OperationType.create.rawValue
                    && $0.babyEntry?.babyId == babyId
                    && self.localDayString(from: $0.entryTimestamp) == day.id
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
                        lengthDisplay: self.formatBabyLengthDisplay(mm: mm, isMetric: metric)
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
            return "\(lbsOz.lbs) \(HistoryListStrings.lbs) \(String(format: "%.1f", lbsOz.oz)) \(HistoryListStrings.oz)"
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
                notes: dto.note
            )
        }.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    /// Groups baby entries by day, then by week, building weekly summaries.
    private func mapBabyEntriesToWeeks(_ entries: [EntrySnapshot], profile: BabyProfile? = nil) -> [BabyHistoryWeek] {
        // Group by local day
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return self.localDayString(from: entry.entryTimestamp)
        }.filter { !$0.key.isEmpty }

        // Build days sorted newest first
        let units = self.currentMeasurementUnits
        let metric = units == .metric
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
                lengthDisplay: self.formatBabyLengthDisplay(mm: avgMm, isMetric: metric)
            )
        }.sorted { $0.id > $1.id }

        // Group days into weeks of 7
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

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
