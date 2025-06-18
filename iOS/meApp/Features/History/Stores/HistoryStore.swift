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

    // MARK: - Navigation
    enum Screen {
        case summary                // History/Total
        case monthDetail(HistoryMonth)
        case metricInfo(BodyMetric)
    }

    @Published private(set) var currentScreen: Screen = .summary

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
        currentScreen = .monthDetail(month)
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
        currentScreen = .metricInfo(metric)
    }

    /// Navigate back one level. Use when the back button is pressed.
    func pop() {
        switch currentScreen {
        case .metricInfo(_):
            currentScreen = .monthDetail(selectedMonth!)
        case .monthDetail(_):
            currentScreen = .summary
            // Reset month–specific state
            selectedMonth = nil
            entries = []
            expandedEntries.removeAll()
        case .summary:
            break
        }
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
}

