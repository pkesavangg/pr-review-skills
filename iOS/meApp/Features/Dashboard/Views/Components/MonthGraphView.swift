//
//  MonthGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import SwiftUI
import Charts

/// Dedicated view for rendering the Month time period chart
/// Uses BaseGraphView for common chart rendering functionality
struct MonthGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: MonthSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    
    var body: some View {
        BaseGraphView(
            viewModel: viewModel,
            dashboardStore: dashboardStore
        )
    }
}

#Preview {
    MonthGraphView(
        viewModel: MonthSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}