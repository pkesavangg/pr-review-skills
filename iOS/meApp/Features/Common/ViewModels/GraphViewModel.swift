//
//  GraphViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

class GraphViewModel: ObservableObject {
    @Published var selectedEntry: BathScaleOperationDTO? = nil
    @Published var annotationHeight: CGFloat = 0
    @Published var selectedPointY: CGFloat = 0
    @Published var chartHeight: CGFloat = 0
    @Published var currentDateRange: ClosedRange<Date> = Date()...Date()
    @Published var isAnimating: Bool = false

    let yAxisTicks: [Double] = stride(from: 175, through: 190, by: 5).map { $0 }
    let goalWeight: Double = 178

    private let calendar = Calendar.current

    func yAxisTicksWithGoal() -> [Double] {
        var ticks = yAxisTicks
        if !ticks.contains(goalWeight) { ticks.append(goalWeight) }
        return ticks.sorted()
    }

    func xAxisDomain(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> ClosedRange<Date> {
        switch period {
        case .week:
            guard let start = weekStartDate(operations) else { return Date()...Date() }
            return start...calendar.date(byAdding: .day, value: 6, to: start)!
        case .month:
            guard let start = monthStartDate(operations),
                  let range = calendar.range(of: .day, in: .month, for: start) else { return Date()...Date() }
            return start...calendar.date(byAdding: .day, value: range.count - 1, to: start)!
        case .year:
            guard let start = yearStartDate(operations) else { return Date()...Date() }
            return start...calendar.date(byAdding: .month, value: 11, to: start)!
        case .total:
            let dates = operations.compactMap { $0.date }
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
            guard let start = weekStartDate(operations) else { return [] }
            return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }
        case .month:
            guard let start = monthStartDate(operations),
                  let range = calendar.range(of: .day, in: .month, for: start) else { return [] }
            let daysInMonth = range.count
            let gridCount = max(4, min(daysInMonth, 6))
            let step = Double(daysInMonth - 1) / Double(gridCount - 1)
            return (0..<gridCount).compactMap {
                let safeDay = min(1 + Int(round(step * Double($0))), daysInMonth)
                return calendar.date(bySetting: .day, value: safeDay, of: start)
            }
        case .year:
            guard let start = yearStartDate(operations) else { return [] }
            return (0..<12).compactMap { calendar.date(byAdding: .month, value: $0, to: start) }
        case .total:
            return []
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
            return nil
        }
    }

    func paddedOperations(for period: TimePeriod, operations: [BathScaleOperationDTO]) -> [BathScaleOperationDTO] {
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

    func getSelectedEntry(at location: CGPoint, proxy: ChartProxy, operations: [BathScaleOperationDTO]) -> (entry: BathScaleOperationDTO, pointY: CGFloat)? {
        guard let date: Date = proxy.value(atX: location.x) else { return nil }
        guard let nearest = operations
            .compactMap({ op -> (BathScaleOperationDTO, Date)? in
                guard let d = op.date else { return nil }
                return (op, d)
            })
            .min(by: { abs($0.1.timeIntervalSince(date)) < abs($1.1.timeIntervalSince(date)) })?.0 else {
            return nil
        }
        guard let weight = nearest.weight, let y = proxy.position(forY: weight) else { return nil }
        return (nearest, y)
    }

    // MARK: - Date Helpers

    private func weekStartDate(_ operations: [BathScaleOperationDTO]) -> Date? {
        calendar.dateInterval(of: .weekOfYear, for: operations.last?.date ?? Date())?.start
    }

    private func monthStartDate(_ operations: [BathScaleOperationDTO]) -> Date? {
        let date = operations.last?.date ?? Date()
        let comps = calendar.dateComponents([.year, .month], from: date)
        return calendar.date(from: comps)
    }

    private func yearStartDate(_ operations: [BathScaleOperationDTO]) -> Date? {
        let date = operations.last?.date ?? Date()
        return calendar.date(from: calendar.dateComponents([.year], from: date))
    }

    func periodPages(operations: [BathScaleOperationDTO], selectedPeriod: TimePeriod) -> [[BathScaleOperationDTO]] {
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

    func periodLabel(for ops: [BathScaleOperationDTO], period: TimePeriod) -> String? {
        guard let minDate = ops.compactMap(\.date).min(),
              let maxDate = ops.compactMap(\.date).max() else { return nil }
        func formatRange(start: Date, end: Date) -> String {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "LLL"
            let startMonth = formatter.string(from: start).lowercased()
            let startDay = calendar.component(.day, from: start)
            let endDay = calendar.component(.day, from: end)
            let year = calendar.component(.year, from: end)
            return "\(startMonth) \(startDay) - \(endDay), \(year)"
        }
        switch period {
        case .week:
            let start = calendar.dateInterval(of: .weekOfYear, for: maxDate)?.start ?? minDate
            return formatRange(start: start, end: calendar.date(byAdding: .day, value: 6, to: start)!)
        case .month:
            let comps = calendar.dateComponents([.year, .month], from: maxDate)
            guard let start = calendar.date(from: comps),
                  let range = calendar.range(of: .day, in: .month, for: start) else { return nil }
            return formatRange(start: start, end: calendar.date(byAdding: .day, value: range.count - 1, to: start)!)
        case .year:
            let comps = calendar.dateComponents([.year], from: maxDate)
            guard let start = calendar.date(from: comps) else { return nil }
            return formatRange(start: start, end: calendar.date(byAdding: .month, value: 11, to: start)!)
        case .total:
            return formatRange(start: minDate, end: maxDate)
        }
    }

    func weightLabel(operations: [BathScaleOperationDTO], selectedPeriod: TimePeriod, selectedPage: Int) -> String? {
        let pages = periodPages(operations: operations, selectedPeriod: selectedPeriod)
        guard selectedPage < pages.count else { return nil }
        return periodLabel(for: pages[selectedPage], period: selectedPeriod)
    }
}
