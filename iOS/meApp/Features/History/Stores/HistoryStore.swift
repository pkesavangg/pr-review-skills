import Combine
//
//  HistoryStore.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//
import Foundation
import SwiftUI

/// Store / ViewModel that powers the History feature (monthly summaries, month detail, entry detail, metric info).
@MainActor
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
    @Published private(set) var entries: [Entry] = []
    
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

    /// Whether the current product type selection is blood pressure.
    var isBloodPressureMode: Bool {
        productTypeStore.selectedItem == .myBloodPressure
    }

    /// Whether the current product type selection is a baby profile.
    var isBabyMode: Bool {
        if case .baby = productTypeStore.selectedItem { return true }
        return false
    }

    // MARK: - UI Flags
    @Published var isEmptyState: Bool = false
    
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Language Strings
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    
    /// Logger tag for this store
    private let tag = "HistoryStore"
    private var hasLoadedMonths = false
    private var monthsLoadTask: Task<Void, Never>?
    
    // MARK: - Init ------------------------------------------------------
    
    init() {
        entryService.entrySaved
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task {
                    await self.loadMonthsInternal(canShowLoader: false)
                    // If we're viewing a month and the saved entry belongs to that month, refresh entries inline
                    if let selectedMonth = self.selectedMonth {
                        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
                        if monthKey == selectedMonth.id {
                            await self.loadEntries(for: selectedMonth, showLoader: false)
                        }
                    }
                }
            }
            .store(in: &cancellables)
        entryService.entryDeleted
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task {
                    await self.loadMonthsInternal(canShowLoader: false)
                    // If we're viewing a month and the deleted entry belongs to that month, refresh entries inline
                    if let selectedMonth = self.selectedMonth {
                        let monthKey = DateTimeTools.getLocalMonthStringFromUTCDate(entry.entryTimestamp)
                        if monthKey == selectedMonth.id {
                            await self.loadEntries(for: selectedMonth, showLoader: false)
                        }
                    }
                }
            }
            .store(in: &cancellables)

        // Reload history when the user switches product type in the header dropdown
        productTypeStore.selectedItemPublisher
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                self.hasLoadedMonths = false
                self.loadMonths()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public API --------------------------------------------------
    /// Call onAppear of History list screen.
    func loadMonths() {
        guard !hasLoadedMonths else { return }
        hasLoadedMonths = true
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
        // Refresh account data to ensure we have latest unit settings
        _ = try? await accountService.refreshAccount()
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
    func showDeleteEntryAlert(entry: Entry, onCancel: (() -> Void)? = nil) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    Task {
                        await self.deleteEntryInternal(entry)
                        onCancel?()
                    }
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
    
    private func loadMonthsInternal(canShowLoader: Bool = true) async {
        guard monthsLoadTask == nil else { return }            // prevent overlap

        // Blood pressure mode uses dummy data — no service call needed
        if isBloodPressureMode {
            monthsLoadTask = Task { [weak self] in
                guard let self else { return }
                let result = BPDummyDataGenerator.generateMonths()
                self.bpMonths = result
                self.isEmptyState = result.isEmpty
                self.monthsLoadTask = nil
            }
            await monthsLoadTask?.value
            return
        }

        // Baby mode uses dummy data — no service call needed
        if isBabyMode {
            monthsLoadTask = Task { [weak self] in
                guard let self else { return }
                let result = BabyDummyDataGenerator.generateWeeks()
                self.babyWeeks = result
                self.isEmptyState = result.isEmpty
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
            let fetched = try await entryService.getMonthDetail(month: selectedMonth.id)

            // UI-level deduplication:
            // Group by entryTimestamp and keep the latest operation by serverTimestamp.
            let grouped = Dictionary(grouping: fetched) { $0.entryTimestamp }
            let latestPerTimestamp: [Entry] = grouped.compactMap { _, values in
                values.max { ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "") }
            }
            // Show only final creates; hide deletes
            let visible = latestPerTimestamp.filter { $0.operationType == OperationType.create.rawValue }
            // Sort newest first by entryTimestamp
            let pairs = visible.map { entry -> (Entry, Int64) in
                (entry, DateTimeTools.getTimestamp(entry.entryTimestamp))
            }
            let sorted = pairs.sorted { $0.1 > $1.1 }.map { $0.0 }
            self.entries = sorted
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to load month entries: monthId=\(selectedMonth.id), error=\(error.localizedDescription)")
            self.entries = []
        }
    }
    
    private func deleteEntryInternal(_ entry: Entry) async {
        do {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingEntry))
            try await entryService.deleteEntry(entry)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete entry:", data: error.localizedDescription)
        }
        notificationService.dismissLoader()
    }
    
    // MARK: - Export Data
    private func exportData() {
        Task {
            notificationService.showLoader(LoaderModel(text: loaderLang.sendingCsv))
            do {
                try await entryService.exportCSV()
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
        bpEntries = BPDummyDataGenerator.generateEntries(for: month.id)
    }

    func resetSelectedBPMonth() {
        selectedBPMonth = nil
        bpEntries = []
    }

    // MARK: - Baby API

    /// User tapped a baby day row.
    func selectBabyDay(_ day: BabyHistoryDay) {
        selectedBabyDay = day
        babyEntries = BabyDummyDataGenerator.generateEntries(for: day.id)
    }

    func resetSelectedBabyDay() {
        selectedBabyDay = nil
        babyEntries = []
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
