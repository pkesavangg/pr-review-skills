//
//  HistoryStoreBPBabyTestsExtra.swift
//  meAppTests
//
//  Continuation of HistoryStoreBPBabyTests: baby optimistic delete + undo,
//  handleExport title variants, isMetric getter, and subscription reactions.
//

import Combine
import Foundation
@testable import meApp
import Testing

extension HistoryStoreBPBabyTests {

    // MARK: - Baby: optimistic delete + undo

    func makeBabyHistoryEntry(id: UUID = UUID(), entryTimestamp: String = "2026-03-10T08:00:00Z") -> BabyHistoryEntry {
        BabyHistoryEntry(
            id: id,
            entryTimestamp: entryTimestamp,
            weightLbs: 8,
            weightOz: 5,
            weightKg: 3.9,
            weightLb: 8.3,
            lengthInches: 20,
            lengthCm: 50.8,
            percentile: 50,
            notes: nil,
            weightDisplay: "8 lbs 5 oz",
            lengthDisplay: "20 in"
        )
    }

    @Test("showDeleteBabyEntryAlert presents a confirmation alert")
    func showDeleteBabyEntryAlertPresents() {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        sut.store.showDeleteBabyEntryAlert(entry: makeBabyHistoryEntry())
        #expect(sut.notificationService.showAlertCalls == 1)
        #expect(sut.notificationService.alertData?.buttons.count == 2)
    }

    @Test("showDeleteBabyEntryAlert cancel dismisses without deleting")
    func showDeleteBabyEntryAlertCancel() {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        sut.store.showDeleteBabyEntryAlert(entry: makeBabyHistoryEntry())
        sut.notificationService.alertData?.buttons.first { $0.type == .secondary }?.action(nil)
        #expect(sut.entryService.deleteEntryByIdCalls == 0)
    }

    @Test("confirm baby delete removes entry optimistically and shows undo toast")
    func confirmBabyDeleteShowsUndoToast() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile))
        sut.entryService.fetchAllEntrySnapshotsResult = .success([
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z", babyId: profile.id)
        ])
        sut.store.loadMonths()
        _ = await waitUntil { !sut.store.babyWeeks.isEmpty }
        if let day = sut.store.babyWeeks.first?.days.first {
            sut.store.selectBabyDay(day)
            _ = await waitUntil { !sut.store.babyEntries.isEmpty }
        }
        let seeded = sut.store.babyEntries.first ?? makeBabyHistoryEntry()

        sut.store.showDeleteBabyEntryAlert(entry: seeded)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)

        #expect(sut.store.babyEntries.contains { $0.id == seeded.id } == false)
        #expect(sut.notificationService.toastData?.message.contains(HistoryListStrings.readingDeleted) == true)
    }

    @Test("undo baby delete restores the entry")
    func undoBabyDeleteRestores() async {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        let entry = makeBabyHistoryEntry()
        sut.store.showDeleteBabyEntryAlert(entry: entry)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)

        sut.notificationService.toastData?.onClick()
        let done = await waitUntil { sut.store.babyEntries.contains { $0.id == entry.id } }
        #expect(done == true)
        #expect(sut.notificationService.toastData?.message == HistoryListStrings.readingRestored)
        #expect(sut.entryService.deleteEntryByIdCalls == 0)
    }

    @Test("commit baby delete on toast dismiss calls deleteEntry")
    func commitBabyDeleteCallsService() async {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        let entry = makeBabyHistoryEntry()
        sut.store.showDeleteBabyEntryAlert(entry: entry)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)

        sut.notificationService.toastData?.onDismiss?()
        let done = await waitUntil { sut.entryService.deleteEntryByIdCalls == 1 }
        #expect(done == true)
        #expect(sut.entryService.deletedEntryIds.first == entry.id)
    }

    @Test("baby delete failure shows an error toast with retry")
    func deleteBabyEntryFailureShowsRetryToast() async {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        let entry = makeBabyHistoryEntry()
        sut.entryService.deleteEntryByIdError = HistoryStoreBPBabyTestError.babyDeleteFailed

        sut.store.showDeleteBabyEntryAlert(entry: entry)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)
        sut.notificationService.toastData?.onDismiss?()

        let done = await waitUntil { sut.notificationService.toastData?.isError == true }
        #expect(done == true)
        #expect(sut.notificationService.toastData?.message == HistoryListStrings.couldntDelete)
    }

    // MARK: - handleExport title variants

    @Test("handleExport uses the blood-pressure download title in BP mode")
    func handleExportBPTitle() {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.store.handleExport()
        #expect(sut.notificationService.alertData?.title == HistoryListStrings.downloadBPHistory)
    }

    @Test("handleExport uses the baby download title in baby mode")
    func handleExportBabyTitle() {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        sut.store.handleExport()
        #expect(sut.notificationService.alertData?.title == HistoryListStrings.downloadBabyHistory)
    }

    // MARK: - isMetric getter

    @Test("isMetric reflects the account measurement units")
    func isMetricReflectsAccount() {
        let metric = makeSUT(measurementUnits: MeasurementUnits.metric.rawValue)
        #expect(metric.store.isMetric == true)
        let imperial = makeSUT(measurementUnits: MeasurementUnits.imperialLbOz.rawValue)
        #expect(imperial.store.isMetric == false)
    }

    // MARK: - Subscriptions

    @Test("entrySaved while viewing a BP month refreshes the BP detail")
    func entrySavedRefreshesBPMonth() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(entryTimestamp: "2026-03-10T08:00:00Z")
        ])
        let month = BPHistoryMonth(id: "2026-03", count: 1, avgSystolic: 120, avgDiastolic: 80, avgPulse: 72, month: "03", year: "2026")
        sut.store.selectBPMonth(month)
        _ = await waitUntil { sut.store.selectedBPMonth != nil }
        let before = sut.entryService.fetchBpmEntriesCalls

        let savedEntry = EntryTestFixtures.makeEntry(timestamp: "2026-03-10T08:00:00Z")
        sut.entryService.entrySaved.send(EntryNotification(from: savedEntry))

        let done = await waitUntil { sut.entryService.fetchBpmEntriesCalls > before }
        #expect(done == true)
    }

    @Test("product type switch reloads history after debounce")
    func productTypeSwitchReloads() async {
        let sut = makeSUT(selection: .myWeight)
        // MOB-1433 §5c: the product-type switch reloads history eagerly only while History
        // is on screen; this test exercises that on-screen reload path.
        sut.store.isHistoryScreenActive = true
        sut.entryService.getMonthsAllResult = .success([])
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(entryTimestamp: "2026-03-10T08:00:00Z")
        ])

        sut.productTypeStore.select(.myBloodPressure)
        // The selectedItemPublisher is debounced by 300ms, so wait past that window.
        try? await Task.sleep(nanoseconds: 600_000_000)
        let done = await waitUntil { sut.entryService.fetchBpmEntriesCalls >= 1 }
        #expect(done == true)
    }
}
