//
//  GoalStreakGridView.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import UIKit
import SwiftUI

/// The UIKit grid view controller, similar to iOS home screen
class GoalStreakGridViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UICollectionViewDragDelegate, UICollectionViewDropDelegate {

    // MARK: - Properties

    var gridModel: MileStoneGridModel
    var store: DashboardStore
    var collectionView: UICollectionView!

    // MARK: - Init

    init(gridModel: MileStoneGridModel, store: DashboardStore) {
        self.gridModel = gridModel
        self.store = store
        super.init(nibName: nil, bundle: nil)
        setupCollectionView()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    // MARK: - View Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.addSubview(collectionView)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    // MARK: - Collection View Setup

    private func setupCollectionView() {
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 16         // gap between columns
        layout.minimumLineSpacing = 16              // gap between rows
        layout.sectionInset = UIEdgeInsets(top: 24, left: 24, bottom: 24, right: 24) // outer margin

        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.register(GoalCardCell.self, forCellWithReuseIdentifier: "GoalCardCell")
        collectionView.register(StreakCardCell.self, forCellWithReuseIdentifier: "StreakCardCell")
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.dragDelegate = self
        collectionView.dropDelegate = self
        collectionView.dragInteractionEnabled = true
        collectionView.allowsMultipleSelection = false
    }

    // MARK: - UICollectionViewDataSource

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        gridModel.mileStones.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let widget = gridModel.mileStones[indexPath.item]
        switch widget {
        case .goalCard:
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "GoalCardCell", for: indexPath) as! GoalCardCell
            cell.configure(with: store)
            return cell
        case .streak(let item):
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "StreakCardCell", for: indexPath) as! StreakCardCell
            cell.configure(with: item, store: store)
            return cell
        }
    }

    // MARK: - UICollectionViewDelegateFlowLayout

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let widget = gridModel.mileStones[indexPath.item]
        let contentWidth = collectionView.bounds.width - 24*2
        let interItemSpacing: CGFloat = 16

        switch widget {
        case .goalCard:
            return CGSize(width: contentWidth, height: 120) // Large widget spans full width
        case .streak:
            // 2 columns per row, gap between columns is 16pt
            let itemWidth = (contentWidth - interItemSpacing) / 2.0
            return CGSize(width: itemWidth, height: 70) // Small widget
        }
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, insetForSectionAt section: Int) -> UIEdgeInsets {
        // The outer margin for the whole grid
        return UIEdgeInsets(top: 24, left: 24, bottom: 24, right: 24)
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, minimumLineSpacingForSectionAt section: Int) -> CGFloat {
        // Vertical gap between rows, including streak rows and goal card rows
        return 16
    }

    // MARK: - Drag & Drop

    func collectionView(_ collectionView: UICollectionView, itemsForBeginning session: UIDragSession, at indexPath: IndexPath) -> [UIDragItem] {
        let widget = gridModel.mileStones[indexPath.item]
        let itemProvider: NSItemProvider
        switch widget {
        case .goalCard:
            itemProvider = NSItemProvider(object: "goalCard" as NSString)
        case .streak(let item):
            itemProvider = NSItemProvider(object: item.id.uuidString as NSString)
        }
        let dragItem = UIDragItem(itemProvider: itemProvider)
        dragItem.localObject = widget
        return [dragItem]
    }

    func collectionView(_ collectionView: UICollectionView, dragPreviewParametersForItemAt indexPath: IndexPath) -> UIDragPreviewParameters? {
        let params = UIDragPreviewParameters()
        params.backgroundColor = .clear
        params.visiblePath = UIBezierPath(roundedRect: collectionView.cellForItem(at: indexPath)?.bounds ?? .zero, cornerRadius: 10)
        return params
    }

    func collectionView(_ collectionView: UICollectionView, dropSessionDidUpdate session: UIDropSession, withDestinationIndexPath destinationIndexPath: IndexPath?) -> UICollectionViewDropProposal {
        return UICollectionViewDropProposal(operation: .move, intent: .insertAtDestinationIndexPath)
    }

    func collectionView(_ collectionView: UICollectionView, performDropWith coordinator: UICollectionViewDropCoordinator) {
        guard let destinationIndexPath = coordinator.destinationIndexPath,
              let item = coordinator.items.first,
              let sourceIndexPath = item.sourceIndexPath else { return }

        gridModel.moveWidget(from: sourceIndexPath.item, to: destinationIndexPath.item)
        collectionView.performBatchUpdates({
            collectionView.moveItem(at: sourceIndexPath, to: destinationIndexPath)
        }, completion: nil)

        // Save the new order to DashboardStore UI state
        persistGridOrderToStore()
    }
    
    /// Saves the current grid order to DashboardStore UI state
    private func persistGridOrderToStore() {
        var newStreakOrder: [MetricItem] = []
        var goalCardPosition: Int? = nil

        for (index, widget) in gridModel.mileStones.enumerated() {
            switch widget {
            case .goalCard:
                goalCardPosition = index
            case .streak(let streakItem):
                newStreakOrder.append(streakItem)
            }
        }

        // Save streak order as array of IDs
        store.state.ui.streakGridOrder = newStreakOrder.map { $0.id.uuidString }
        
        // Save goal card position
        store.state.ui.goalCardPosition = goalCardPosition ?? 0
        
        // Force UI update to reflect the changes
        store.objectWillChange.send()
    }
} 
