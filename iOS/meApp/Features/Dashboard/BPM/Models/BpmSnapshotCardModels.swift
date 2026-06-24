//
//  BpmSnapshotCardModels.swift
//  meApp
//
//  Data models used by BpmSnapshotCard for chart rendering and headline display.
//

import SwiftUI

struct BpmChartPoint: Identifiable {
    let id: String
    let date: Date
    let value: Double
    let series: String
    let color: Color
}

struct BpmLatestReading {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
}
