@testable import meApp
import Testing

@MainActor
private func makeStreak(_ label: String) -> MileStoneType {
    .streak(DashboardTestFixtures.makeMetricItem(label: label))
}

private func labels(for widgets: [MileStoneType]) -> [String] {
    widgets.map {
        switch $0 {
        case let .streak(item):
            return item.label
        case .goalCard:
            return "goal"
        }
    }
}

@Suite(.serialized)
@MainActor
struct MileStoneGridModelTests {

    @Test("construction preserves the provided milestone order")
    func constructionPreservesOrder() {
        let model = MileStoneGridModel(mileStones: [.goalCard, makeStreak("current"), makeStreak("longest")])

        #expect(labels(for: model.mileStones) == ["goal", "current", "longest"])
    }

    @Test("moveWidget moves the goal card like a normal item")
    func moveWidgetMovesGoalCardNormally() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), .goalCard, makeStreak("b"), makeStreak("c")])

        model.moveWidget(from: 1, to: 3)

        #expect(labels(for: model.mileStones) == ["a", "b", "c", "goal"])
    }

    @Test("moveWidget swaps immediate streak neighbors around the goal card")
    func moveWidgetSwapsImmediateNeighborsAroundGoalCard() {
        var model = MileStoneGridModel(mileStones: [makeStreak("left"), .goalCard, makeStreak("right"), makeStreak("tail")])

        model.moveWidget(from: 0, to: 2)

        #expect(labels(for: model.mileStones) == ["right", "goal", "left", "tail"])
    }

    @Test("moveWidget falls back to a normal move when no goal card exists")
    func moveWidgetFallsBackToNormalMoveWithoutGoalCard() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), makeStreak("b"), makeStreak("c")])

        model.moveWidget(from: 0, to: 2)

        #expect(labels(for: model.mileStones) == ["b", "c", "a"])
    }

    @Test("moveWidget ignores invalid indices and no-op moves")
    func moveWidgetIgnoresInvalidIndicesAndNoOpMoves() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), .goalCard, makeStreak("b")])

        model.moveWidget(from: 1, to: 1)
        model.moveWidget(from: -1, to: 0)
        model.moveWidget(from: 0, to: 9)

        #expect(labels(for: model.mileStones) == ["a", "goal", "b"])
    }

    @Test("moveWidget uses the normal move path when the neighbor swap target is not a streak")
    func moveWidgetFallsBackToNormalMoveWhenImmediateTargetIsNotStreak() {
        var model = MileStoneGridModel(mileStones: [makeStreak("left"), .goalCard, .goalCard, makeStreak("tail")])

        model.moveWidget(from: 0, to: 2)

        #expect(labels(for: model.mileStones) == ["goal", "goal", "left", "tail"])
    }

    @Test("reorderGrid leaves the layout unchanged when the goal card already starts a row")
    func reorderGridLeavesGoalAtRowStartUntouched() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), makeStreak("b"), .goalCard, makeStreak("c"), makeStreak("d")])

        model.reorderGrid(spanCount: 2)

        #expect(labels(for: model.mileStones) == ["a", "b", "goal", "c", "d"])
    }

    @Test("reorderGrid moves the goal card to the next row when it would split a row")
    func reorderGridMovesGoalCardToNextRow() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), .goalCard, makeStreak("b")])

        model.reorderGrid(spanCount: 2)

        #expect(labels(for: model.mileStones) == ["a", "b", "goal"])
    }

    @Test("reorderGrid skips automatic movement when streaks were removed")
    func reorderGridSkipsAutomaticMovementWhenStreaksWereRemoved() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), .goalCard, makeStreak("b")])

        model.reorderGrid(spanCount: 2, hasRemovedStreaks: true)

        #expect(labels(for: model.mileStones) == ["a", "goal", "b"])
    }

    @Test("reorderGrid leaves order unchanged when streak count is not divisible by the span count")
    func reorderGridLeavesOrderWhenStreakCountIsNotDivisibleBySpanCount() {
        var model = MileStoneGridModel(mileStones: [makeStreak("a"), .goalCard, makeStreak("b"), makeStreak("c")])

        model.reorderGrid(spanCount: 2)

        #expect(labels(for: model.mileStones) == ["a", "goal", "b", "c"])
    }
}
