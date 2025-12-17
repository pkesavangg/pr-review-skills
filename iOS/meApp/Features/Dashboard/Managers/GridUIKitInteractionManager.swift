//
//  GridUIKitInteractionManager.swift
//  meApp
//
//  Shared UIKit grid helpers for Dashboard collection views.
//

import UIKit

/// Shared UIKit grid helpers for consistent configuration and responsive gesture behavior.
struct GridUIKitInteractionManager {
    /// Applies common configuration used across dashboard grids.
    /// Keeps system drag disabled (interactive movement is used instead).
    static func applyCommonCollectionViewConfiguration(_ collectionView: CustomCollectionView) {
        collectionView.backgroundColor = .clear
        collectionView.hideDragPlatter = true
        if #available(iOS 11.0, *) {
            collectionView.reorderingCadence = .immediate
        }

        // Allow the lifted item overlay to render beyond bounds without clipping
        collectionView.clipsToBounds = false
        collectionView.layer.masksToBounds = false

        // Disable selection and scrolling; allow intrinsic size calculation
        collectionView.allowsSelection = false
        collectionView.isScrollEnabled = false
        collectionView.showsVerticalScrollIndicator = false
        collectionView.showsHorizontalScrollIndicator = false
        collectionView.contentInsetAdjustmentBehavior = .never

        // Suppress implicit layer animations for smooth drag/reorder visuals
        collectionView.layer.actions = [
            "position": NSNull(),
            "bounds": NSNull(),
            "transform": NSNull(),
            "opacity": NSNull(),
            "onOrderIn": NSNull(),
            "onOrderOut": NSNull(),
            "sublayers": NSNull(),
            "contents": NSNull(),
            "hidden": NSNull(),
            "cornerRadius": NSNull()
        ]

        // Ensure system drag stays disabled; we use interactive movement.
        collectionView.dragInteractionEnabled = false
    }

    /// Adds a no-op tap recognizer that keeps taps responsive inside the grid and prevents parent/background
    /// tap handlers from stealing touches (mirrors MetricGridUIKitView behavior).
    static func addTapSink(to collectionView: UICollectionView, target: Any, action: Selector) {
        let tap = UITapGestureRecognizer(target: target, action: action)
        tap.cancelsTouchesInView = false
        tap.delaysTouchesBegan = false
        tap.delaysTouchesEnded = false
        collectionView.addGestureRecognizer(tap)
    }
}


