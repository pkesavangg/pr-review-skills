//
//  MetricInfoSheetWrapper.swift
//  meApp
//
//  Wrapper to safely extract DTO from Entry before displaying ScaleMetricsView
//

import SwiftData
import SwiftUI

enum MetricInfoSheetDTOResolver {
    @MainActor
    static func resolveDTO(
        entry: Entry,
        refetchedEntry: Entry?,
        mainContext: ModelContext
    ) -> BathScaleOperationDTO {
        if let refetchedEntry {
            return refetchedEntry.toOperationDTO()
        }

        if entry.modelContext != nil {
            return entry.toOperationDTO()
        }

        mainContext.insert(entry)
        let dto = entry.toOperationDTO()
        mainContext.delete(entry)
        return dto
    }
}

/// Wrapper that safely extracts DTO from Entry before displaying ScaleMetricsView.
/// This ensures SwiftData properties are accessed on main actor within a ModelContext.
@MainActor
struct MetricInfoSheetWrapper: View {
    private struct ReloadTrigger: Equatable {
        let entryId: UUID
        let selectedPeriod: TimePeriod
        let metricLabels: [String]
    }

    let entry: Entry
    let selectedMetric: BodyMetric
    @ObservedObject var dashboardStore: DashboardStore
    @State private var entryDTO: BathScaleOperationDTO?
    @State private var lastReloadTrigger: ReloadTrigger?
    private let dtoLoader: @Sendable () async -> BathScaleOperationDTO

    init(
        entry: Entry,
        selectedMetric: BodyMetric,
        dashboardStore: DashboardStore,
        dtoLoader: (@Sendable () async -> BathScaleOperationDTO)? = nil
    ) {
        self.entry = entry
        self.selectedMetric = selectedMetric
        self.dashboardStore = dashboardStore
        self.dtoLoader = dtoLoader ?? {
            await Self.loadDTO(for: entry)
        }
    }

    var body: some View {
        Group {
            if let dto = entryDTO {
                ScaleMetricsView(entryDTO: dto, selectedMetric: selectedMetric, dashboardStore: dashboardStore)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            await reloadIfNeeded(force: true)
        }
        .onReceive(dashboardStore.objectWillChange) { _ in
            Task {
                await reloadIfNeeded()
            }
        }
    }

    private var reloadTrigger: ReloadTrigger {
        ReloadTrigger(
            entryId: entry.id,
            selectedPeriod: dashboardStore.state.graph.selectedPeriod,
            metricLabels: dashboardStore.state.metrics.metrics.map(\.label)
        )
    }

    private func reloadIfNeeded(force: Bool = false) async {
        let trigger = reloadTrigger
        guard force || trigger != lastReloadTrigger else { return }
        lastReloadTrigger = trigger
        await extractDTO()
    }

    private func extractDTO() async {
        let dto = await dtoLoader()
        await MainActor.run {
            self.entryDTO = dto
        }
    }

    static func loadDTO(
        for entry: Entry,
        refetchEntries: @escaping @Sendable ([UUID]) async throws -> [UUID: Entry] = { entryIds in
            let repository = EntryRepository()
            return try await repository.refetchEntriesOnMainActor(entryIds: entryIds)
        },
        mainContext: @escaping @MainActor () -> ModelContext = {
            PersistenceController.shared.context
        }
    ) async -> BathScaleOperationDTO {
        do {
            let refetched = try await refetchEntries([entry.id])
            return await MainActor.run {
                MetricInfoSheetDTOResolver.resolveDTO(
                    entry: entry,
                    refetchedEntry: refetched[entry.id],
                    mainContext: mainContext()
                )
            }
        } catch {
            return await MainActor.run {
                MetricInfoSheetDTOResolver.resolveDTO(
                    entry: entry,
                    refetchedEntry: nil,
                    mainContext: mainContext()
                )
            }
        }
    }
}
