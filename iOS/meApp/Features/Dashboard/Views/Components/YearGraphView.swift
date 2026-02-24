//
//  YearGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import SwiftUI

/// Dedicated view for rendering the Year time period chart
/// Uses BaseGraphView for common chart rendering functionality
struct YearGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: YearSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    
    var body: some View {
        BaseGraphView(
            viewModel: viewModel,
            dashboardStore: dashboardStore
        )
    }
}

#Preview {
    YearGraphView(
        viewModel: YearSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
