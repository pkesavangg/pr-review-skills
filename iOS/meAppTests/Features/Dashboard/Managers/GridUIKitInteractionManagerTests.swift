import Testing
import UIKit
@testable import meApp

@MainActor
private final class GridTapSinkTarget: NSObject {
    @objc func handleTap() {}
}

@Suite(.serialized)
@MainActor
struct GridUIKitInteractionManagerTests {

    @Test("applyCommonCollectionViewConfiguration sets the shared dashboard grid flags")
    func applyCommonCollectionViewConfigurationSetsExpectedFlags() {
        let layout = UICollectionViewFlowLayout()
        let collectionView = CustomCollectionView(frame: CGRect(x: 0, y: 0, width: 200, height: 120), collectionViewLayout: layout)

        collectionView.backgroundColor = .red
        collectionView.hideDragPlatter = false
        collectionView.clipsToBounds = true
        collectionView.layer.masksToBounds = true
        collectionView.allowsSelection = true
        collectionView.isScrollEnabled = true
        collectionView.showsVerticalScrollIndicator = true
        collectionView.showsHorizontalScrollIndicator = true
        collectionView.dragInteractionEnabled = true

        GridUIKitInteractionManager.applyCommonCollectionViewConfiguration(collectionView)

        #expect(collectionView.backgroundColor == .clear)
        #expect(collectionView.hideDragPlatter == true)
        #expect(collectionView.reorderingCadence == .immediate)
        #expect(collectionView.clipsToBounds == false)
        #expect(collectionView.layer.masksToBounds == false)
        #expect(collectionView.allowsSelection == false)
        #expect(collectionView.isScrollEnabled == false)
        #expect(collectionView.showsVerticalScrollIndicator == false)
        #expect(collectionView.showsHorizontalScrollIndicator == false)
        #expect(collectionView.contentInsetAdjustmentBehavior == .never)
        #expect(collectionView.dragInteractionEnabled == false)

        let actionKeys = Set((collectionView.layer.actions ?? [:]).keys)
        #expect(actionKeys.contains("position"))
        #expect(actionKeys.contains("bounds"))
        #expect(actionKeys.contains("transform"))
        #expect(actionKeys.contains("opacity"))
        #expect(actionKeys.contains("hidden"))
        #expect(actionKeys.contains("cornerRadius"))
    }

    @Test("addTapSink appends a non-canceling tap recognizer")
    func addTapSinkAppendsNonCancelingTapRecognizer() {
        let collectionView = UICollectionView(frame: .zero, collectionViewLayout: UICollectionViewFlowLayout())
        let existingRecognizers = collectionView.gestureRecognizers ?? []

        let target = GridTapSinkTarget()
        GridUIKitInteractionManager.addTapSink(to: collectionView, target: target, action: #selector(GridTapSinkTarget.handleTap))

        let recognizers = collectionView.gestureRecognizers ?? []
        #expect(recognizers.count == existingRecognizers.count + 1)

        let tapRecognizer = recognizers
            .compactMap { $0 as? UITapGestureRecognizer }
            .first(where: { recognizer in
                !existingRecognizers.contains { $0 === recognizer }
            })
        #expect(tapRecognizer != nil)
        #expect(tapRecognizer?.cancelsTouchesInView == false)
        #expect(tapRecognizer?.delaysTouchesBegan == false)
        #expect(tapRecognizer?.delaysTouchesEnded == false)
    }
}
