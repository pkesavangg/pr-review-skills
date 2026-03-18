import Foundation

/// Service implementation for managing dashboard date range calculations,
/// period label formatting, and date filtering operations.
@MainActor
final class DashboardDateRangeManager: DashboardDateRangeManagerProtocol {
    
    // MARK: - Private Properties
    
    private let calendar = Calendar.current
    
    // MARK: - Date Range Calculations
    
    func getYearLabelDateRange(xScrollPosition: Date) -> (start: Date, end: Date)? {
        let leftEdge = xScrollPosition
        
        // Align to the start of the month containing the left edge
        let start = calendar.dateInterval(of: .month, for: leftEdge)?.start ?? calendar.startOfDay(for: leftEdge)
        guard let endExclusive = calendar.date(byAdding: .month, value: 12, to: start) else {
            return nil
        }
        let endInclusive = inclusiveEnd(fromExclusive: endExclusive)
        
        // Keep label aligned to the visible 12-month window, even if trailing months
        // have no entries. This matches the rendered year grid/ticks behavior.
        return (start: start, end: endInclusive)
    }
    
    func getLabelDateRangeForMonth(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        continuousOperations: [BathScaleWeightSummary]
    ) -> DateInterval {
        let today = Date()
        let hasAnyOps = !continuousOperations.isEmpty
        let lastEntryDate = continuousOperations.last?.date
        
        if let monthInterval = getFullyContainedMonthInterval(
            xScrollPosition: xScrollPosition,
            visibleDomainLength: visibleDomainLength
        ) {
            // A full month is visible; always label the full calendar month.
            let fullMonth = DateInterval(
                start: monthInterval.start,
                end: inclusiveEnd(fromExclusive: monthInterval.end)
            )
            return fullMonth
        }
        
        let leftEdge = xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(visibleDomainLength)
        let visibleWindow = DateInterval(start: leftEdge, end: inclusiveEnd(fromExclusive: rightEdge))
        
        // If the visible window crosses into a *future* month that has no entries,
        // clamp the label to the end of the current month. This prevents labels like
        // "Feb 13 – Mar 17, 2026" when the chart does not render March grid lines/ticks.
        //
        // We only clamp when:
        // - The label would extend beyond the end of the current calendar month (today's month), AND
        // - The latest entry is not beyond that month (i.e., no data in the future month).
        if hasAnyOps,
           let currentMonth = calendar.dateInterval(of: .month, for: today) {
            let endOfCurrentMonthInclusive = inclusiveEnd(fromExclusive: currentMonth.end)
            let crossesIntoFutureMonth = visibleWindow.end > endOfCurrentMonthInclusive
            let noEntriesBeyondCurrentMonth = (lastEntryDate ?? .distantPast) <= endOfCurrentMonthInclusive
            
            if crossesIntoFutureMonth && noEntriesBeyondCurrentMonth {
                // Ensure a valid interval even if the user scrolls entirely beyond the current month.
                // In that case, clamp start to the same day as the clamped end (label becomes the current month).
                let clampedStart = min(visibleWindow.start, endOfCurrentMonthInclusive)
                return DateInterval(start: clampedStart, end: endOfCurrentMonthInclusive)
            }
        }
        
        return visibleWindow
    }
    
    func getLabelDateRangeForYear(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> DateInterval {
        if let dateRange = getYearLabelDateRange(xScrollPosition: xScrollPosition) {
            return DateInterval(start: dateRange.start, end: dateRange.end)
        }
        
        let leftEdge = xScrollPosition
        let windowStart = calendar.dateInterval(of: .month, for: leftEdge)?.start ?? calendar.startOfDay(for: leftEdge)
        let endExclusive = calendar.date(byAdding: .month, value: 12, to: windowStart)
            ?? windowStart.addingTimeInterval(visibleDomainLength)
        return DateInterval(start: windowStart, end: inclusiveEnd(fromExclusive: endExclusive))
    }
    
    func getLabelDateRangeForWeek(xScrollPosition: Date) -> DateInterval {
        let leftEdge = xScrollPosition
        let windowStart = calendar.startOfDay(for: leftEdge)
        let windowEndExclusive = calendar.date(byAdding: .day, value: 7, to: windowStart)
            ?? windowStart.addingTimeInterval(DashboardConstants.TimeInterval.calendarWeek)
        let windowEndInclusive = windowEndExclusive.addingTimeInterval(-1)
        return DateInterval(start: windowStart, end: windowEndInclusive)
    }
    
    func getFullyContainedMonthInterval(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> DateInterval? {
        let leftEdge = xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(visibleDomainLength)
        
        let leftDay = calendar.startOfDay(for: leftEdge)
        let rightDay = calendar.startOfDay(for: rightEdge)
        
        // Helper to check if a month interval is fully contained
        func isFullyContained(_ monthInterval: DateInterval) -> Bool {
            let startDay = calendar.startOfDay(for: monthInterval.start)
            let endDay = calendar.startOfDay(for: monthInterval.end)
            return leftDay <= startDay && rightDay >= endDay
        }
        
        // Find the month containing leftEdge
        guard let leftMonthInterval = calendar.dateInterval(of: .month, for: leftEdge) else {
            return nil
        }
        
        // First check: Is the month containing leftEdge fully contained?
        // (This handles cases where leftEdge is at the start of a month, e.g., Nov 1 to Dec 1)
        if isFullyContained(leftMonthInterval) {
            return leftMonthInterval
        }
        
        // Second check: Is the next month fully contained?
        // (This handles cases where leftEdge is at end of previous month, e.g., Oct 31 to Dec 1)
        let nextMonthStart = leftMonthInterval.end
        if let nextMonthInterval = calendar.dateInterval(of: .month, for: nextMonthStart),
           isFullyContained(nextMonthInterval) {
            return nextMonthInterval
        }
        
        return nil
    }
    
    func inclusiveEnd(fromExclusive end: Date) -> Date {
        end.addingTimeInterval(-1)
    }
    
    // MARK: - Label Formatting
    
    func labelForTotalPeriod(
        dateBounds: (min: Date, max: Date)?,
        formatDateRange: (Date, Date, TimePeriod) -> String,
        fallbackLabel: () -> String
    ) -> String {
        // Use cached date bounds from data manager to avoid O(n) min/max scans
        if let bounds = dateBounds {
            return formatDateRange(bounds.min, bounds.max, .total)
        }
        return fallbackLabel()
    }
    
    func labelForYearGridlines(
        xScrollPosition: Date,
        formatDateRange: (Date, Date, TimePeriod) -> String,
        fallbackLabel: () -> String
    ) -> String {
        guard let dateRange = getYearLabelDateRange(xScrollPosition: xScrollPosition) else {
            return fallbackLabel()
        }
        return formatDateRange(dateRange.start, dateRange.end, .year)
    }
    
    func labelForMonthGridlines(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        continuousOperations: [BathScaleWeightSummary],
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String {
        let period: TimePeriod = .month
        let labelRange = getLabelDateRangeForMonth(
            xScrollPosition: xScrollPosition,
            visibleDomainLength: visibleDomainLength,
            continuousOperations: continuousOperations
        )
        return formatDateRange(labelRange.start, labelRange.end, period)
    }
    
    func labelForWeekGridlines(
        xScrollPosition: Date,
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String {
        let period: TimePeriod = .week
        let windowStart = calendar.startOfDay(for: xScrollPosition)
        let windowEndExclusive = calendar.date(byAdding: .day, value: 7, to: windowStart)
            ?? windowStart.addingTimeInterval(DashboardConstants.TimeInterval.calendarWeek)
        
        return formatDateRange(windowStart, windowEndExclusive, period)
    }
    
    func defaultRangeLabel(
        for period: TimePeriod,
        lastScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String {
        let minDate = lastScrollPosition
        let maxDate = lastScrollPosition.addingTimeInterval(visibleDomainLength)
        switch period {
        case .week:
            // Use shared range formatter (it applies inclusive end-day handling to match Android)
            return formatDateRange(minDate, maxDate, period)
        default:
            // For other periods, use existing methods
            return formatDateRange(minDate, maxDate, period)
        }
    }
    
    func formatWeekRangeLabel(from start: Date, to end: Date) -> String {
        let startYear = calendar.component(.year, from: start)
        let endYear = calendar.component(.year, from: end)
        let startMonth = calendar.component(.month, from: start)
        let endMonth = calendar.component(.month, from: end)
        let endDay = calendar.component(.day, from: end)
        
        // Match Android WEEK formatting logic
        // Cross-year: "MMM d, yyyy – MMM d, yyyy"
        if startYear != endYear {
            let fmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(fmt.string(from: start)) – \(fmt.string(from: end))"
        }
        
        // Cross-month: "MMM d – MMM d, yyyy"
        if startMonth != endMonth {
            let startFmt = DateTimeTools.formatter("MMM d")
            let endFmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(startFmt.string(from: start)) – \(endFmt.string(from: end))"
        }
        
        // Same month: "MMM d – d, yyyy"
        let startFmt = DateTimeTools.formatter("MMM d")
        return "\(startFmt.string(from: start)) – \(endDay), \(startYear)"
    }
    
    func emptyStatePeriodLabel(for period: TimePeriod, today: Date) -> String {
        switch period {
        case .week:
            // Find the most recent Sunday (start of week), then end at Saturday
            let startOfDay = calendar.startOfDay(for: today)
            let sundayStart = calendar.nextDate(
                after: startOfDay,
                matching: DateComponents(weekday: 1),
                matchingPolicy: .nextTime,
                direction: .backward
            ) ?? startOfDay
            guard let weekEnd = calendar.date(byAdding: .day, value: 6, to: sundayStart) else {
                return DateTimeTools.formatter("MMM d, yyyy").string(from: today)
            }
            let sameYear = calendar.isDate(sundayStart, equalTo: weekEnd, toGranularity: .year)
            if sameYear {
                let startString = DateTimeTools.formatter("MMM d").string(from: sundayStart)
                let endString = DateTimeTools.formatter("MMM d, yyyy").string(from: weekEnd)
                return "\(startString) - \(endString)"
            } else {
                let startString = DateTimeTools.formatter("MMM d, yyyy").string(from: sundayStart)
                let endString = DateTimeTools.formatter("MMM d, yyyy").string(from: weekEnd)
                return "\(startString) - \(endString)"
            }
        case .month:
            return DateTimeTools.formatter("MMM, yyyy").string(from: today)
        case .year:
            return DateTimeTools.formatter("yyyy").string(from: today)
        case .total:
            // Show current year for total in empty-state per spec
            return DateTimeTools.formatter("yyyy").string(from: today)
        }
    }
    
    // MARK: - Date Filtering Operations
    
    func filterOperationsInDateRange(
        operations: [BathScaleWeightSummary],
        start: Date,
        end: Date
    ) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }
        
        // Binary search for start index
        var lo = 0
        var hi = operations.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if operations[mid].date < start {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        let startIndex = lo
        
        // Binary search for end index
        lo = startIndex
        hi = operations.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if operations[mid].date <= end {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        let endIndex = lo
        
        guard startIndex < endIndex else { return [] }
        return Array(operations[startIndex..<endIndex])
    }
    
    func filterOperationsInDateRangeByDay(
        operations: [BathScaleWeightSummary],
        start: Date,
        end: Date
    ) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }
        
        let startDay = calendar.startOfDay(for: start)
        let endDay = calendar.startOfDay(for: end)
        
        func firstIndexAtOrAfterDay(_ day: Date) -> Int? {
            var lo = 0
            var hi = operations.count
            while lo < hi {
                let mid = (lo + hi) / 2
                let midDay = calendar.startOfDay(for: operations[mid].date)
                if midDay < day {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            return lo < operations.count ? lo : nil
        }
        
        func lastIndexAtOrBeforeDay(_ day: Date) -> Int? {
            var lo = 0
            var hi = operations.count
            while lo < hi {
                let mid = (lo + hi) / 2
                let midDay = calendar.startOfDay(for: operations[mid].date)
                if midDay <= day {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            let idx = lo - 1
            return idx >= 0 ? idx : nil
        }
        
        guard let startIndex = firstIndexAtOrAfterDay(startDay),
              let endIndex = lastIndexAtOrBeforeDay(endDay),
              startIndex <= endIndex else {
            return []
        }
        
        return Array(operations[startIndex...endIndex])
    }
    
    // swiftlint:disable:next function_parameter_count
    func getOperationsForLabelDateRange( // swiftlint:disable:this function_body_length
        period: TimePeriod,
        xScrollPosition: Date,
        visibleDomainLength: (TimePeriod) -> TimeInterval,
        continuousOperations: [BathScaleWeightSummary],
        dateBounds: (min: Date, max: Date)?,
        cachedPeriod: TimePeriod?,
        cachedScrollPos: Date?,
        cachedOps: [BathScaleWeightSummary]
    ) -> DateRangeOperationsResult {
        let currentPeriod = period
        let currentScrollPos = xScrollPosition
        
        // Return cache if valid (same period and scroll position within threshold)
        if cachedPeriod == currentPeriod,
           let cachedScrollPos = cachedScrollPos,
           !cachedOps.isEmpty {
            // Use cache if scroll position hasn't changed significantly (within 1 hour for year, 1 day for month)
            let threshold: TimeInterval = currentPeriod == .year ? 3600 : 86400
            if abs(currentScrollPos.timeIntervalSince(cachedScrollPos)) < threshold {
                return DateRangeOperationsResult(
                    operations: cachedOps,
                    cachedPeriod: currentPeriod,
                    cachedScrollPos: currentScrollPos,
                    cachedOps: cachedOps
                )
            }
        }
        
        var result: [BathScaleWeightSummary]
        
        switch currentPeriod {
        case .month:
            let labelRange = getLabelDateRangeForMonth(
                xScrollPosition: currentScrollPos,
                visibleDomainLength: visibleDomainLength(.month),
                continuousOperations: continuousOperations
            )
            result = filterOperationsInDateRange(
                operations: continuousOperations,
                start: labelRange.start,
                end: labelRange.end
            )
            
        case .year:
            let labelRange = getLabelDateRangeForYear(
                xScrollPosition: currentScrollPos,
                visibleDomainLength: visibleDomainLength(.year)
            )
            result = filterOperationsInDateRange(
                operations: continuousOperations,
                start: labelRange.start,
                end: labelRange.end
            )
            
        case .week:
            let labelRange = getLabelDateRangeForWeek(xScrollPosition: currentScrollPos)
            result = filterOperationsInDateRangeByDay(
                operations: continuousOperations,
                start: labelRange.start,
                end: labelRange.end
            )
            
        case .total:
            // Total view shows full timeline (e.g. feb 2022 - feb 2026); use ALL ops in that range.
            // visibleOperations uses a 1-year window and would undercount.
            if let bounds = dateBounds {
                result = filterOperationsInDateRange(
                    operations: continuousOperations,
                    start: bounds.min,
                    end: bounds.max
                )
            } else {
                result = continuousOperations
            }
        }
        
        // Return the result with updated cache values
        return DateRangeOperationsResult(
            operations: result,
            cachedPeriod: currentPeriod,
            cachedScrollPos: currentScrollPos,
            cachedOps: result
        )
    }
}
