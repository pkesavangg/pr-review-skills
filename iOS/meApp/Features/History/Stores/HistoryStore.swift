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
    private var loadedProductTypes: Set<String> = []
    private var monthsLoadTask: Task<Void, Never>?
    
    // MARK: - Init ------------------------------------------------------
    
    init() {
        entryService.entrySaved
            .sink { [weak self] entry in
                guard let self = self else { return }
                Task {
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
                Task {
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
            .sink { [weak self] _ in
                guard let self = self else { return }
                self.monthsLoadTask?.cancel()
                self.monthsLoadTask = nil
                self.loadMonths()
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
                    let allEntries = try await self.entryService.getAllEntries()
                    let babyProfile: BabyProfile? = {
                        if case .baby(let profile) = self.productTypeStore.selectedItem { return profile }
                        return nil
                    }()
                    let babyEntries = allEntries.filter {
                        $0.deviceType == DeviceType.babyScale.rawValue
                        && $0.operationType == OperationType.create.rawValue
                        && $0.babyId == babyProfile?.id
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

    // MARK: - BP Delete

    func showDeleteBPEntryAlert(entry: BPHistoryEntry) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    Task { await self.deleteBPEntryInternal(entry) }
                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    self.notificationService.dismissAlert()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func deleteBPEntryInternal(_ entry: BPHistoryEntry) async {
        do {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingEntry))
            try await entryService.deleteBpmEntry(entryTimestamp: entry.entryTimestamp)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete BP entry: \(error.localizedDescription)")
        }
        notificationService.dismissLoader()
    }

    // MARK: - Baby Delete

    func showDeleteBabyEntryAlert(entry: BabyHistoryEntry) {
        let alert = AlertModel(
            title: AlertStrings.DeleteEntryAlert.title,
            message: AlertStrings.DeleteEntryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.deleteButton, type: .danger) { _ in
                    Task { await self.deleteBabyEntryInternal(entry) }
                },
                AlertButtonModel(title: AlertStrings.DeleteEntryAlert.cancelButton, type: .secondary) { _ in
                    self.notificationService.dismissAlert()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func deleteBabyEntryInternal(_ babyEntry: BabyHistoryEntry) async {
        do {
            notificationService.showLoader(LoaderModel(text: loaderLang.deletingEntry))
            guard let entry = try await entryService.getEntry(byId: babyEntry.id) else {
                logger.log(level: .error, tag: tag, message: "Baby entry not found for deletion: id=\(babyEntry.id)")
                notificationService.dismissLoader()
                return
            }
            try await entryService.deleteEntry(entry)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete baby entry: \(error.localizedDescription)")
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

    /// Whether the active account uses metric (kg) for weight.
    var isMetric: Bool {
        accountService.activeAccount?.weightSettings?.weightUnit == .kg
    }

    /// User tapped a baby day row.
    func selectBabyDay(_ day: BabyHistoryDay) {
        selectedBabyDay = day
        Task {
            do {
                let allEntries = try await entryService.getAllEntries()
                let babyProfile: BabyProfile? = {
                    if case .baby(let profile) = productTypeStore.selectedItem { return profile }
                    return nil
                }()
                let babyId = babyProfile?.id
                let dayEntries = allEntries.filter {
                    $0.deviceType == DeviceType.babyScale.rawValue
                    && $0.operationType == OperationType.create.rawValue
                    && $0.babyId == babyId
                    && self.localDayString(from: $0.entryTimestamp) == day.id
                }
                let metric = self.isMetric
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
                        notes: baby.note.isEmpty ? nil : baby.note,
                        weightDisplay: self.formatBabyWeightDisplay(decigrams: decigrams, source: source, isMetric: metric),
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

    /// Formats baby weight in decigrams as a display string based on unit preference.
    /// When source is provided (e.g. "0220", "0222"), applies graduation rounding to match scale LCD.
    private func formatBabyWeightDisplay(decigrams: Int, source: String? = nil, isMetric: Bool) -> String {
        guard decigrams > 0 else { return "--" }
        let displayUnit: ConversionTools.BabyDisplayUnit = isMetric ? .kg : .lbOz
        let graduatedDecigrams = ConversionTools.convertToDisplayWeightBase(
            decigrams: decigrams, source: source, unit: displayUnit, isBabyScaleEntry: true
        )
        if isMetric {
            let kg = ConversionTools.convertBabyDecigramsToKg(graduatedDecigrams)
            return "\(String(format: "%.3f", kg)) \(HistoryListStrings.kg)"
        } else {
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
    private func mapBabyEntriesToWeeks(_ entries: [Entry], profile: BabyProfile? = nil) -> [BabyHistoryWeek] {
        // Group by local day
        let grouped = Dictionary(grouping: entries) { entry -> String in
            return self.localDayString(from: entry.entryTimestamp)
        }.filter { !$0.key.isEmpty }

        // Build days sorted newest first
        let metric = self.isMetric
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
                weightDisplay: self.formatBabyWeightDisplay(decigrams: avgWeight, isMetric: metric),
                lengthDisplay: self.formatBabyLengthDisplay(mm: avgMm, isMetric: metric)
            )
        }.sorted { $0.id > $1.id }

        // Group days into weeks of 7
        var weeks: [BabyHistoryWeek] = []
        for (index, chunk) in days.chunked(into: 7).enumerated() {
            weeks.append(BabyHistoryWeek(
                id: "week-\(index + 1)",
                weekNumber: index + 1,
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
