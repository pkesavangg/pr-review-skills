import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct MultiDeviceSnapshotViewModelTests {

    // MARK: - Helpers

    private func makeSUT() -> (sut: MultiDeviceSnapshotViewModel, entryService: EntryService) {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let sut = MultiDeviceSnapshotViewModel()
        return (sut, deps.entry)
    }

    private func makeBabyProfile(
        id: String = "baby-1",
        name: String = "Aria",
        birthday: Date? = Calendar.current.date(byAdding: .day, value: -90, to: Date())
    ) -> BabyProfile {
        BabyProfile(
            id: id,
            name: name,
            birthday: birthday,
            biologicalSex: "female",
            birthWeightLbs: 7.5,
            birthWeightOz: 3.0
        )
    }

    // MARK: - Initial State

    @Test("initialises with empty published collections")
    func initialStateIsEmpty() {
        let (sut, _) = makeSUT()
        #expect(sut.dailySummaries.isEmpty)
        #expect(sut.bpmDailySummaries.isEmpty)
        #expect(sut.babyDailySummaries.isEmpty)
    }

    // MARK: - dailySummaries Binding

    @Test("dailySummaries updates when entryService publishes new values")
    func dailySummariesUpdateFromEntryService() async {
        let (sut, entryService) = makeSUT()
        let summaries = DashboardTestFixtures.makeSortedDailySummaries()

        entryService.dailySummaries = summaries

        await DashboardTestFixtures.waitUntil { sut.dailySummaries.count == 5 }
        #expect(sut.dailySummaries.count == 5)
    }

    @Test("dailySummaries clears when entryService publishes empty array")
    func dailySummariesClearFromEntryService() async {
        let (sut, entryService) = makeSUT()
        entryService.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()
        await DashboardTestFixtures.waitUntil { sut.dailySummaries.count == 5 }

        entryService.dailySummaries = []
        await DashboardTestFixtures.waitUntil { sut.dailySummaries.isEmpty }
        #expect(sut.dailySummaries.isEmpty)
    }

    // MARK: - bpmDailySummaries Binding

    @Test("bpmDailySummaries updates when entryService publishes new BPM values")
    func bpmDailySummariesUpdateFromEntryService() async {
        let (sut, entryService) = makeSUT()
        let bpmSummaries = [
            makeBpmSummary(systolic: 120, diastolic: 80, date: Date()),
            makeBpmSummary(systolic: 130, diastolic: 85, date: Date().addingTimeInterval(-86400))
        ]

        entryService.bpmDailySummaries = bpmSummaries

        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.count == 2 }
        #expect(sut.bpmDailySummaries.count == 2)
    }

    @Test("bpmDailySummaries clears when entryService publishes empty array")
    func bpmDailySummariesClearFromEntryService() async {
        let (sut, entryService) = makeSUT()
        entryService.bpmDailySummaries = [makeBpmSummary(systolic: 120, diastolic: 80, date: Date())]
        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.count == 1 }

        entryService.bpmDailySummaries = []
        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.isEmpty }
        #expect(sut.bpmDailySummaries.isEmpty)
    }

    // MARK: - snapshotItems

    @Test("snapshotItems passes through non-baby non-BPM items unchanged")
    func snapshotItemsPassThroughMyWeight() {
        let (sut, _) = makeSUT()
        let items: [ProductSelection] = [.myWeight]
        let result = sut.snapshotItems(from: items, selectedItem: .myWeight)
        #expect(result == [.myWeight])
    }

    @Test("snapshotItems collapses multiple babies to one, falling back to first when no baby selected")
    func snapshotItemsKeepsOnlyFirstBabyWhenNoneSelected() {
        let (sut, _) = makeSUT()
        let baby1 = makeBabyProfile(id: "b1", name: "First")
        let baby2 = makeBabyProfile(id: "b2", name: "Second")
        let items: [ProductSelection] = [.myWeight, .baby(profile: baby1), .baby(profile: baby2)]

        let result = sut.snapshotItems(from: items, selectedItem: .myWeight)

        let babyItems = result.compactMap { item -> BabyProfile? in
            if case .baby(let profile) = item { return profile }
            return nil
        }
        #expect(babyItems.count == 1)
        #expect(babyItems.first?.id == "b1")
    }

    @Test("snapshotItems returns selected baby when selectedItem is a baby present in the list")
    func snapshotItemsReturnsSelectedBaby() {
        let (sut, _) = makeSUT()
        let baby1 = makeBabyProfile(id: "b1", name: "First")
        let baby2 = makeBabyProfile(id: "b2", name: "Second")
        let items: [ProductSelection] = [.myWeight, .baby(profile: baby1), .baby(profile: baby2)]

        let result = sut.snapshotItems(from: items, selectedItem: .baby(profile: baby2))

        let babyItems = result.compactMap { item -> BabyProfile? in
            if case .baby(let profile) = item { return profile }
            return nil
        }
        #expect(babyItems.count == 1)
        #expect(babyItems.first?.id == "b2")
    }

    @Test("snapshotItems falls back to first baby when selected baby is absent from the list")
    func snapshotItemsFallsBackToFirstWhenSelectedAbsent() {
        let (sut, _) = makeSUT()
        let baby1 = makeBabyProfile(id: "b1", name: "First")
        let baby2 = makeBabyProfile(id: "b2", name: "Second")
        let absentBaby = makeBabyProfile(id: "b3", name: "Absent")
        let items: [ProductSelection] = [.myWeight, .baby(profile: baby1), .baby(profile: baby2)]

        let result = sut.snapshotItems(from: items, selectedItem: .baby(profile: absentBaby))

        let babyIds = result.compactMap { item -> String? in
            if case .baby(let p) = item { return p.id }
            return nil
        }
        #expect(babyIds == ["b1"])
    }

    @Test("snapshotItems appends BPM when bpmDailySummaries is non-empty and BPM not already present")
    func snapshotItemsAddsBpmWhenDataExists() async {
        let (sut, entryService) = makeSUT()
        entryService.bpmDailySummaries = [makeBpmSummary(systolic: 120, diastolic: 80, date: Date())]
        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.count == 1 }

        let result = sut.snapshotItems(from: [.myWeight], selectedItem: .myWeight)

        #expect(result.contains(.myBloodPressure))
    }

    @Test("snapshotItems does not duplicate BPM when already in available items")
    func snapshotItemsDoesNotDuplicateBpm() async {
        let (sut, entryService) = makeSUT()
        entryService.bpmDailySummaries = [makeBpmSummary(systolic: 120, diastolic: 80, date: Date())]
        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.count == 1 }

        let result = sut.snapshotItems(from: [.myWeight, .myBloodPressure], selectedItem: .myWeight)

        let bpmCount = result.filter { $0 == .myBloodPressure }.count
        #expect(bpmCount == 1)
    }

    @Test("snapshotItems does not add BPM when bpmDailySummaries is empty")
    func snapshotItemsDoesNotAddBpmWithNoData() {
        let (sut, _) = makeSUT()
        let result = sut.snapshotItems(from: [.myWeight], selectedItem: .myWeight)

        #expect(!result.contains(.myBloodPressure))
    }

    @Test("snapshotItems returns pending baby placeholder when no real baby profile exists")
    func snapshotItemsReturnsPendingBabyWhenNoRealBabies() {
        let (sut, _) = makeSUT()
        let pendingProfile = BabyProfile(id: BabyProfile.pendingSelectionId, name: "Baby Scale")
        let items: [ProductSelection] = [.myWeight, .baby(profile: pendingProfile)]

        let result = sut.snapshotItems(from: items, selectedItem: .myWeight)

        let babyItems = result.compactMap { item -> BabyProfile? in
            if case .baby(let profile) = item { return profile }
            return nil
        }
        #expect(babyItems.count == 1)
        #expect(babyItems.first?.isPendingSelection == true)
    }

    @Test("snapshotItems shows real baby over pending placeholder when both present")
    func snapshotItemsPrefersRealBabyOverPending() {
        let (sut, _) = makeSUT()
        let realBaby = makeBabyProfile(id: "real-baby", name: "Aria")
        let pendingProfile = BabyProfile(id: BabyProfile.pendingSelectionId, name: "Baby Scale")
        let items: [ProductSelection] = [.myWeight, .baby(profile: realBaby), .baby(profile: pendingProfile)]

        let result = sut.snapshotItems(from: items, selectedItem: .myWeight)

        let babyItems = result.compactMap { item -> BabyProfile? in
            if case .baby(let profile) = item { return profile }
            return nil
        }
        #expect(babyItems.count == 1)
        #expect(babyItems.first?.isPendingSelection == false)
        #expect(babyItems.first?.id == "real-baby")
    }

    @Test("snapshotItems places baby last")
    func snapshotItemsBabyIsLast() async {
        let (sut, entryService) = makeSUT()
        entryService.bpmDailySummaries = [makeBpmSummary(systolic: 120, diastolic: 80, date: Date())]
        await DashboardTestFixtures.waitUntil { sut.bpmDailySummaries.count == 1 }

        let baby = makeBabyProfile()
        let result = sut.snapshotItems(from: [.myWeight, .baby(profile: baby)], selectedItem: .myWeight)

        if case .baby = result.last {
            // pass
        } else {
            Issue.record("Expected baby to be last item, got \(String(describing: result.last))")
        }
    }

    @Test("loadSnapshots loads data for every requested baby profile")
    func loadSnapshotsLoadsAllBabyProfiles() async {
        let (sut, entryService) = makeSUT()
        let baby1 = makeBabyProfile(id: "baby-1", name: "Aria")
        let baby2 = makeBabyProfile(id: "baby-2", name: "Noah")

        await sut.loadSnapshots(availableItems: [
            .myWeight,
            .baby(profile: baby1),
            .baby(profile: baby2)
        ])

        await DashboardTestFixtures.waitUntil {
            entryService.babyDailySummariesByProfile[baby1.id] != nil &&
            entryService.babyDailySummariesByProfile[baby2.id] != nil
        }

        #expect(entryService.babyDailySummariesByProfile[baby1.id] != nil)
        #expect(entryService.babyDailySummariesByProfile[baby2.id] != nil)
    }

    // TODO: MA-XXXX — Test needs MockEntryService; concrete EntryService lacks call tracking properties.
    // @Test("loadSnapshots skips duplicate work for the same available item signature")
    // func loadSnapshotsSkipsDuplicateSignature() async { ... }

    // MARK: - babySummaries

    @Test("babySummaries returns dummy data when babyDailySummaries is empty for profile")
    func babySummariesReturnsDummyWhenEmpty() {
        let (sut, _) = makeSUT()
        let baby = makeBabyProfile()

        let result = sut.babySummaries(for: baby)

        #expect(!result.isEmpty)
    }

    @Test("babySummaries returns dummy data for a baby whose id has no entry in babyDailySummaries")
    func babySummariesReturnsDummyForUnknownBabyId() {
        let (sut, _) = makeSUT()
        // A profile whose id is NOT in babyDailySummaries (which is empty by default)
        let baby = makeBabyProfile(id: "unknown-baby")

        let result = sut.babySummaries(for: baby)

        // Dummy summaries are always non-empty for a valid baby profile
        #expect(!result.isEmpty)
    }

    @Test("babySummaries dummy data account ids contain the baby id")
    func babySummariesDummyDataHasBabyIdInAccountId() {
        let (sut, _) = makeSUT()
        let baby = makeBabyProfile(id: "my-baby-id")

        let result = sut.babySummaries(for: baby)

        #expect(result.allSatisfy { $0.accountId.contains("my-baby-id") })
    }

    // MARK: - Private Helpers

    private func makeBpmSummary(systolic: Double, diastolic: Double, date: Date) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-bpm",
            period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
            entryTimestamp: ISO8601DateFormatter().string(from: date),
            date: date,
            count: 1,
            weight: 0,
            systolic: systolic,
            diastolic: diastolic
        )
    }
}
