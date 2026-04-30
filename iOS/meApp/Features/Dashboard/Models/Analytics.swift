//
//  DataAnalytics.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation

struct DataAnalytics {
    let totalEntries: Int
    let dailyEntries: Int
    let monthlyEntries: Int
    let dateRange: DateRange?
    let dataCompleteness: Double
    let cacheSize: Int
    let lastUpdated: Date

    var completenessPercentage: String {
        return String(format: "%.1f%%", dataCompleteness * 100)
    }

    var cacheSizeFormatted: String {
        let kb = Double(cacheSize) / 1024
        if kb < 1024 {
            return String(format: "%.1f KB", kb)
        } else {
            let mb = kb / 1024
            return String(format: "%.1f MB", mb)
        }
    }
}

// MARK: - Supporting Types
struct GoalAnalytics {
    let daysToGoal: Int?
    let weeklyTarget: Double?
    let currentTrend: GoalTrend
    let progressPercentage: Double
}


// MARK: - Supporting Types
struct StreakAnalytics {
    let currentStreak: Int
    let longestStreak: Int
    let weeklyChange: Double
    let monthlyChange: Double
    let yearlyChange: Double
    let totalChange: Double
    let trend: StreakTrend
    let momentum: StreakMomentum

    var isOnTrack: Bool {
        return trend != .broken && momentum != .slowing
    }

    var streakRatio: Double {
        guard longestStreak > 0 else { return 0.0 }
        return Double(currentStreak) / Double(longestStreak)
    }
}
