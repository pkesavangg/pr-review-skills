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
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isEmptyState: Bool = false

    private var cancellables = Set<AnyCancellable>()
    // MARK: - Language Strings
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    
    /// Logger tag for this store
    private let tag = "HistoryStore"

    // MARK: - Init ------------------------------------------------------

    init() {
        // Refresh only the affected month when a new entry is stored or deleted.
        let refreshMonthForEntry: (Entry) -> Void = { [weak self] entry in
            guard let self = self else { return }
            let monthKey = String(entry.entryTimestamp.prefix(7))
            Task { await self.refreshMonth(monthKey) }
        }
        entryService.entrySaved
            .sink(receiveValue: refreshMonthForEntry)
            .store(in: &cancellables)
        entryService.entryDeleted
            .sink(receiveValue: refreshMonthForEntry)
            .store(in: &cancellables)
    }

    // MARK: - Public API --------------------------------------------------

    /// Call onAppear of History list screen.
    func loadMonths() {
        Task { [weak self] in
            await self?.loadMonthsInternal()
        }
    }

    /// User tapped a month row.
    func selectMonth(_ month: HistoryMonth) {
        selectedMonth = month
        Task { [weak self] in
            await self?.loadEntries(for: month)
        }
    }

    /// Toggle expand/collapse for an entry row.
    func toggleEntry(_ entry: Entry) {
        let id = entry.id.uuidString
        if expandedEntries.contains(id) {
            expandedEntries.remove(id)
        } else {
            expandedEntries.insert(id)
        }
    }

    /// User tapped a metric inside an expanded entry.
    func selectMetric(_ metric: BodyMetric) {
        selectedMetric = metric
    }

    func deleteEntry(_ entry: Entry) {
        Task { [weak self] in
            guard let self = self else { return }
            // Show confirmation alert first
            let loader = LoaderModel(text: LoaderStrings.deletingEntry)
            self.notificationService.showLoader(loader)
            await self.deleteEntryInternal(entry)
            await self.refreshSelectedMonth()
            self.notificationService.dismissLoader()
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
                    self.deleteEntry(entry)
                    onCancel?()
                  }

                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Manual Refresh -------------------------------------------------

    /// Refresh the entries for the month that is currently selected (used by pull-to-refresh UI)
    func refreshSelectedMonth() async {
        guard let month = selectedMonth else { return }
        await loadEntries(for: month)
    }

    func refreshAllEntries() async {
        await entryService.syncAllEntriesWithRemote()
        await loadMonthsInternal()
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

    private func loadMonthsInternal() async {
        await setLoading(true)
        do {
            let result = try await entryService.getMonthsAll()
            months = result
            isEmptyState = result.isEmpty
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
            months = []
        }
        await setLoading(false)
    }

    private func loadEntries(for month: HistoryMonth) async {
        await setLoading(true)
        do {
            entries = try await entryService.getMonthDetail(month: month.id)
            isEmptyState = entries.isEmpty
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
            entries = []
        }
        await setLoading(false)
    }

    private func setLoading(_ value: Bool) async {
        await MainActor.run { self.isLoading = value }
    }

    // Update or insert a single month summary instead of recomputing all months.
    private func refreshMonth(_ monthKey: String) async {
        do {
            if let summary = try await entryService.getMonthSummary(monthKey: monthKey) {
                await MainActor.run {
                    if let index = months.firstIndex(where: { $0.id == monthKey }) {
                        months[index] = summary
                    } else {
                        months.append(summary)
                        months.sort { $0.entryTimestamp > $1.entryTimestamp }
                    }
                }
                // Ensure empty state flag stays correct
                await MainActor.run { isEmptyState = months.isEmpty }
            }
        } catch {
            // Fallback to full reload on error
            Task { await loadMonthsInternal() }
        }
    }

    private func deleteEntryInternal(_ entry: Entry) async {
        do {
            try await entryService.deleteEntry(entry)
        } catch {
            errorMessage = error.localizedDescription
        }
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

