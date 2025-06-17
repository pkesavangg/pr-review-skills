//
//  TimePeriod.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import Foundation

enum TimePeriod: String, CaseIterable, Identifiable {
    case week
    case month
    case year
    case total
    
    var id: String { self.rawValue }
    var displayName: String { self.rawValue }
}
