@testable import meApp
import Testing

@MainActor
private func labels(for widgets: [MileStoneType]) -> [String] {
    widgets.map {
        switch $0 {
        case let .streak(item): return item.label
        case .goalCard: return "goal"
        }
    }
}

@MainActor
private func makeInputs(
    isEditMode: Bool = false,
    hasLoadedProgressMetrics: Bool = true,
    managerStreaks: [MetricItem] = [],
    streakItemsToShow: [MetricItem] = [],
    goalCardPosition: Int = 0,
    streakGridOrder: [String] = [],
    isGoalCardRemoved: Bool = false,
    removedLabels: Set<String> = [],
    isTablet: Bool = false
) -> GoalStreakGridBuilder.Inputs {
    GoalStreakGridBuilder.Inputs(
        isEditMode: isEditMode,
        hasLoadedProgressMetrics: hasLoadedProgressMetrics,
        managerStreaks: managerStreaks,
        streakItemsToShow: streakItemsToShow,
        goalCardPosition: goalCardPosition,
        streakGridOrder: streakGridOrder,
        isGoalCardRemoved: isGoalCardRemoved,
        isStreakRemoved: { removedLabels.contains($0) },
        isTablet: isTablet
    )
}

@MainActor
private func streaks(_ labels: [String]) -> [MetricItem] {
    labels.map { DashboardTestFixtures.makeMetricItem(label: $0) }
}

@Suite(.serialized)
@MainActor
struct GoalStreakGridBuilderTests {

    // MARK: - Empty / trivial cases

    @Test("returns empty grid when there are no streaks and the goal card is removed")
    func emptyWhenNoStreaksAndGoalRemoved() {
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(isGoalCardRemoved: true))
        #expect(model.mileStones.isEmpty)
    }

    @Test("returns only the goal card when API has not loaded and there are no streaks anywhere")
    func goalCardOnlyFallbackBeforeApiLoad() {
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(hasLoadedProgressMetrics: false))
        #expect(labels(for: model.mileStones) == ["goal"])
    }

    @Test("returns empty grid when API not loaded, no streaks anywhere, and goal card removed")
    func emptyWhenFallbackAndGoalRemoved() {
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(hasLoadedProgressMetrics: false, isGoalCardRemoved: true))
        #expect(model.mileStones.isEmpty)
    }

    // MARK: - Pre-API fallback to manager streaks

    @Test("uses manager streaks before API loads, in non-edit mode")
    func usesManagerStreaksBeforeApiLoad() {
        let manager = streaks(["a", "b"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            hasLoadedProgressMetrics: false,
            managerStreaks: manager,
            streakItemsToShow: []
        ))
        #expect(labels(for: model.mileStones).contains("a"))
        #expect(labels(for: model.mileStones).contains("b"))
    }

    @Test("prefers streakItemsToShow in edit mode, even before API loads")
    func prefersShownStreaksInEditMode() {
        let manager = streaks(["manager1"])
        let shown = streaks(["shown1"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            hasLoadedProgressMetrics: false,
            managerStreaks: manager,
            streakItemsToShow: shown
        ))
        #expect(labels(for: model.mileStones).contains("shown1"))
        #expect(!labels(for: model.mileStones).contains("manager1"))
    }

    // MARK: - Streak ordering from streakGridOrder

    @Test("non-edit mode respects streakGridOrder ids and appends any missing streaks")
    func nonEditModeRespectsOrderAndAppendsMissing() {
        let items = streaks(["a", "b", "c"])
        let order = [items[2].id.uuidString, items[0].id.uuidString]
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            goalCardPosition: 0,
            streakGridOrder: order,
            isGoalCardRemoved: true
        ))
        #expect(labels(for: model.mileStones) == ["c", "a", "b"])
    }

    @Test("non-edit mode ignores streakGridOrder when it is empty")
    func nonEditModeUsesDefaultOrderWhenNoSavedOrder() {
        let items = streaks(["a", "b"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            streakGridOrder: [],
            isGoalCardRemoved: true
        ))
        #expect(labels(for: model.mileStones) == ["a", "b"])
    }

    // MARK: - Goal card placement

    @Test("goal card at position 0 sits before the streaks")
    func goalAtStart() {
        let items = streaks(["a", "b", "c", "d"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            goalCardPosition: 0
        ))
        #expect(labels(for: model.mileStones).first == "goal")
    }

    @Test("goal card at the end of full rows (phone, 4 streaks) snaps to a row-start index")
    func goalCardSnapsToRowStartWithFullRows() {
        let items = streaks(["a", "b", "c", "d"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            goalCardPosition: 3
        ))
        // Non-edit + full rows + columns=2 → clampedGoalPos (3) → row-start = 2
        // Expected visual order: [a, b, goal, c, d]
        #expect(labels(for: model.mileStones) == ["a", "b", "goal", "c", "d"])
    }

    @Test("goal card at an incomplete-last-row position is placed exactly (phone, 3 streaks)")
    func goalCardAtExactPositionWhenLastRowIncomplete() {
        let items = streaks(["a", "b", "c"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            goalCardPosition: 1
        ))
        // 3 streaks on 2 columns → last row incomplete → exact placement allowed at index 1
        #expect(labels(for: model.mileStones) == ["a", "goal", "b", "c"])
    }

    @Test("edit mode with full rows snaps an odd goal position down to the preceding even slot")
    func editModeEvenSnapForFullRows() {
        let items = streaks(["a", "b", "c", "d"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            streakItemsToShow: items,
            goalCardPosition: 3
        ))
        // Edit + full rows + odd clampedGoalPos=3 → even-snap to 2
        #expect(labels(for: model.mileStones) == ["a", "b", "goal", "c", "d"])
    }

    // MARK: - Tablet column count

    @Test("tablet uses 4 columns: 4 streaks with goalCardPosition at 4 places goal card at the end")
    func tabletColumnCountRespected() {
        let items = streaks(["a", "b", "c", "d"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            goalCardPosition: 4,
            isTablet: true
        ))
        // 4 streaks on 4 columns → full row → clampedGoalPos=4, row-start=4 → goal after all streaks
        #expect(labels(for: model.mileStones) == ["a", "b", "c", "d", "goal"])
    }

    // MARK: - Removed streaks

    @Test("non-edit mode hides removed streaks")
    func nonEditModeHidesRemovedStreaks() {
        let items = streaks(["a", "b", "c"])
        // streakItemsToShow already excludes removed streaks in production; the builder treats
        // passed-in items as the source of truth for non-edit visibility.
        let shown = [items[0], items[2]]
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: shown,
            goalCardPosition: 0,
            isGoalCardRemoved: true
        ))
        #expect(labels(for: model.mileStones) == ["a", "c"])
    }

    @Test("edit mode shows removed streaks after the active section")
    func editModeAppendsRemovedStreaksAfterActive() {
        let items = streaks(["a", "b", "c"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            streakItemsToShow: items,
            goalCardPosition: 0,
            removedLabels: ["b"]
        ))
        // Active: [a, c]; removed: [b] appended; goal at start (position 0)
        #expect(labels(for: model.mileStones) == ["goal", "a", "c", "b"])
    }

    @Test("edit mode appends the goal card at the end when it is removed")
    func editModeAppendsRemovedGoalCardLast() {
        let items = streaks(["a", "b"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            streakItemsToShow: items,
            isGoalCardRemoved: true
        ))
        #expect(labels(for: model.mileStones) == ["a", "b", "goal"])
    }

    @Test("removed streaks preserve the saved goal-card position (Android parity)")
    func removedStreaksPreserveSavedGoalCardPosition() {
        // 5 streaks with `e` removed → 4 active (full rows) forces `lastRowIncomplete=false`,
        // so the only path that preserves goalCardPosition=3 is `hasRemovedStreaks`.
        let items = streaks(["a", "b", "c", "d", "e"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            managerStreaks: items,
            streakItemsToShow: items,
            goalCardPosition: 3,
            removedLabels: ["e"]
        ))
        // Without the fix, edit-mode + full rows would even-snap position 3 down to 2 and
        // render [a, b, goal, c, d, e] instead.
        #expect(labels(for: model.mileStones) == ["a", "b", "c", "goal", "d", "e"])
    }

    @Test("re-adding a streak does not yank the goal card to a new row")
    func reAddingStreakKeepsGoalCardInPlace() {
        // 3 streaks with `c` removed → 2 active (full row) forces `lastRowIncomplete=false`,
        // so only the `hasRemovedStreaks` branch can keep goalCardPosition=1.
        let items = streaks(["a", "b", "c"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: true,
            managerStreaks: items,
            streakItemsToShow: items,
            goalCardPosition: 1,
            removedLabels: ["c"]
        ))
        // Without the fix, edit-mode full-rows would snap odd position 1 down to 0 and
        // jump the goal card above `a`, rendering [goal, a, b, c].
        #expect(labels(for: model.mileStones) == ["a", "goal", "b", "c"])
    }

    @Test("post-save non-edit mode preserves the user's goal-card position when streaks are removed")
    func postSaveNonEditPreservesGoalCardPosition() {
        // Post-save: non-edit mode, streakItemsToShow is pre-filtered to active only.
        // User has one removed streak ("c"), and placed the goal card at position 1.
        let managerStreaks = streaks(["a", "b", "c"])
        let activeOnly = [managerStreaks[0], managerStreaks[1]]
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            isEditMode: false,
            managerStreaks: managerStreaks,
            streakItemsToShow: activeOnly,
            goalCardPosition: 1,
            removedLabels: ["c"]
        ))
        // Goal card stays between `a` and `b`. Before the fix it snapped to row-start 0
        // because split.removed was empty and the builder mistakenly saw "no removed streaks".
        #expect(labels(for: model.mileStones) == ["a", "goal", "b"])
    }

    // MARK: - Goal card removed

    @Test("goal card removed: streaks appear alone in non-edit mode")
    func goalRemovedRendersStreaksOnly() {
        let items = streaks(["a", "b"])
        let model = GoalStreakGridBuilder.build(inputs: makeInputs(
            streakItemsToShow: items,
            isGoalCardRemoved: true
        ))
        #expect(labels(for: model.mileStones) == ["a", "b"])
    }
}
