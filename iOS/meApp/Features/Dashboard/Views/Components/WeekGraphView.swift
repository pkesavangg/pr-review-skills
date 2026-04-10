//
//  WeekGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import SwiftUI

/// Dedicated view for rendering the Week time period chart
/// Uses BaseGraphView for common chart rendering functionality
struct WeekGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: WeekSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    
    var body: some View {
        BaseGraphView(
            viewModel: viewModel,
            dashboardStore: dashboardStore
        )
    }
}

#Preview {
    WeekGraphView(
        viewModel: WeekSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
