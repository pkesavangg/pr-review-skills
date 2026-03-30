//
//  BpmTrendView.swift
//  meApp
//
//  BP dashboard: headline values + graph + period selector.
//  Mirrors WeightTrendView structure but for blood pressure data.
//

import SwiftUI

struct BpmTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore

    var body: some View {
        DashboardTrendView(dashboardStore: dashboardStore) {
            BpmDisplayView(dashboardStore: dashboardStore)
        }
    }
}
