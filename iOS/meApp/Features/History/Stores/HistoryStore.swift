//
//  HistoryStore.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//
import Foundation
import Combine
import SwiftUI

/// Store / ViewModel that powers the History feature (monthly summaries, month detail, entry detail, metric info).
@MainActor
final class HistoryStore: ObservableObject {
    
    // MARK: - Dependencies
    @Injector private var entryService: EntryService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService
    
    // MARK: - Summary Screen State
    @Published private(set) var months: [HistoryMonth] = []
    
    // MARK: - Month Detail State
    @Published private(set) var selectedMonth: HistoryMonth?
    @Published private(set) var entries: [Entry] = []
    
    /// Set of entry ids that are currently expanded in the Month Detail screen.
    @Published var expandedEntries: Set<String> = []
    
    // MARK: - Metric Info State
    @Published private(set) var selectedMetric: BodyMetric?
    
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
    private var monthsLoadTask: Task<Void, Never>? = nil
    
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
        await entryService.syncAllEntriesWithRemote()
        await loadMonthsInternal()
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
        let alert = AlertModel(
            title: alertLang.CsvExportAlert.title,
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
            // Log original count for debugging
            logger.log(level: .debug, tag: tag, message: "Fetched \(fetched.count) entries for month \(selectedMonth.id)")
            
            // UI-level deduplication:
            // Fixes duplicate history entries from wifi scales by grouping on entryTimestamp and keeping the latest by serverTimestamp.
            let deduplicated = Dictionary(
                grouping: fetched,
                by: { $0.entryTimestamp }
            ).compactMap { $0.value.max(by: { ($0.serverTimestamp ?? "") < ($1.serverTimestamp ?? "") }) }
            
            // Log deduplication results
            let duplicateCount = fetched.count - deduplicated.count
            if duplicateCount > 0 {
                logger.log(level: .info, tag: tag, message: "Removed \(duplicateCount) duplicate entries for month \(selectedMonth.id)")
            }
            
            // Parse once per entry, then sort on the parsed timestamp
            let pairs = deduplicated.map { entry -> (Entry, Int64) in
                (entry, DateTimeTools.getTimestamp(entry.entryTimestamp))
            }
            let sorted = pairs.sorted { $0.1 > $1.1 }.map { $0.0 }
            
            self.entries = sorted
            self.isEmptyState = sorted.isEmpty
        } catch {
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
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
