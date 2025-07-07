//
//  GraphViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

final class GraphStore: ObservableObject {
    @Published var selectedEntry: BathScaleOperationDTO? = nil
    @Published var annotationHeight: CGFloat = 0
    @Published var selectedPointY: CGFloat = 0
    @Published var chartHeight: CGFloat = 0
    @Published var currentDateRange: ClosedRange<Date> = Date()...Date()
    @Published var isAnimating: Bool = false
    @Published var selectedWeight: Double? = nil
    @Published var selectedPeriod: TimePeriod = .week
    @Published var operations: [BathScaleOperationDTO] = []
    @Published var xScrollPosition: Date = Date()

    let yAxisTicks: [Double] = stride(from: 175, through: 190, by: 5).map { $0 }
    let goalWeight: Double = 178
    private let calendar = Calendar.current

    // MARK: - Computed Properties

    var continuousOperations: [BathScaleOperationDTO] {
        operations.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }
    }

    var weightLabel: String? {
        guard !operations.isEmpty else { return nil }
        
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? operations : visibleOps
        guard let minDate = opsToUse.compactMap(\.date).min(),
              let maxDate = opsToUse.compactMap(\.date).max() else { return nil }

        switch selectedPeriod {
        case .week:
            let month = DateTimeTools.formatter("LLL").string(from: minDate)
            let startDay = calendar.component(.day, from: minDate)
            let endDay = calendar.component(.day, from: maxDate)
            let year = calendar.component(.year, from: maxDate)
            return "\(month) \(startDay)-\(endDay), \(year)"
        case .month:
            return DateTimeTools.formatter("LLL yyyy").string(from: minDate)
        case .year:
            return DateTimeTools.formatter("yyyy").string(from: minDate)
        case .total:
            let minYear = calendar.component(.year, from: operations.compactMap(\.date).min() ?? Date())
            let maxYear = calendar.component(.year, from: operations.compactMap(\.date).max() ?? Date())
            return minYear == maxYear ? "\(minYear)" : "\(minYear)-\(maxYear)"
        }
    }

    var displayWeight: Double? {
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? operations : visibleOps
        
        switch selectedPeriod {
        case .week, .month:
            return opsToUse.last?.weight
        case .year, .total:
            let weights = opsToUse.compactMap(\.weight)
            guard !weights.isEmpty else { return nil }
            return weights.reduce(0, +) / Double(weights.count)
        }
    }

    // MARK: - Public Methods

    func updateOperations(_ newOperations: [BathScaleOperationDTO]) {
        operations = newOperations
        // Set scroll position to the latest date (like ContentView.swift)
        xScrollPosition = operations.compactMap(\.date).max() ?? Date()
        selectedWeight = displayWeight
    }

    func updateSelectedPeriod(_ period: TimePeriod) {
        selectedPeriod = period
        // Keep scroll position at the latest date when switching periods
        xScrollPosition = operations.compactMap(\.date).max() ?? Date()
        selectedWeight = displayWeight
    }

    func selectEntry(_ entry: BathScaleOperationDTO?) {
        selectedEntry = entry
        selectedWeight = entry?.weight
    }

    func yAxisTicksWithGoal() -> [Double] {
        var ticks = yAxisTicks
        if !ticks.contains(goalWeight) { ticks.append(goalWeight) }
        return ticks.sorted()
    }

    func xAxisValues(for period: TimePeriod) -> [Date] {
        let allDates = operations.compactMap(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        
        var dates: [Date] = []
        
        switch period {
        case .week:
            // For week view, show one mark per day across the entire data range
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.startOfDay(for: maxDate)
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
            }
            
        case .month:
            // For month view, show one mark per week
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.startOfDay(for: maxDate)
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
            }
            
        case .year:
            // For year view, show one mark per month
            var current = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let endComponents = calendar.dateComponents([.year, .month], from: maxDate)
            let end = calendar.date(from: endComponents) ?? maxDate
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            }
            
        case .total:
            // For total view, show one mark per quarter (3 months)
            var current = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let endComponents = calendar.dateComponents([.year, .month], from: maxDate)
            let end = calendar.date(from: endComponents) ?? maxDate
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 3, to: current) ?? current
            }
        }
        
        return dates
    }

    func xAxisLabels(for period: TimePeriod) -> [Date] {
        return xAxisValues(for: period)
    }

    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            return "\(calendar.component(.year, from: date))"
        }
    }

    func getSelectedEntry(at location: CGPoint, proxy: ChartProxy) -> (entry: BathScaleOperationDTO, pointY: CGFloat)? {
        guard let date: Date = proxy.value(atX: location.x) else { return nil }
        guard let nearest = operations
            .compactMap({ op -> (BathScaleOperationDTO, Date)? in
                guard let d = op.date else { return nil }
                return (op, d)
            })
            .min(by: { abs($0.1.timeIntervalSince(date)) < abs($1.1.timeIntervalSince(date)) })?.0,
              let weight = nearest.weight,
              let y = proxy.position(forY: weight) else { return nil }
        return (nearest, y)
    }

    func getFirstDateInAllOps() -> Date? {
        operations.compactMap(\.date).min()
    }

    func getLastDateInAllOps() -> Date? {
        operations.compactMap(\.date).max()
    }

    // MARK: - Scrollable Chart Helpers

    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week: return 7 * 24 * 60 * 60
        case .month: return 30 * 24 * 60 * 60
        case .year: return 365 * 24 * 60 * 60
        case .total: 
            // For total view, show a reasonable portion of the data at once
            let allDates = operations.compactMap(\.date)
            guard let minDate = allDates.min(), let maxDate = allDates.max() else {
                return 365 * 24 * 60 * 60 // Default to 1 year if no data
            }
            let totalRange = maxDate.timeIntervalSince(minDate)
            // Show about 1/4 of the total range, but minimum 1 year
            return max(totalRange / 4, 365 * 24 * 60 * 60)
        }
    }

    func timeSnapUnit(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week: return 24 * 60 * 60 // 1 day
        case .month: return 7 * 24 * 60 * 60 // 1 week
        case .year: return 30 * 24 * 60 * 60 // 1 month
        case .total: return 90 * 24 * 60 * 60 // 3 months
        }
    }

    private func getVisibleOperations() -> [BathScaleOperationDTO] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)
        
        return operations.filter { op in
            guard let date = op.date else { return false }
            return date >= visibleStart && date <= visibleEnd
        }
    }
}
