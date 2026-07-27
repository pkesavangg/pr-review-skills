//
//  HistoryStoreOffScreenRefreshTests.swift
//  meAppTests
//
//  MOB-1842: a reading saved while History is off screen (e.g. SAVE on the Bluetooth
//  "New Reading Received" card from the dashboard) must show up as soon as History is
//  back on screen. Every tab lives in the tab bar's ZStack, so a retained month-detail
//  screen keeps its already-loaded rows across a tab switch — invalidating the months
//  cache alone left that detail stale until the user re-navigated by hand.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HistoryStoreOffScreenRefreshTests {

    /// Drives the store to "user is viewing the 2026-03 month detail" with one entry loaded.
    private func makeStoreViewingMonth() async -> (HistoryStore, MockEntryService) {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        store.isHistoryScreenActive = true
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")
        ])
        store.loadMonths()
        _ = await waitUntilHistoryStore { store.months.count == 1 }
        store.selectMonth(makeHistoryMonth(id: "2026-03"))
        _ = await waitUntilHistoryStore { store.entries.count == 1 }
        return (store, entryService)
    }

    @Test("entrySaved off screen defers the reads, then refreshes the retained month on activation")
    func offScreenSaveRefreshesRetainedMonthOnActivation() async {
        let (store, entryService) = await makeStoreViewingMonth()

        // User switches to the dashboard; the month detail stays alive behind it.
        store.isHistoryScreenActive = false
        let monthCallsWhenLeaving = entryService.getMonthsAllCalls
        let detailCallsWhenLeaving = entryService.fetchEntrySnapshotsForMonthCalls

        // The Bluetooth card's SAVE lands while History is off screen.
        entryService.fetchEntrySnapshotsForMonthResult = .success([
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z"),
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-02T09:00:00Z")
        ])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T09:00:00Z")
        entryService.entrySaved.send(EntryNotification(from: entry))
        for _ in 0..<20 { await Task.yield() }

        // MOB-1433 §5c still holds: nothing is re-read while off screen.
        #expect(entryService.getMonthsAllCalls == monthCallsWhenLeaving)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == detailCallsWhenLeaving)
        #expect(store.entries.count == 1)

        // User returns to History — the deferred detail read settles now.
        store.isHistoryScreenActive = true
        let refreshed = await waitUntilHistoryStore { store.entries.count == 2 }

        #expect(refreshed == true)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls > detailCallsWhenLeaving)
    }

    @Test("entryDeleted off screen also refreshes the retained month on activation")
    func offScreenDeleteRefreshesRetainedMonthOnActivation() async {
        let (store, entryService) = await makeStoreViewingMonth()

        store.isHistoryScreenActive = false
        let detailCallsWhenLeaving = entryService.fetchEntrySnapshotsForMonthCalls

        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        entryService.entryDeleted.send(EntryNotification(from: entry))
        for _ in 0..<20 { await Task.yield() }

        #expect(entryService.fetchEntrySnapshotsForMonthCalls == detailCallsWhenLeaving)

        store.isHistoryScreenActive = true
        let refreshed = await waitUntilHistoryStore { store.entries.isEmpty }

        #expect(refreshed == true)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls > detailCallsWhenLeaving)
    }

    @Test("activation without an off-screen change does not re-read the retained month")
    func activationWithoutChangeDoesNotRefetch() async {
        let (store, entryService) = await makeStoreViewingMonth()

        store.isHistoryScreenActive = false
        let detailCallsWhenLeaving = entryService.fetchEntrySnapshotsForMonthCalls

        store.isHistoryScreenActive = true
        for _ in 0..<20 { await Task.yield() }

        #expect(entryService.fetchEntrySnapshotsForMonthCalls == detailCallsWhenLeaving)
    }
}
