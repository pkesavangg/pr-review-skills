//
//  SkeletonMetricCell.swift
//  meApp
//
//  Created for skeleton loading in UIKit collection view
//

import SwiftUI
import UIKit

/// UICollectionViewCell that displays a skeleton loading state for metric cards
class SkeletonMetricCell: UICollectionViewCell {
    
    // MARK: - UI Components
    
    private var hostingController: UIHostingController<SkeletonMetricCardView>?
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        let skeletonView = SkeletonMetricCardView(dashboardType: .dashboard12)
        hostingController = UIHostingController(rootView: skeletonView)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    // MARK: - Configuration
    
    func configure(dashboardType: DashboardType) {
        let skeletonView = SkeletonMetricCardView(dashboardType: dashboardType)
        hostingController?.rootView = skeletonView
    }
    
    // MARK: - Private Methods
    
    private func setupUI() {
        guard let hostingController = hostingController else { return }
        hostingController.view.backgroundColor = .clear
        contentView.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: contentView.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }
}
