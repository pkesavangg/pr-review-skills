//
//  DashboardStrings.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import Foundation

struct DashboardStrings {
    // Action buttons
    static let selectGraph = "Select Graph"
    static let editDashboard = "Edit dashboard"
    static let updateGoal = "Update Goal"
    static let metricInfo = "Metric info"
    static let saveChanges = "Save Changes"
    static let resetDashboard = "Reset Dashboard"

    // Dashboard Metrics
    static let customizeDashboardTitle = "Customize App Dashboard"
    static let customizeDashboardSubtitle = "Rearrange tiles and/or hide unwanted metrics from your dashboard."

    static let weight = "weight"
    static let bmi = "bmi"
    static let bodyFatBase = "body fat"
    static let muscleBase = "muscle"
    static let waterBase = "water"
    static let heartBase = "heart"
    static let boneBase = "bone"
    static let visceralFat = "visceral fat"
    static let subFatBase = "sub fat"
    static let proteinBase = "protein"
    static let skelMuscle = "skel muscle"
    static let bmrBase = "bmr"
    static let metAge = "meta age"

    // Units
    static let percentageUnitSymbol = "%"
    static let bpmUnitSymbol = "bpm"
    static let kcalUnitSymbol = "kcal"

    static let bodyFat = "\(bodyFatBase) \(percentageUnitSymbol)"
    static let muscle = "\(muscleBase) \(percentageUnitSymbol)"
    static let water = "\(waterBase) \(percentageUnitSymbol)"
    static let heartBpm = "\(heartBase) \(bpmUnitSymbol)"
    static let bone = "\(boneBase) \(percentageUnitSymbol)"
    static let visceralFatPre = "Lv."
    static let subFat = "\(subFatBase) \(percentageUnitSymbol)"
    static let protein = "\(proteinBase) \(percentageUnitSymbol)"
    static let bmrKcal = "\(bmrBase) \(kcalUnitSymbol)"
    static let metAgeUnit = "yrs"

    // Streak/Loss labels
    static let currentStreak = "current streak"
    static let longestStreak = "longest streak"
    static func daySuffix(forStreak streak: Int) -> String {
        // Streak-value display rule (per dashboard mock):
        //   1        → "day"  (singular)
        //   2…999    → "days" (full word)
        //   1000+    → "d"    (abbreviated, keeps the streak card width fixed — no layout expansion)
        if streak >= 1000 { return " d" }
        return streak == 1 ? " day" : " days"
    }
    static let lbsWeek = "lb/week"
    static let lbsMonth = "lb/month"
    static let lbsYear = "lb/year"
    static let lbsTotal = "lb/total"

    // Goal Card/Progress strings
    static let goalTypeLabel = "Goal Type: %@"
    static let plus = "+"
    static let minus = "-"
    static let placeholder = "--"
    static let goalReached = "goal reached!"
    static let reachYourGoals = "Reach Your Goals!"
    static let setGoalWeight = "Set a goal weight"
    static func loseGoalWeightLabel(_ unit: String) -> String {
        return "\(unit) to goal"
    }
    static func gainGoalWeightLabel(_ goalWeight: String, _ unit: String) -> String {
        return "\(unit) to \(goalWeight) \(unit) goal weight"
    }

    // Empty state messages
    static let noEntries = "no entries"
    static let noEntriesMessage = "To collect an entry, connect a device or add a manual entry."
    static let noReadingsYet = "No readings yet"
    static let connectDevice = "Connect Device"
    static func noEntriesInPeriodMessage(_ timePeriod: String) -> String {
        return "You haven't added any entries this \(timePeriod)."
    }

    // Reading arrival relative timestamp
    static let justNow = "Just now"
    static let oneMinuteAgo = "1 min ago"
    static let minutesAgoFormat = "%d min ago"
    static let yesterdayAtFormat = "Yesterday %@"

    // Baby reading arrival
    static let babyReadingArrivalTitle = "New Reading Received"
    static let babyReadingArrivalAssign = "ASSIGN"
    static let babyReadingArrivalDontAssign = "DON'T ASSIGN"

    // Baby reading arrival — no baby profile
    static let babyReadingNoProfileMessage = "Add a baby to save this reading."
    static let babyReadingNoProfileAddBaby = "ADD A BABY"
    static let babyReadingNoProfileDiscard = "DISCARD"

    // Assign baby modal
    static let assignMeasurementTitle = "Assign Measurement"
    static let assignMeasurementSubtitle = "Which baby is this measurement for?"
    static let assignMeasurementAssign = "ASSIGN"
    static let assignMeasurementAddBaby = "ADD A BABY"
    static let assignMeasurementDontAssign = "DON'T ASSIGN"
    static let assignMeasurementNewBaby = "Assign to new baby"
    static let assignMeasurementNewBabySubtitle = "create new baby profile"

    // Assigned baby toast
    static let babyReadingAssignedTo = "Reading assigned to"
    static let babyReadingWrongBaby = "Have you assigned to Wrong baby?"
    static let babyReadingReassign = "Reassign"

    // Weight scale reading arrival
    static let weightReadingArrivalTitle = "New Reading Received"
    static let weightReadingArrivalSave = "SAVE"
    static let weightReadingArrivalDiscard = "DISCARD"

    // BPM reading arrival
    static let bpmReadingArrivalTitle = "New Reading Received"
    static let bpmReadingArrivalSave = "SAVE"
    static let bpmReadingArrivalDiscard = "DISCARD"
    static let bpmReadingArrivalMmhg = "mmhg"
    static let bpmReadingArrivalPulse = "pulse"
    static let weightReadingArrivalJustNow = "Just now"

    // Graph scroll hint modal
    static let graphScrollHintTitle = "NEW: Scrollable Graph"
    static let graphScrollHintMessage = "Swipe left or right on the graph to view more growth data over time."
    static let graphScrollHintConfirm = "GOT IT"

    // Wi-Fi reading arrival (entry already saved server-side — VIEW navigates to History)
    static let readingArrivalView = "VIEW"
    static let wifiReadingArrivalTitle = "New Reading saved to your log"

    // Multiple readings counter (shown when a second reading arrives while a card is active)
    static func moreReadingsReceived(_ count: Int) -> String {
        "\(count) more reading\(count == 1 ? "" : "s") received for this session"
    }

    // Baby reading — single baby: baby name embedded in the toast title
    static func babyReadingArrivalTitleForSingleBaby(_ name: String) -> String {
        "New Reading Received for \(name)"
    }

    // MARK: - Accessibility (VoiceOver) — spoken text only, not shown on screen
    static let accSnapshotLogoLabel = "Weight Gurus"
    static let accWeightChartLabel = "Weight trend chart"
    static let accBpmChartLabel = "Blood pressure trend chart"
    static let accBabyChartLabel = "Baby growth trend chart"
    static let accChartXAxisName = "Date"
    static let accChartWeightYAxisName = "Weight"
    static let accChartBpmYAxisName = "Blood pressure"
    static let accChartBabyYAxisName = "Measurement"
    static let accRemoveMetricHint = "Double tap to remove from dashboard"
    static let accAddMetricHint = "Double tap to add back to dashboard"
    static let accMetricRemovedValue = "Hidden"
    static let accMetricVisibleValue = "Visible"
}
