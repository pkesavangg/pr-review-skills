import Foundation

/// Pure rendering configuration: X-axis tick generation, date formatting, and scroll math.
///
/// No state, no side effects. All functions take what they need and return a result.
/// Inject a `Calendar` for timezone-correct rendering and easy testability.
struct GraphRenderingConfiguration {

    let calendar: Calendar

    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }

    // MARK: - Domain Length

    func visibleDomainLength(for period: TimePeriod, at position: Date? = nil) -> TimeInterval {
        if period == .month,
           let position,
           let monthInterval = calendar.dateInterval(of: .month, for: position) {
            return monthInterval.duration
        }
        return DateTimeTools.visibleDomainLength(for: period)
    }

    // MARK: - X-Axis Tick Generation

    /// Generates all X-axis tick dates for the given period and scroll context.
    func xAxisValues(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date
    ) -> [Date] {
        guard let minDate = operations.first?.date,
              let maxDate = operations.last?.date else { return [] }

        let domainLength = visibleDomainLength(for: period, at: scrollPosition)
        let dataSpan = maxDate.timeIntervalSince(minDate)
        let useFixedDomain = period == .year
            ? dataSpan <= 5 * DashboardConstants.TimeInterval.year
            : dataSpan <= DashboardConstants.TimeInterval.year

        let (visibleStart, visibleEnd) = axisRange(
            period: period,
            minDate: minDate,
            maxDate: maxDate,
            scrollPosition: scrollPosition,
            domainLength: domainLength,
            useFixedDomain: useFixedDomain
        )

        let shouldRepeat = DateTimeTools.shouldRepeatXAxisLabels(for: period, entryCount: operations.count)

        switch period {
        case .week:  return weeklyTicks(from: visibleStart, to: visibleEnd)
        case .month: return monthlyTicks(from: visibleStart, to: visibleEnd)
        case .year:  return yearlyTicks(from: visibleStart, to: visibleEnd)
        case .total: return totalTicks(from: visibleStart, to: visibleEnd, operations: operations, shouldRepeat: shouldRepeat)
        }
    }

    // MARK: - Period-Specific Tick Generators

    func weeklyTicks(from start: Date, to end: Date) -> [Date] {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        cal.firstWeekday = 1 // Sunday

        let (startDate, endDate) = (min(start, end), max(start, end))
        let weekStart = cal.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
        let weekEndExclusive = cal.dateInterval(of: .weekOfYear, for: endDate)?.end ?? endDate

        var dates: [Date] = []
        var currentWeek = weekStart
        while currentWeek < weekEndExclusive {
            let dayStart = cal.startOfDay(for: currentWeek)
            for offset in 0...6 {
                if let day = cal.date(byAdding: .day, value: offset, to: dayStart),
                   let noon = cal.date(byAdding: .hour, value: 12, to: day),
                   noon >= weekStart && noon < weekEndExclusive {
                    dates.append(noon)
                }
            }
            guard let next = cal.date(byAdding: .weekOfYear, value: 1, to: currentWeek) else { break }
            currentWeek = next
        }
        // Trailing phantom tick ensures Saturday is not flush against the right edge
        if let last = dates.max(),
           let nextDay = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: last)),
           let phantom = cal.date(byAdding: .hour, value: 12, to: nextDay) {
            dates.append(phantom)
        }
        return dates
    }

    func monthlyTicks(from start: Date, to end: Date) -> [Date] {
        let (startDate, endDate) = (min(start, end), max(start, end))
        let monthStart = calendar.dateInterval(of: .month, for: startDate)?.start ?? startDate
        let monthEnd = calendar.dateInterval(of: .month, for: endDate)?.end ?? endDate
        let totalMonths = max(1, Int(ceil(monthEnd.timeIntervalSince(monthStart) / DashboardConstants.TimeInterval.month)))

        var dates: [Date] = []
        for offset in 0..<totalMonths {
            guard let monthBegin = calendar.date(byAdding: .month, value: offset, to: monthStart),
                  let monthInterval = calendar.dateInterval(of: .month, for: monthBegin) else { continue }

            // Generate ticks every 7 days starting from the 1st: 1, 8, 15, 22, 29
            let first = monthInterval.start
            let monthEndDate = monthInterval.end
            var day = first
            while day < monthEndDate {
                let noon = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: day) ?? day
                if noon >= monthStart && noon <= monthEnd {
                    dates.append(noon)
                }
                guard let next = calendar.date(byAdding: .day, value: 7, to: day) else { break }
                day = next
            }
        }
        // Trailing phantom tick at the last day of the last visible month
        if let last = dates.max() {
            let lastMonthEnd = calendar.dateInterval(of: .month, for: last)?.end ?? last
            let lastDayOfMonth = lastMonthEnd.addingTimeInterval(-1)
            let phantomNoon = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: lastDayOfMonth) ?? lastDayOfMonth
            if phantomNoon > last {
                dates.append(phantomNoon)
            }
        }
        return dates
    }

    func yearlyTicks(from start: Date, to end: Date) -> [Date] {
        let cal = yearlyCalendar
        let (startDate, endDate) = (min(start, end), max(start, end))
        let yearStart = cal.dateInterval(of: .year, for: startDate)?.start ?? startDate
        let yearEndExclusive = cal.dateInterval(of: .year, for: endDate)?.end ?? endDate
        let totalYears = max(1, Int(ceil(yearEndExclusive.timeIntervalSince(yearStart) / DashboardConstants.TimeInterval.year)))

        var dates: [Date] = []
        for yearOffset in 0..<totalYears {
            guard let currentYear = cal.date(byAdding: .year, value: yearOffset, to: yearStart) else { continue }
            let dayStart = cal.startOfDay(for: currentYear)
            for monthOffset in 0..<12 {
                if let monthStart = cal.date(byAdding: .month, value: monthOffset, to: dayStart),
                   let noon = cal.date(byAdding: .hour, value: 12, to: monthStart),
                   noon >= yearStart && noon < yearEndExclusive {
                    dates.append(noon)
                }
            }
        }
        // Trailing phantom month so December isn't flush against the right edge
        if let last = dates.max(),
           let nextMonth = cal.date(byAdding: .month, value: 1, to: cal.startOfDay(for: last)),
           let phantom = cal.date(byAdding: .hour, value: 12, to: nextMonth) {
            dates.append(phantom)
        }
        return dates
    }

    func totalTicks(
        from start: Date,
        to end: Date,
        operations: [BathScaleWeightSummary],
        shouldRepeat: Bool
    ) -> [Date] {
        areEntriesInSameEra(operations)
            ? yearlyTicks(from: start, to: end)
            : quarterlyTicks(from: start, to: end)
    }

    // MARK: - Scroll Position Calculation

    func optimalScrollPosition(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        anchorDate: Date? = nil,
        showingLatest: Bool = true,
        cachedBounds: (min: Date, max: Date)? = nil
    ) -> Date {
        let bounds: (min: Date, max: Date)
        if let cached = cachedBounds {
            bounds = cached
        } else {
            let dates = operations.map(\.date)
            guard let minDate = dates.min(), let maxDate = dates.max() else { return Date() }
            bounds = (min: minDate, max: maxDate)
        }

        if period == .total { return bounds.min }

        if period == .year,
           calendar.isDate(bounds.min, equalTo: bounds.max, toGranularity: .year) {
            var components = yearlyCalendar.dateComponents([.year], from: bounds.min)
            components.month = 1
            components.day = 1
            components.hour = 12
            components.minute = 0
            components.second = 0
            return yearlyCalendar.date(from: components) ?? bounds.min
        }

        let domainReferenceDate = anchorDate ?? bounds.max
        let domainLength = visibleDomainLength(for: period, at: domainReferenceDate)

        if let anchor = anchorDate {
            return anchoredScrollPosition(
                anchor: anchor,
                domainLength: domainLength,
                maxDate: bounds.max,
                period: period,
                minDate: bounds.min
            )
        }

        return showingLatest
            ? latestScrollPosition(for: period, latestDate: bounds.max, domainLength: domainLength)
            : bounds.min
    }

    func snapScrollPosition(_ position: Date, for period: TimePeriod) -> Date {
        switch period {
        case .week:
            var components = calendar.dateComponents([.year, .month, .day], from: position)
            components.hour = 12; components.minute = 0; components.second = 0
            return calendar.date(from: components) ?? position

        case .month:
            // Snap to the 1st of the containing calendar month so the entire month is visible.
            guard let monthInterval = calendar.dateInterval(of: .month, for: position) else { return position }
            var components = calendar.dateComponents([.year, .month], from: monthInterval.start)
            components.day = 1; components.hour = 0; components.minute = 0; components.second = 0
            return calendar.date(from: components) ?? position

        case .year:
            var components = yearlyCalendar.dateComponents([.year, .month], from: position)
            components.day = 1; components.hour = 12; components.minute = 0; components.second = 0
            return yearlyCalendar.date(from: components) ?? position

        case .total:
            return position
        }
    }

    func clampScrollPosition(_ position: Date, for period: TimePeriod, minDate: Date, maxDate: Date) -> Date {
        let padding = periodPadding(for: period)
        let minBound = minDate.addingTimeInterval(-padding)
        let maxBound = maxDate.addingTimeInterval(padding)
        guard minBound < maxBound else { return minDate }
        return max(minBound, min(maxBound, position))
    }

    // MARK: - Formatting

    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        DateTimeTools.formatXAxisLabel(for: date, period: period, operations: operations)
    }

    func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
        switch period {
        case .week, .month: return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
        case .year, .total: return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        let start = calendar.startOfDay(for: min(minDate, maxDate))
        let end = calendar.startOfDay(for: max(minDate, maxDate))

        switch period {
        case .total:
            if calendar.isDate(start, equalTo: end, toGranularity: .month) {
                return DateTimeTools.formatter("MMM yyyy").string(from: start)
            }
            let fmt = DateTimeTools.formatter("MMM yyyy")
            return "\(fmt.string(from: start)) – \(fmt.string(from: end))"

        case .month:
            let sy = calendar.component(.year, from: start), sm = calendar.component(.month, from: start)
            let ey = calendar.component(.year, from: end), em = calendar.component(.month, from: end)
            if sy == ey && sm == em { return DateTimeTools.formatter("MMM yyyy").string(from: start) }
            if sy != ey {
                let fmt = DateTimeTools.formatter("MMM d, yyyy")
                return "\(fmt.string(from: start)) – \(fmt.string(from: end))"
            }
            return "\(DateTimeTools.formatter("MMM d").string(from: start)) – \(DateTimeTools.formatter("MMM d, yyyy").string(from: end))"

        case .year:
            let sy = calendar.component(.year, from: start), ey = calendar.component(.year, from: end)
            if sy == ey { return DateTimeTools.formatter("yyyy").string(from: start) }
            return "\(DateTimeTools.formatter("MMM yyyy").string(from: start)) – \(DateTimeTools.formatter("MMM yyyy").string(from: end))"

        case .week:
            let inclusiveEnd = calendar.date(byAdding: .day, value: -1, to: end) ?? end
            let startDay = calendar.component(.day, from: start)
            let endDay = calendar.component(.day, from: inclusiveEnd)
            let startMonth = DateTimeTools.formatter("LLL").string(from: start).lowercased()
            let endMonth = DateTimeTools.formatter("LLL").string(from: inclusiveEnd).lowercased()
            let year = calendar.component(.year, from: inclusiveEnd)
            return "\(startMonth) \(startDay) - \(endMonth) \(endDay), \(year)"
        }
    }

    func fallbackTimeLabel(for period: TimePeriod) -> String {
        let now = Date()
        switch period {
        case .week:
            if let week = calendar.dateInterval(of: .weekOfYear, for: now) {
                let start = DateTimeTools.formatter("MMM d").string(from: week.start)
                let end = DateTimeTools.formatter("d").string(from: week.end.addingTimeInterval(-1))
                return "\(start)-\(end), \(calendar.component(.year, from: now))"
            }
            return DateTimeTools.formatter("MMM d, yyyy").string(from: now)
        case .month:
            return DateTimeTools.formatter("LLLL yyyy").string(from: now)
        case .year, .total:
            return DateTimeTools.formatter("yyyy").string(from: now)
        }
    }

    // MARK: - Sample Date Generation (for interpolated averaging)

    func sampleDates(for period: TimePeriod, scrollPosition: Date) -> [Date] {
        guard period != .total else { return [] }
        let domainLength = visibleDomainLength(for: period, at: scrollPosition)
        let rightEdge = scrollPosition.addingTimeInterval(domainLength)
        let unit: Calendar.Component = period == .week ? .day : period == .month ? .weekOfYear : .month

        var dates: [Date] = []
        var current = scrollPosition
        while current <= rightEdge {
            dates.append(current)
            guard let next = calendar.date(byAdding: unit, value: 1, to: current) else { break }
            current = next
        }
        return dates
    }

    // MARK: - Private

    /// Gregorian calendar for yearly tick generation — keeps ticks and snap targets aligned.
    private var yearlyCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        return cal
    }

    private func areEntriesInSameEra(_ ops: [BathScaleWeightSummary]) -> Bool {
        let validYears = ops.compactMap { op -> Int? in
            let y = calendar.component(.year, from: op.date)
            return (1900...2100).contains(y) ? y : nil
        }
        return Set(validYears).count <= 1
    }

    private func periodPadding(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:  return DashboardConstants.TimeInterval.week
        case .month: return DashboardConstants.TimeInterval.month
        case .year:  return DashboardConstants.TimeInterval.year
        case .total: return 0
        }
    }

    // swiftlint:disable:next function_parameter_count cyclomatic_complexity
    private func axisRange(
        period: TimePeriod,
        minDate: Date,
        maxDate: Date,
        scrollPosition: Date,
        domainLength: TimeInterval,
        useFixedDomain: Bool
    ) -> (Date, Date) {
        var minBuffer: TimeInterval = 0
        var maxBuffer: TimeInterval = 0
        switch period {
        case .week:
            if calendar.component(.weekday, from: minDate) == calendar.firstWeekday {
                minBuffer = DashboardConstants.TimeInterval.week
            }
            let maxWeekday = calendar.component(.weekday, from: maxDate)
            let maxDaysFromStart = (maxWeekday - calendar.firstWeekday + 7) % 7
            maxBuffer = TimeInterval(6 - maxDaysFromStart) * DashboardConstants.TimeInterval.day
        case .month:
            if calendar.component(.day, from: minDate) == 1 {
                minBuffer = DashboardConstants.TimeInterval.month
            }
            if let range = calendar.range(of: .day, in: .month, for: maxDate) {
                let maxDay = calendar.component(.day, from: maxDate)
                maxBuffer = TimeInterval(range.count - maxDay) * DashboardConstants.TimeInterval.day
            }
        case .year:
            if calendar.component(.month, from: minDate) == 1 {
                minBuffer = DashboardConstants.TimeInterval.year
            }
            let maxMonth = calendar.component(.month, from: maxDate)
            maxBuffer = TimeInterval(12 - maxMonth) * DashboardConstants.TimeInterval.month
        case .total: break
        }

        let adjMin = minDate.addingTimeInterval(-minBuffer)
        let adjMax = maxDate.addingTimeInterval(maxBuffer)

        if useFixedDomain {
            if period == .year {
                let cal = yearlyCalendar
                let start = cal.dateInterval(of: .year, for: minDate)?.start ?? adjMin
                let end = cal.dateInterval(of: .year, for: maxDate)?.end.addingTimeInterval(-1) ?? adjMax
                return (start, end)
            }
            if period == .month {
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? adjMin
                let monthEnd = calendar.dateInterval(of: .month, for: maxDate)?.end.addingTimeInterval(-1)
                    ?? currentPeriodEnd(for: period)
                return (monthStart, monthEnd)
            }
            return (adjMin, max(adjMax, currentPeriodEnd(for: period)))
        } else {
            let buffer = domainLength * 2.0
            let scrollEnd = scrollPosition.addingTimeInterval(domainLength / 2 + buffer)
            let visibleStart = max(adjMin, scrollPosition.addingTimeInterval(-domainLength / 2 - buffer))
            let now = Date()
            let visibleEnd: Date
            if adjMax > now {
                visibleEnd = min(adjMax, scrollEnd)
            } else if adjMax < now.addingTimeInterval(-domainLength * 3) {
                visibleEnd = max(now, scrollEnd)
            } else {
                visibleEnd = min(now, scrollEnd)
            }
            return (visibleStart, visibleEnd)
        }
    }

    private func currentPeriodEnd(for period: TimePeriod) -> Date {
        let now = Date()
        switch period {
        case .week:
            let weekday = calendar.component(.weekday, from: now)
            let days = (7 - weekday + 7) % 7
            if let sat = calendar.date(byAdding: .day, value: days, to: calendar.startOfDay(for: now)),
               let noon = calendar.date(byAdding: .hour, value: 12, to: sat) { return noon }
        case .month:
            if let interval = calendar.dateInterval(of: .month, for: now) { return interval.end.addingTimeInterval(-1) }
        case .year, .total:
            if let interval = calendar.dateInterval(of: .year, for: now) { return interval.end.addingTimeInterval(-1) }
        }
        return now
    }

    private func quarterlyTicks(from start: Date, to end: Date) -> [Date] {
        let (startDate, endDate) = (min(start, end), max(start, end))
        guard let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: startDate)) else { return [] }
        let endMonth = calendar.component(.month, from: endDate)
        let endYear = calendar.component(.year, from: endDate)
        let quarterEndMonth = ((endMonth - 1) / 3 + 1) * 3
        let quarterEnd = calendar.date(from: DateComponents(year: endYear, month: quarterEndMonth + 1, day: 1))?.addingTimeInterval(-1) ?? endDate
        let totalQuarters = max(1, Int(ceil(quarterEnd.timeIntervalSince(quarterStart) / DashboardConstants.TimeInterval.quarter)))

        return (0..<totalQuarters).compactMap { offset in
            guard let date = calendar.date(byAdding: .month, value: offset * 3, to: quarterStart) else { return nil }
            return (date >= quarterStart && date <= quarterEnd) ? date : nil
        }
    }

    private func anchoredScrollPosition(
        anchor: Date,
        domainLength: TimeInterval,
        maxDate: Date,
        period: TimePeriod,
        minDate: Date
    ) -> Date {
        var pos = anchor.addingTimeInterval(-domainLength / 2)
        let maxDataEnd = maxDate.addingTimeInterval(periodPadding(for: period))
        let viewportEnd = pos.addingTimeInterval(domainLength)
        if viewportEnd > maxDataEnd {
            pos = pos.addingTimeInterval(maxDataEnd.timeIntervalSince(viewportEnd))
        }
        let minBound = minDate.addingTimeInterval(-domainLength * 0.1)
        return max(minBound, pos)
    }

    private func latestScrollPosition(for period: TimePeriod, latestDate: Date, domainLength: TimeInterval) -> Date {
        switch period {
        case .week:
            let rightEdgeWithBuffer = latestDate.addingTimeInterval(2 * DashboardConstants.TimeInterval.day)
            return rightEdgeWithBuffer.addingTimeInterval(-domainLength)
        case .month:
            // Snap to the 1st of the month containing the latest entry
            return calendar.dateInterval(of: .month, for: latestDate)?.start ?? latestDate
        case .year:
            let rightEdgeWithBuffer = latestDate.addingTimeInterval(DashboardConstants.TimeInterval.month)
            return rightEdgeWithBuffer.addingTimeInterval(-domainLength)
        default:
            return latestDate.addingTimeInterval(periodPadding(for: period) - domainLength)
        }
    }
}
