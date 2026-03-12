import Foundation
import SwiftData
import SwiftUI
import Testing
import UIKit
@testable import meApp

@Suite(.serialized)
@MainActor
struct MetricInfoSheetWrapperTests {

    @Test("MetricInfoSheetDTOResolver: prefers a refetched entry when one is available")
    func resolverPrefersRefetchedEntry() {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let original = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        let refetched = EntryTestFixtures.makeEntry(timestamp: "2026-03-05T08:00:00Z", weight: 1900)

        let dto = MetricInfoSheetDTOResolver.resolveDTO(
            entry: original,
            refetchedEntry: refetched,
            mainContext: context
        )

        #expect(dto.entryTimestamp == "2026-03-05T08:00:00Z")
        #expect(dto.weight == 1900)
    }

    @Test("MetricInfoSheetDTOResolver: uses the existing model-context entry directly when available")
    func resolverUsesEntryWithExistingContext() throws {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        context.insert(entry)

        let dto = MetricInfoSheetDTOResolver.resolveDTO(
            entry: entry,
            refetchedEntry: nil,
            mainContext: context
        )

        #expect(entry.modelContext != nil)
        #expect(dto.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(dto.weight == 1800)
    }

    @Test("MetricInfoSheetDTOResolver: temporarily inserts contextless entries and cleans them up after extraction")
    func resolverTemporarilyInsertsContextlessEntry() throws {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T08:00:00Z", weight: 1810, bodyFat: 250)

        let dto = MetricInfoSheetDTOResolver.resolveDTO(
            entry: entry,
            refetchedEntry: nil,
            mainContext: context
        )

        let persisted = try context.fetch(FetchDescriptor<Entry>())

        #expect(dto.entryTimestamp == "2026-03-02T08:00:00Z")
        #expect(dto.weight == 1810)
        #expect(dto.bodyFat == 250)
        #expect(persisted.isEmpty)
    }

    @Test("MetricInfoSheetDTOResolver: preserves nil metric fields when converting partial entries")
    func resolverPreservesMissingMetricValues() {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let entry = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-03T08:00:00Z",
            weight: 1820,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil
        )

        let dto = MetricInfoSheetDTOResolver.resolveDTO(
            entry: entry,
            refetchedEntry: nil,
            mainContext: context
        )

        #expect(dto.weight == 1820)
        #expect(dto.bodyFat == nil)
        #expect(dto.muscleMass == nil)
        #expect(dto.water == nil)
        #expect(dto.bmi == nil)
    }

    @Test("MetricInfoSheetWrapper.loadDTO: uses a refetched entry when the repository returns one")
    func loadDTOUsesRefetchedEntry() async {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let original = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z", weight: 1800)
        let refetched = EntryTestFixtures.makeEntry(timestamp: "2026-03-06T08:00:00Z", weight: 1910)

        let dto = await MetricInfoSheetWrapper.loadDTO(
            for: original,
            refetchEntries: { _ in [original.id: refetched] },
            mainContext: { context }
        )

        #expect(dto.entryTimestamp == "2026-03-06T08:00:00Z")
        #expect(dto.weight == 1910)
    }

    @Test("MetricInfoSheetWrapper.loadDTO: falls back to the original entry when refetch fails")
    func loadDTOFallsBackOnRefetchFailure() async {
        let context = DashboardManagerTestSupport.makeEntryContext()
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-07T08:00:00Z", weight: 1920, bodyFat: 280)

        let dto = await MetricInfoSheetWrapper.loadDTO(
            for: entry,
            refetchEntries: { _ in throw DashboardTestError.repoFailure },
            mainContext: { context }
        )

        #expect(dto.entryTimestamp == "2026-03-07T08:00:00Z")
        #expect(dto.weight == 1920)
        #expect(dto.bodyFat == 280)
    }

    @Test("MetricInfoSheetWrapper: initial task and on-change handlers rerun the injected DTO loader")
    func wrapperBodyTaskAndOnChangeReloadDTO() async {
        let sut = DashboardManagerTestSupport.makeStore(
            cacheManager: DashboardCacheManager(),
            formatter: MockDashboardFormatter()
        )
        let store = sut.store
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-08T08:00:00Z", weight: 1930, bodyFat: 290)
        let expectedDTO = BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: 290,
            boneMass: nil,
            entryTimestamp: "2026-03-08T08:00:00Z",
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: nil,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: 1930
        )

        var loadCalls = 0
        let view = MetricInfoSheetWrapper(
            entry: entry,
            selectedMetric: .bodyFat,
            dashboardStore: store,
            dtoLoader: {
                loadCalls += 1
                return expectedDTO
            }
        )

        let host = UIHostingController(rootView: view)
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = host
        window.makeKeyAndVisible()
        _ = host.view

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
            loadCalls >= 1
        }

        store.state.graph.selectedPeriod = .month
        store.forceImmediateUIUpdate()
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 1_000_000_000) {
            loadCalls >= 2
        }

        try? await Task.sleep(nanoseconds: 100_000_000)
        store.state.metrics.metrics = store.state.metrics.metrics + [DashboardTestFixtures.makeMetricItem(label: DashboardStrings.bodyFat)]
        store.forceImmediateUIUpdate()
        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 3_000_000_000) {
            loadCalls >= 3
        }

        #expect(loadCalls >= 3)
        #expect(host.view != nil)
    }
}
