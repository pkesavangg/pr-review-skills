@testable import meApp
import Testing
import UIKit

@MainActor
private final class FixedContentSizeLayout: UICollectionViewLayout {
    let fixedContentSize: CGSize

    init(contentSize: CGSize) {
        self.fixedContentSize = contentSize
        super.init()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var collectionViewContentSize: CGSize {
        fixedContentSize
    }
}

@Suite(.serialized)
@MainActor
struct GridBoundaryDetectorTests {

    private func makeCollectionView(
        frame: CGRect = CGRect(x: 0, y: 40, width: 200, height: 120),
        contentSize: CGSize = CGSize(width: 200, height: 140),
        contentInset: UIEdgeInsets = .zero
    ) -> (container: UIView, collectionView: UICollectionView) {
        let container = UIView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 600))
        let layout = FixedContentSizeLayout(contentSize: contentSize)
        let collectionView = UICollectionView(frame: frame, collectionViewLayout: layout)
        collectionView.contentInset = contentInset
        container.addSubview(collectionView)
        return (container, collectionView)
    }

    private func makeStrictBufferZone(dividerY: CGFloat) -> CGRect {
        let excludeZone = CGRect(
            x: 0,
            y: max(0, dividerY - 16),
            width: UIScreen.main.bounds.width,
            height: 16
        )
        return excludeZone.insetBy(dx: -4, dy: -4)
    }

    @Test("goal streak constraints create a compact exclude zone above the divider")
    func goalStreakConstraintsCreateExcludeZone() {
        let constraints = GridBoundaryDetector.BoundaryConstraints.goalStreak(gridHeight: 180, dividerY: 120)

        #expect(constraints.maxHeight == 180)
        #expect(constraints.minY == 0)
        #expect(constraints.excludeZones.count == 1)
        #expect(constraints.excludeZones[0].origin.y == 104)
        #expect(constraints.excludeZones[0].height == 16)
    }

    @Test("updateGridBounds uses content height plus insets for the metric grid")
    func updateGridBoundsUsesContentHeightAndInsets() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 20, y: 50, width: 180, height: 90),
            contentSize: CGSize(width: 180, height: 160),
            contentInset: UIEdgeInsets(top: 8, left: 0, bottom: 12, right: 0)
        )
        _ = container

        detector.updateGridBounds(for: collectionView)

        let bounds = detector.getGridBounds()
        #expect(bounds.origin.x == 0)
        #expect(bounds.origin.y == 50)
        #expect(bounds.width == UIScreen.main.bounds.width)
        #expect(bounds.height == 180)
    }

    @Test("updateGoalStreakConstraints caps the grid height during bounds calculation")
    func updateGoalStreakConstraintsCapsHeight() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 10, width: 220, height: 100),
            contentSize: CGSize(width: 220, height: 240),
            contentInset: UIEdgeInsets(top: 10, left: 0, bottom: 10, right: 0)
        )
        _ = container

        detector.updateGoalStreakConstraints(gridHeight: 150, dividerY: 100)
        detector.updateGridBounds(for: collectionView)

        let bounds = detector.getGridBounds()
        #expect(bounds.origin.y == 10)
        #expect(bounds.height == 150)
    }

    @Test("isDragLocationWithinBounds returns true only for points inside the allowed region")
    func isDragLocationWithinBoundsTracksAllowedRegion() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 10, y: 30, width: 180, height: 100),
            contentSize: CGSize(width: 180, height: 120)
        )
        _ = container

        #expect(detector.isDragLocationWithinBounds(CGPoint(x: 50, y: 40), in: collectionView) == true)
        #expect(detector.isDragLocationWithinBounds(CGPoint(x: 50, y: 160), in: collectionView) == false)
    }

    @Test("exclude zones block drag locations even when the point is inside the raw bounds")
    func excludeZonesBlockDragLocations() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 0, width: 220, height: 150),
            contentSize: CGSize(width: 220, height: 200)
        )
        _ = container
        detector.updateGoalStreakConstraints(gridHeight: 200, dividerY: 120)

        #expect(detector.isDragLocationWithinBounds(CGPoint(x: 40, y: 110), in: collectionView) == false)
        #expect(detector.canDragAtLocation(CGPoint(x: 40, y: 40), in: collectionView) == true)
    }

    @Test("constrainDragLocation clamps points to the grid bounds")
    func constrainDragLocationClampsToGridBounds() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 0, width: 200, height: 100),
            contentSize: CGSize(width: 200, height: 100)
        )
        _ = container

        let constrained = detector.constrainDragLocation(CGPoint(x: -25, y: 160), in: collectionView)

        #expect(constrained.x == 0)
        #expect(constrained.y == 100)
    }

    @Test("constrainDragLocation pushes drags above the strict divider buffer zone")
    func constrainDragLocationPushesAboveStrictBufferZone() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 0, width: 220, height: 150),
            contentSize: CGSize(width: 220, height: 200)
        )
        _ = container
        detector.updateGoalStreakConstraints(gridHeight: 200, dividerY: 120)

        let constrained = detector.constrainDragLocation(CGPoint(x: 40, y: 110), in: collectionView)

        #expect(constrained.x == 40)
        #expect(constrained.y == 99)
    }

    @Test("constrainDragFrame keeps the dragged frame inside the grid bounds")
    func constrainDragFrameClampsToGridBounds() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 0, width: 220, height: 120),
            contentSize: CGSize(width: 220, height: 100)
        )
        _ = container
        let startingFrame = CGRect(x: UIScreen.main.bounds.width - 20, y: 90, width: 50, height: 30)

        let constrained = detector.constrainDragFrame(startingFrame, in: collectionView)

        #expect(constrained.origin.x == UIScreen.main.bounds.width - 50)
        #expect(constrained.origin.y == 70)
    }

    @Test("constrainDragFrame moves intersecting frames out of the divider buffer zone")
    func constrainDragFrameMovesOutOfExcludeZone() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView(
            frame: CGRect(x: 0, y: 0, width: 220, height: 150),
            contentSize: CGSize(width: 220, height: 200)
        )
        _ = container
        detector.updateGoalStreakConstraints(gridHeight: 200, dividerY: 120)
        let strictBufferZone = makeStrictBufferZone(dividerY: 120)

        let constrained = detector.constrainDragFrame(CGRect(x: 20, y: 110, width: 40, height: 20), in: collectionView)

        #expect(constrained.intersects(strictBufferZone) == false)
        #expect(constrained.minY < strictBufferZone.minY)
    }

    @Test("boundary state tracks transitions and reset clears cached bounds")
    func boundaryStateTracksTransitionsAndResetClearsState() {
        let detector = GridBoundaryDetector()
        let (container, collectionView) = makeCollectionView()
        _ = container

        detector.updateGridBounds(for: collectionView)
        detector.updateDragBoundaryState(true, for: collectionView, draggedItemId: "item-1")
        #expect(detector.isCurrentlyOutsideBounds == true)

        detector.updateDragBoundaryState(false, for: collectionView, draggedItemId: "item-1")
        #expect(detector.isCurrentlyOutsideBounds == false)

        detector.resetBoundaryState()
        #expect(detector.isCurrentlyOutsideBounds == false)
        #expect(detector.getGridBounds() == .zero)
    }

    @Test("non-collection views are rejected or passed through unchanged")
    func nonCollectionViewsAreHandledSafely() {
        let detector = GridBoundaryDetector()
        let plainView = UIView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
        let point = CGPoint(x: 12, y: 18)
        let frame = CGRect(x: 4, y: 6, width: 20, height: 30)

        #expect(detector.isDragLocationWithinBounds(point, in: plainView) == false)
        #expect(detector.canDragAtLocation(point, in: plainView) == false)
        #expect(detector.constrainDragLocation(point, in: plainView) == point)
        #expect(detector.constrainDragFrame(frame, in: plainView) == frame)
    }
}
