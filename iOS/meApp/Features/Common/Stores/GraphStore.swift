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
    @Published var selectedPage: Int = 0
    @Published var selectedPeriod: TimePeriod = .week
    @Published var operations: [BathScaleOperationDTO] = []

    let yAxisTicks: [Double] = stride(from: 175, through: 190, by: 5).map { $0 }
    let goalWeight: Double = 178
    private let calendar = Calendar.current

    // MARK: - Computed Properties

    var periodPages: [[BathScaleOperationDTO]] {
        Self.periodPages(operations: operations, selectedPeriod: selectedPeriod, calendar: calendar)
    }

    var continuousOperations: [BathScaleOperationDTO] {
        operations.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }
    }

    var weightLabel: String? {
        guard !operations.isEmpty else { return nil }
        let pages = periodPages
        guard selectedPage < pages.count else { return nil }
        let pageOps = pages[selectedPage]
        guard let minDate = pageOps.compactMap(\.date).min(),
              let maxDate = pageOps.compactMap(\.date).max() else { return nil }

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

    // --- Display Value for Weight ---
    /// For .week/.month: latest entry in current page. For .year/.total: average of current page.
    var displayWeight: Double? {
        let pages = periodPages
        guard selectedPage < pages.count else { return nil }
        let pageOps = pages[selectedPage]
        switch selectedPeriod {
        case .week, .month:
            return pageOps.last?.weight
        case .year, .total:
            let weights = pageOps.compactMap(\.weight)
            guard !weights.isEmpty else { return nil }
            return weights.reduce(0, +) / Double(weights.count)
        }
    }

    // MARK: - Public Methods

    func updateOperations(_ newOperations: [BathScaleOperationDTO]) {
        operations = newOperations
        selectedWeight = displayWeight
        selectedPage = getCurrentPeriodPageIndex(for: selectedPeriod)
    }

    func updateSelectedPeriod(_ period: TimePeriod) {
        selectedPeriod = period
        selectedPage = getCurrentPeriodPageIndex(for: period)
        selectedWeight = displayWeight
    }

    func selectEntry(_ entry: BathScaleOperationDTO?) {
        selectedEntry = entry
        selectedWeight = entry?.weight
    }

    func getPaddedOperations(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> [BathScaleOperationDTO] {
        guard !operations.isEmpty else { return [] }
        let domain = xAxisDomain(for: period, operations: operations)
        var padded = operations.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }
        let isoFormatter = ISO8601DateFormatter()

        if let first = padded.first, let firstDate = first.date,
           firstDate > domain.lowerBound,
           operations.contains(where: { ($0.date ?? .distantPast) < domain.lowerBound }) {
            padded.insert(first.copy(with: isoFormatter.string(from: domain.lowerBound)), at: 0)
        }

        if let last = padded.last, let lastDate = last.date,
           lastDate < domain.upperBound,
           operations.contains(where: { ($0.date ?? .distantFuture) > domain.upperBound }) {
            padded.append(last.copy(with: isoFormatter.string(from: domain.upperBound)))
        }

        return padded
    }

    func getCurrentPageDomain(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> ClosedRange<Date> {
        xAxisDomain(for: period, operations: operations)
    }

    func getExtendedDomain(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> ClosedRange<Date> {
        let currentDomain = getCurrentPageDomain(for: period, operations: operations)
        let paddingInterval: TimeInterval = {
            switch period {
            case .week: return 7 * 24 * 3600
            case .month: return 30 * 24 * 3600
            case .year: return 365 * 24 * 3600
            case .total: return 0
            }
        }()
        let extendedStart = currentDomain.lowerBound.addingTimeInterval(-paddingInterval)
        let extendedEnd = currentDomain.upperBound.addingTimeInterval(paddingInterval)
        return extendedStart...extendedEnd
    }

    func getContinuousLineOperations(allOperations: [BathScaleOperationDTO], currentPageOperations: [BathScaleOperationDTO], period: TimePeriod) -> [BathScaleOperationDTO] {
        let extendedDomain = getExtendedDomain(for: period, operations: currentPageOperations)
        return allOperations.filter {
            guard let date = $0.date else { return false }
            return extendedDomain.contains(date)
        }.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }
    }

    func getFirstDateInAllOps(_ operations: [BathScaleOperationDTO]) -> Date? {
        operations.compactMap(\.date).min()
    }

    func getLastDateInAllOps(_ operations: [BathScaleOperationDTO]) -> Date? {
        operations.compactMap(\.date).max()
    }

    func yAxisTicksWithGoal() -> [Double] {
        var ticks = yAxisTicks
        if !ticks.contains(goalWeight) { ticks.append(goalWeight) }
        return ticks.sorted()
    }

    func xAxisDomain(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> ClosedRange<Date> {
        switch period {
        case .week:
            guard let start = Self.periodStartDate(.week, operations, calendar) else { return Date()...Date() }
            return start...calendar.date(byAdding: .day, value: 6, to: start)!
        case .month:
            guard let start = Self.periodStartDate(.month, operations, calendar),
                  let range = calendar.range(of: .day, in: .month, for: start) else { return Date()...Date() }
            return start...calendar.date(byAdding: .day, value: range.count - 1, to: start)!
        case .year:
            guard let start = Self.periodStartDate(.year, operations, calendar) else { return Date()...Date() }
            return start...calendar.date(byAdding: .month, value: 11, to: start)!
        case .total:
            let dates = operations.compactMap(\.date)
            guard let min = dates.min(), let max = dates.max() else {
                let now = Date()
                return now...now
            }
            return min...max
        }
    }

    func xAxisLabels(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> [Date] {
        switch period {
        case .week:
            guard let start = Self.periodStartDate(.week, operations, calendar) else { return [] }
            return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }
        case .month:
            guard let start = Self.periodStartDate(.month, operations, calendar),
                  let range = calendar.range(of: .day, in: .month, for: start) else { return [] }
            let daysInMonth = range.count
            let gridCount = max(4, min(daysInMonth, 6))
            let step = Double(daysInMonth - 1) / Double(gridCount - 1)
            return (0..<gridCount).compactMap {
                let safeDay = min(1 + Int(round(step * Double($0))), daysInMonth)
                return calendar.date(bySetting: .day, value: safeDay, of: start)
            }
        case .year:
            guard let start = Self.periodStartDate(.year, operations, calendar) else { return [] }
            return (0..<12).compactMap { calendar.date(byAdding: .month, value: $0, to: start) }
        case .total:
            let dates = operations.compactMap(\.date)
            guard let first = dates.min(), let last = dates.max() else { return [] }
            let firstYear = calendar.component(.year, from: first)
            let lastYear = calendar.component(.year, from: last)
            return (firstYear...lastYear).compactMap { year in
                var comps = calendar.dateComponents([.year, .month, .day], from: first)
                comps.year = year
                comps.month = 1
                comps.day = 1
                return calendar.date(from: comps)
            }
        }
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

    func getSelectedEntry(at location: CGPoint, proxy: ChartProxy, operations: [BathScaleOperationDTO]) -> (entry: BathScaleOperationDTO, pointY: CGFloat)? {
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

    // MARK: - Static Period Helpers

    static func periodStartDate(_ period: TimePeriod, _ operations: [BathScaleOperationDTO], _ calendar: Calendar) -> Date? {
        let date = operations.last?.date ?? Date()
        switch period {
        case .week:
            return calendar.dateInterval(of: .weekOfYear, for: date)?.start
        case .month:
            let comps = calendar.dateComponents([.year, .month], from: date)
            return calendar.date(from: comps)
        case .year:
            return calendar.date(from: calendar.dateComponents([.year], from: date))
        case .total:
            return nil
        }
    }

    static func periodPages(operations: [BathScaleOperationDTO], selectedPeriod: TimePeriod, calendar: Calendar) -> [[BathScaleOperationDTO]] {
        guard selectedPeriod != .total else {
            return [operations.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }]
        }
        let grouped = Dictionary(grouping: operations) { op -> Date in
            let date = op.date ?? Date()
            switch selectedPeriod {
            case .week:
                return calendar.dateInterval(of: .weekOfYear, for: date)?.start ?? calendar.startOfDay(for: date)
            case .month:
                return calendar.date(from: calendar.dateComponents([.year, .month], from: date)) ?? date
            case .year:
                return calendar.date(from: calendar.dateComponents([.year], from: date)) ?? date
            case .total:
                return .distantPast
            }
        }
        return grouped
            .sorted { $0.key < $1.key }
            .map { $0.value.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) } }
    }

    // MARK: - Current Period Helpers

    private func getCurrentPeriodPageIndex(for period: TimePeriod) -> Int {
        let pages = periodPages
        let currentDate = Date()
        guard !pages.isEmpty else { return 0 }
        let match: (Date) -> Bool = {
            switch period {
            case .week: return { self.calendar.isDate($0, equalTo: currentDate, toGranularity: .weekOfYear) }
            case .month: return { self.calendar.isDate($0, equalTo: currentDate, toGranularity: .month) }
            case .year: return { self.calendar.isDate($0, equalTo: currentDate, toGranularity: .year) }
            case .total: return { _ in false }
            }
        }()
        for (index, page) in pages.enumerated() {
            if let first = page.first?.date, match(first) {
                return index
            }
        }
        return max(pages.count - 1, 0)
    }
}
