import Foundation
@testable import meApp
import Testing

/// Exercises `GoalProgressViewModel.loadData` across its goal-type, weight-unit,
/// latest-entry, and weightless branches, plus the publisher-driven reloads.
@Suite(.serialized)
@MainActor
struct GoalProgressViewModelTests {

    private func makeSUT(
        account: AccountSnapshot?,
        latest: Entry? = nil
    ) -> (sut: GoalProgressViewModel, entry: MockEntryService) {
        TestDependencyContainer.reset()
        let accountService = MockAccountService()
        let entryService = MockEntryService()
        entryService.getLatestEntryResult = .success(latest)

        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(entryService as EntryServiceProtocol)

        // Set before init so the init-time `Task { await loadData() }` sees the account.
        accountService.activeAccount = account

        return (GoalProgressViewModel(), entryService)
    }

    private func makeAccount(
        goalType: GoalType?,
        weightUnit: WeightUnit = .lb,
        goalWeight: Double? = nil,
        initialWeight: Double? = nil,
        isWeightlessOn: Bool = false,
        weightlessWeight: Double? = nil
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: "acct-goal",
            isActiveAccount: true,
            weightUnit: weightUnit,
            goalType: goalType,
            goalWeight: goalWeight,
            initialWeight: initialWeight,
            isWeightlessOn: isWeightlessOn,
            weightlessWeight: weightlessWeight
        )
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    private func entryWithWeight(_ weight: Int) -> Entry {
        let entry = Entry(
            entryTimestamp: "2025-01-01T00:00:00.000Z",
            accountId: "acct-goal",
            operationType: "create",
            isSynced: false
        )
        entry.scaleEntry = BathScaleEntry(weight: weight)
        return entry
    }

    // MARK: - No goal

    @Test("loadData with a nil goalType resets to the no-goal state")
    func noGoalResetsState() async {
        let (sut, _) = makeSUT(account: makeAccount(goalType: nil))

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.goalType == .none)
        #expect(sut.delta == 0)
        #expect(sut.progress == 0)
        #expect(sut.unit == WeightUnit.lb.rawValue)
    }

    // MARK: - Lose goal

    @Test("loadData with a lose goal and no latest entry falls back to initial weight (zero progress)")
    func loseGoalNoEntry() async {
        let account = makeAccount(goalType: .lose, goalWeight: 1800, initialWeight: 2000)
        let (sut, _) = makeSUT(account: account)

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.goalType == .lose)
        #expect(sut.unit == WeightUnit.lb.rawValue)
        #expect(sut.startWeight > sut.goalWeight)   // losing: start above goal
        #expect(sut.delta < 0)                        // goal below current
        #expect(sut.progress == 0)                    // current == initial → nothing achieved
    }

    @Test("loadData with a lose goal and a latest entry between start and goal reports partial progress")
    func loseGoalWithLatestEntry() async {
        let account = makeAccount(goalType: .lose, goalWeight: 1800, initialWeight: 2000)
        let (sut, _) = makeSUT(account: account, latest: entryWithWeight(1900))

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.progress > 0)
        #expect(sut.progress <= 1)
    }

    // MARK: - Maintain goal

    @Test("loadData with a maintain goal keeps progress at zero")
    func maintainGoalZeroProgress() async {
        let account = makeAccount(goalType: .maintain, goalWeight: 2000, initialWeight: 2000)
        let (sut, _) = makeSUT(account: account)

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.goalType == .maintain)
        #expect(sut.progress == 0)
    }

    // MARK: - Units

    @Test("loadData surfaces the kg weight unit")
    func kgUnitSurfaced() async {
        let account = makeAccount(goalType: .lose, weightUnit: .kg, goalWeight: 800, initialWeight: 1000)
        let (sut, _) = makeSUT(account: account)

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.unit == WeightUnit.kg.rawValue)
    }

    // MARK: - Weightless

    @Test("loadData reflects the weightless flag")
    func weightlessFlagReflected() async {
        let account = makeAccount(
            goalType: .lose,
            goalWeight: 1800,
            initialWeight: 2000,
            isWeightlessOn: true,
            weightlessWeight: 1000
        )
        let (sut, _) = makeSUT(account: account)

        let loaded = await waitUntil { sut.isLoaded }
        #expect(loaded == true)
        #expect(sut.weightlessOn == true)
    }

    // MARK: - Publisher-driven reload

    @Test("an entrySaved notification triggers a reload")
    func entrySavedTriggersReload() async {
        let account = makeAccount(goalType: .lose, goalWeight: 1800, initialWeight: 2000)
        let (sut, entry) = makeSUT(account: account)
        _ = await waitUntil { sut.isLoaded }

        entry.getLatestEntryResult = .success(entryWithWeight(1900))
        entry.entrySaved.send(EntryNotification(from: entryWithWeight(1900)))

        let reloaded = await waitUntil { sut.progress > 0 }
        #expect(reloaded == true)
    }
}
