//
//  TotalGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Charts
import SwiftUI

/// Dedicated view for rendering the Total time period chart
/// Uses BaseGraphView for common chart rendering functionality
struct TotalGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: TotalSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    
    var body: some View {
        BaseGraphView(
            viewModel: viewModel,
            dashboardStore: dashboardStore
        )
    }
}

#Preview {
    TotalGraphView(
        viewModel: TotalSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
