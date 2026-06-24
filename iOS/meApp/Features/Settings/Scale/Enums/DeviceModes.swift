//
//  DeviceModes.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

enum DeviceModes: String, CaseIterable, Identifiable {
    case allBodyMetrics = "All Body Metrics"
    case weightOnly = "Weight Only"
    var id: String { self.rawValue }
}
