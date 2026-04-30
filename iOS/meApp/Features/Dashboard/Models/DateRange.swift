//
//  DateRange.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation

struct DateRange {
    let start: Date
    let end: Date

    var duration: TimeInterval {
        return end.timeIntervalSince(start)
    }

    var durationInDays: Int {
        return Calendar.current.dateComponents([.day], from: start, to: end).day ?? 0
    }
}
