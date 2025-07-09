//
//  DashboardStrings.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import Foundation

struct DashboardStrings {
    // Action buttons
    static let editDashboard = "Edit dashboard"
    static let updateGoal = "Update Goal"
    static let metricInfo = "Metric info"
    static let saveChanges = "Save Changes"
    static let resetDashboard = "Reset Dashboard"

    // Metrics labels, units, preLabels
    static let weight = "weight"
    static let bmi = "bmi"
    static let bodyFat = "body fat %"
    static let bodyFatUnit = "%"
    static let muscle = "muscle %"
    static let muscleUnit = "%"
    static let water = "water %"
    static let waterUnit = "%"
    static let heartBpm = "heart bpm"
    static let heartBpmUnit = "bpm"
    static let bone = "bone %"
    static let boneUnit = "%"
    static let visceralFat = "visceral fat"
    static let visceralFatPre = "Lv."
    static let subFat = "sub fat %"
    static let subFatUnit = "%"
    static let protein = "protein %"
    static let proteinUnit = "%"
    static let skelMuscle = "skel muscle"
    static let skelMuscleUnit = "%"
    static let bmrKcal = "bmr kcal"
    static let bmrKcalUnit = "kcal"
    static let metAge = "met age"
    static let metAgeUnit = "yrs"

    // Streak/Loss labels
    static let currentStreak = "current streak"
    static let longestStreak = "longest streak"
    static let lbsWeek = "lbs/week"
    static let lbsMonth = "lbs/month"
    static let lbsYear = "lbs/year"
    static let lbsTotal = "lbs/total"

    // Goal Card/Progress strings
    static let goalTypeLabel = "Goal Type: %@"
    static let plus  = "+"
    static let minus  = "-"
    static let placeholder = "--"
    static func loseGoalWeightLabel(_ unit: String) -> String{
        return "\(unit) to goal"
    }
    static func gainGoalWeightLabel(_ goalWeight: String, _ unit: String) -> String {
           return "\(unit) to \(goalWeight) \(unit) goal weight"
       }
    
    // Empty state messages
    static let noEntriesMessage = "To collect your first entry, connect a scale or add a manual entry."
    static func noEntriesInPeriodMessage(_ timePeriod: String) -> String {
        return "You haven't added any entries this \(timePeriod)."
    }
}
