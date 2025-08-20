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

    // MARK: - Init ------------------------------------------------------

    init() {
        entryService.entrySaved
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task {
                    await self.loadMonthsInternal(canShowLoader: false)
                }
            }
            .store(in: &cancellables)
        entryService.entryDeleted
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task {
                    await self.loadMonthsInternal(canShowLoader: false)
                }
            }
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
        print("Loading months...", canShowLoader ? "Showing loader" : "Not showing loader")
        if canShowLoader {
            notificationService.showLoader(LoaderModel(text: loaderLang.loading))
        }
        do {
            let result = try await entryService.getMonthsAll()
            await self.loadEntries()
            months = result
            isEmptyState = result.isEmpty
        } catch {
            months = []
        }
        notificationService.dismissLoader()
    }

    func loadEntries(for month: HistoryMonth? = nil) async {
        let selectedMonth = month ?? self.selectedMonth
        
        guard let selectedMonth else {
            return
        }
        notificationService.showLoader(LoaderModel(text: loaderLang.loading))
        do {
            entries = try await entryService.getMonthDetail(month: selectedMonth.id)
            
            // Sort entries by entryTimestamp (newest first)
            entries.sort {
                DateTimeTools.getTimestamp($0.entryTimestamp) >
                DateTimeTools.getTimestamp($1.entryTimestamp)
            }
            isEmptyState = entries.isEmpty
        } catch {
            entries = []
        }
        self.notificationService.dismissLoader()
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
