import Foundation

/// Pure rendering configuration: X-axis tick generation, date formatting, and scroll math.
///
/// No state, no side effects. All functions take what they need and return a result.
/// Inject a `Calendar` for timezone-correct rendering and easy testability.
struct GraphRenderingConfiguration {

    let calendar: Calendar
    let now: () -> Date

    init(calendar: Calendar = .current, now: @escaping () -> Date = Date.init) {
        self.calendar = calendar
        self.now = now
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

        switch period {
        case .week:  return weeklyTicks(from: visibleStart, to: visibleEnd)
        case .month: return monthlyTicks(from: visibleStart, to: visibleEnd)
        case .year:  return yearlyTicks(from: visibleStart, to: visibleEnd)
        case .total: return totalTicks(from: visibleStart, to: visibleEnd, operations: operations)
        }
    }

    // MARK: - Full-Domain Geometry (MOB-518 v2 engine)

    /// Full scrollable x-domain for the v2 weight engine — the buffered data span using the *fixed-domain*
    /// semantics of `axisRange`, so it is INDEPENDENT of the current scroll position. The v2 engine scrolls
    /// natively within this once-computed domain (no live re-windowing), which keeps the scroll region
    /// stable across a y-settle. Returns `nil` for an empty dataset. `operations` must be sorted ascending.
    func fullXDomain(for period: TimePeriod, from operations: [BathScaleWeightSummary]) -> ClosedRange<Date>? {
        guard let minDate = operations.first?.date, let maxDate = operations.last?.date else {
            return emptyStateDomain(for: period)
        }
        // Two periods need a clean window so points read inset instead of pinned to an edge:
        //   • WEEK (any number of readings) → the data bracketed to whole Sun→Sat calendar weeks: the domain
        //     opens on the Sunday of the FIRST reading's week and closes on the Saturday of the LATER of the
        //     last reading's week and the CURRENT week — so the graph stays scrollable forward from the data up
        //     to today (a lone June reading is still scrollable to the current July week). A first entry
        //     mid-week (e.g. Wednesday) then shows the WHOLE week with its point on the correct weekday — the
        //     empty lead-in days (Sun–Tue) ARE the leading space — instead of the domain starting at the entry
        //     and pinning it to the left edge. Month already extends to the current month; year does now too.
        //   • TOTAL (any number of readings) → the data range padded by 6 months on BOTH ends, so the first
        //     and last readings sit inset with equal breathing room. Previously the first reading was flush
        //     against the left edge while the trailing year-end pad left a gap only on the right; a single
        //     reading now centres in a 12-month window. TOTAL isn't scrollable and draws no x-axis ticks, so
        //     this padding is purely for legibility. (MOB-1516)
        if period == .week,
           let firstWeek = gregorian.dateInterval(of: .weekOfYear, for: minDate),
           let lastWeek = gregorian.dateInterval(of: .weekOfYear, for: maxDate) {
            // Upper bound = the LATER of the last reading's week and the current week, so it's scrollable
            // forward to today. A single reading in the current week → both coincide → its own week.
            //
            // MOB-1726 (issue 3): this is a CLEAN next-Sunday midnight (an exclusive week boundary) — NOT
            // shaved by 1s. The domain width must be an exact whole number of 7-day windows so the LAST
            // Sunday-aligned window fits: with the old `−1s` the domain was 1s short of the final window's
            // right edge, so the value-aligned scroll (Sunday `majorAlignment`) could never rest on the last
            // week and snapped back to the first (e.g. a 2-week baby span Jul 12–26 could not reach Jul 19–25).
            // The weekly tick generator no longer spills a phantom following week off this boundary end — see
            // the exclusive-end handling in `weeklyTicks`.
            return firstWeek.start...max(lastWeek.end, currentPeriodEnd(for: .week))
        }
        if period == .total {
            let lower = gregorian.date(byAdding: .month, value: -6, to: minDate) ?? minDate
            let upper = gregorian.date(byAdding: .month, value: 6, to: maxDate) ?? maxDate
            return lower...upper
        }
        let (start, end) = axisRange(
            period: period,
            minDate: minDate,
            maxDate: maxDate,
            scrollPosition: maxDate,
            domainLength: visibleDomainLength(for: period, at: maxDate),
            useFixedDomain: true
        )
        return start <= end ? start...end : end...start
    }

    /// Bounded scrollable x-domain for the v2 engine: a window of `±windows` visible-windows around
    /// `scrollPosition`, clamped to the buffered full data span. This caps the Swift Charts scroll canvas
    /// (fullDomain ÷ visibleWindow) to ~`2·windows`× regardless of how much history exists — the fix for the
    /// week/month scroll hang, where the full-dataset domain made the canvas 100×+ wide. `.total` isn't
    /// scrollable → the whole span. Returns `nil` for an empty dataset. `operations` must be sorted ascending.
    func boundedXDomain(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        around scrollPosition: Date,
        windows: Double
    ) -> ClosedRange<Date>? {
        guard let full = fullXDomain(for: period, from: operations) else { return nil }
        guard period != .total else { return full }
        let half = visibleDomainLength(for: period) * windows
        let low = max(full.lowerBound, scrollPosition.addingTimeInterval(-half))
        let high = min(full.upperBound, scrollPosition.addingTimeInterval(half))
        return low < high ? low...high : full
    }

    /// X-axis ticks generated FOR the bounded window (`boundedXDomain`), not the full span — so only a
    /// handful of `AxisMarks` render and gridlines/labels appear correctly across every period. `operations`
    /// must be sorted ascending.
    func boundedXAxisValues(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        around scrollPosition: Date,
        windows: Double
    ) -> [Date] {
        // TOTAL draws no x-axis ticks in any situation — no labels AND no gridlines (a single reading is a
        // bare centred point; multi-reading totals drop the month/year markers too). The renderer reserves
        // the label-row height so the plot keeps the same height as the other periods. (MOB-1516)
        guard let domain = boundedXDomain(for: period, from: operations, around: scrollPosition, windows: windows) else { return [] }
        switch period {
        case .week:  return weeklyTicks(from: domain.lowerBound, to: domain.upperBound)
        case .month: return monthlyWeeklyTicks(from: domain.lowerBound, to: domain.upperBound)
        case .year:  return yearlyTicks(from: domain.lowerBound, to: domain.upperBound)
        case .total: return []
        }
    }

    // MARK: - Period-Specific Tick Generators

    func weeklyTicks(from start: Date, to end: Date) -> [Date] {
        let cal = gregorian

        let (startDate, endDate) = (min(start, end), max(start, end))
        let weekStart = cal.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
        // MOB-1726 (issue 3): treat an end that lands exactly on a week boundary (next-Sunday midnight) as
        // EXCLUSIVE. The week scroll domain now ends on a clean Sunday boundary (no `−1s`), so looking up the
        // week of `endDate` itself would return the FOLLOWING week and spill a phantom week of ticks. Nudging
        // 1s back keeps a boundary end inside the last real week; a non-boundary end is unaffected (same week).
        let weekEndExclusive = cal.dateInterval(of: .weekOfYear, for: endDate.addingTimeInterval(-1))?.end ?? endDate

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

    /// MOB-518 v2 weight engine — Apple-Health-style MONTH ticks: a **continuous Sunday-anchored 7-day grid**
    /// across the whole domain that never resets at the 1st, so labels read every 7 days (… may 17, 24, 31,
    /// jun 7 …) exactly like Health. Unlike `monthlyTicks` (which restarts the grid at the 1st of every month
    /// → `1, 8, 15, 22, 29`, bunching `29`/`1` at the boundary — 2–3 days apart, not 7). The **solid month
    /// boundary rule** is NOT baked into these ticks: injecting a `1st` tick 1 day from the adjacent Sunday
    /// made Swift Charts hide that Sunday's label (the boundary tick "ate" it), leaving a visible gap. The
    /// view draws the boundary as a separate gridline-only mark (`WeightChartView.monthBoundaryTicks`) that
    /// carries no tick/label, so every Sunday label renders. Scoped to the v2 weight paths
    /// (`boundedXAxisValues`); the legacy `monthlyTicks` (used by `xAxisValues`, shared with
    /// baby/BPM) is intentionally left unchanged.
    func monthlyWeeklyTicks(from start: Date, to end: Date) -> [Date] {
        let cal = gregorian

        let (startDate, endDate) = (min(start, end), max(start, end))

        // Continuous weekly grid: anchor to the Sunday of the start's week, then step +7 days across month
        // boundaries (never resetting to the 1st). Ticks at local noon, like the other generators.
        var dates: [Date] = []
        if let weekStart = cal.dateInterval(of: .weekOfYear, for: startDate)?.start {
            var day = cal.startOfDay(for: weekStart)
            while day <= endDate {
                if let noon = cal.date(byAdding: .hour, value: 12, to: day), noon >= startDate, noon <= endDate {
                    dates.append(noon)
                }
                guard let next = cal.date(byAdding: .day, value: 7, to: day) else { break }
                day = next
            }
        }

        // Trailing phantom (+7 days past the last tick) so the view's `dropLast()` removes a throwaway rather
        // than a real tick, keeping the last real label/gridline off the right edge (parity with the others).
        if let last = dates.last,
           let phantom = cal.date(byAdding: .day, value: 7, to: cal.startOfDay(for: last)),
           let phantomNoon = cal.date(byAdding: .hour, value: 12, to: phantom) {
            dates.append(phantomNoon)
        }
        return dates
    }

    func yearlyTicks(from start: Date, to end: Date) -> [Date] {
        let cal = gregorian
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
        operations: [BathScaleWeightSummary]
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
            var components = gregorian.dateComponents([.year], from: bounds.min)
            components.month = 1
            components.day = 1
            components.hour = 12
            components.minute = 0
            components.second = 0
            return gregorian.date(from: components) ?? bounds.min
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
            var components = gregorian.dateComponents([.year, .month], from: position)
            components.day = 1; components.hour = 12; components.minute = 0; components.second = 0
            return gregorian.date(from: components) ?? position

        case .total:
            return position
        }
    }

    // NOTE (MOB-518, v2 weight engine): there is deliberately NO weight-specific scroll snap. Native
    // `ValueAlignedChartScrollTargetBehavior` (see `WeightChartView.scrollBehavior`) rests the window on the
    // fine grid — a fling on the period boundary, a slow drag on any day / month — and `commitWeightScroll`
    // records that landed position verbatim. An earlier `snapWeightScrollPosition` re-rounded it here and
    // caused a one-unit drift on release / on leave-and-return, so it was removed. Legacy baby/BPM still use
    // `snapScrollPosition` above.

    func clampScrollPosition(_ position: Date, for period: TimePeriod, minDate: Date, maxDate: Date) -> Date {
        let padding = periodPadding(for: period)
        let minBound = minDate.addingTimeInterval(-padding)
        let maxBound = maxDate.addingTimeInterval(padding)
        guard minBound < maxBound else { return minDate }
        return max(minBound, min(maxBound, position))
    }

    // MARK: - Formatting

    /// Fixed-locale (en_US_POSIX) formatter that renders in the *injected* calendar's timezone.
    /// In production the calendar is `.current`, so this matches the device timezone exactly as
    /// before; in tests a GMT calendar is injected, making formatted output deterministic instead
    /// of dependent on the CI machine's timezone. Mirrors `DateTimeTools.formatter` but honours the
    /// injected calendar — the whole reason this type takes a `Calendar` ("timezone-correct
    /// rendering and easy testability").
    private func formatter(_ format: String) -> DateFormatter {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = calendar.timeZone
        df.dateFormat = format
        return df
    }

    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        DateTimeTools.formatXAxisLabel(for: date, period: period, operations: operations)
    }

    func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
        switch period {
        case .week, .month: return formatter("MMM d, yyyy").string(from: date)
        case .year, .total: return formatter("MMM yyyy").string(from: date)
        }
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        let start = calendar.startOfDay(for: min(minDate, maxDate))
        let end = calendar.startOfDay(for: max(minDate, maxDate))

        switch period {
        case .total:
            if calendar.isDate(start, equalTo: end, toGranularity: .month) {
                return formatter("MMM yyyy").string(from: start)
            }
            let fmt = formatter("MMM yyyy")
            return "\(fmt.string(from: start)) – \(fmt.string(from: end))"

        case .month:
            let sy = calendar.component(.year, from: start), sm = calendar.component(.month, from: start)
            let ey = calendar.component(.year, from: end), em = calendar.component(.month, from: end)
            if sy == ey && sm == em { return formatter("MMM yyyy").string(from: start) }
            if sy != ey {
                let fmt = formatter("MMM d, yyyy")
                return "\(fmt.string(from: start)) – \(fmt.string(from: end))"
            }
            return "\(formatter("MMM d").string(from: start)) – \(formatter("MMM d, yyyy").string(from: end))"

        case .year:
            let sy = calendar.component(.year, from: start), ey = calendar.component(.year, from: end)
            if sy == ey { return formatter("yyyy").string(from: start) }
            return "\(formatter("MMM yyyy").string(from: start)) – \(formatter("MMM yyyy").string(from: end))"

        case .week:
            let inclusiveEnd = calendar.date(byAdding: .day, value: -1, to: end) ?? end
            let startDay = calendar.component(.day, from: start)
            let endDay = calendar.component(.day, from: inclusiveEnd)
            let startMonth = formatter("LLL").string(from: start).lowercased()
            let endMonth = formatter("LLL").string(from: inclusiveEnd).lowercased()
            let year = calendar.component(.year, from: inclusiveEnd)
            return "\(startMonth) \(startDay) - \(endMonth) \(endDay), \(year)"
        }
    }

    func fallbackTimeLabel(for period: TimePeriod) -> String {
        let now = now()
        switch period {
        case .week:
            if let week = calendar.dateInterval(of: .weekOfYear, for: now) {
                let start = formatter("MMM d").string(from: week.start)
                let end = formatter("d").string(from: week.end.addingTimeInterval(-1))
                return "\(start)-\(end), \(calendar.component(.year, from: now))"
            }
            return formatter("MMM d, yyyy").string(from: now)
        case .month:
            return formatter("LLLL yyyy").string(from: now)
        case .year, .total:
            return formatter("yyyy").string(from: now)
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

    /// Sunday-first Gregorian calendar in the injected timezone/locale. Used by the yearly tick + scroll math
    /// (where it keeps ticks and snap targets aligned) AND the weekly tick generators + the single-reading
    /// week domain, so week bracketing is deterministic regardless of the injected calendar's `firstWeekday`.
    private var gregorian: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        cal.firstWeekday = 1 // Sunday
        return cal
    }

    /// MOB-1516 — x-domain for the EMPTY state (no readings): the CURRENT calendar period around `now`, so an
    /// empty week/month/year still renders its own vertical gridlines + x-axis labels (sun–sat, the weekly
    /// 5/12/19/26 grid, the twelve months) instead of a blank box. Previously an empty dataset returned nil
    /// here → no domain and no ticks were produced. TOTAL gets the current calendar year for a sensible header
    /// extent, but still draws NO ticks (`boundedXAxisValues` returns none for total) — it stays a bare box, as
    /// before. `-1s` so a period's exclusive interval end doesn't spill a phantom unit into the next period.
    private func emptyStateDomain(for period: TimePeriod) -> ClosedRange<Date>? {
        let unit: Calendar.Component = period == .week ? .weekOfYear : (period == .month ? .month : .year)
        return gregorian.dateInterval(of: unit, for: now()).map { $0.start...$0.end.addingTimeInterval(-1) }
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

    // swiftlint:disable:next function_parameter_count cyclomatic_complexity function_body_length
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
                let cal = gregorian
                let start = cal.dateInterval(of: .year, for: minDate)?.start ?? adjMin
                let end = cal.dateInterval(of: .year, for: maxDate)?.end.addingTimeInterval(-1) ?? adjMax
                // Extend forward to the current year (parity with month) so it's scrollable up to today.
                return (start, max(end, currentPeriodEnd(for: .year)))
            }
            if period == .month {
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? adjMin
                let monthEnd = calendar.dateInterval(of: .month, for: maxDate)?.end.addingTimeInterval(-1)
                    ?? currentPeriodEnd(for: period)
                return (monthStart, max(monthEnd, currentPeriodEnd(for: period)))
            }
            return (adjMin, max(adjMax, currentPeriodEnd(for: period)))
        } else {
            let buffer = domainLength * 2.0
            let scrollEnd = scrollPosition.addingTimeInterval(domainLength / 2 + buffer)
            let visibleStart = max(adjMin, scrollPosition.addingTimeInterval(-domainLength / 2 - buffer))
            let now = now()
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
        let now = now()
        switch period {
        case .week:
            // End of the current week (next Sunday, exclusive) so the latest 7-day window rests as a FULL
            // Sun→Sat week. The old Saturday-noon cut the window ~½ day short, squishing the last column
            // against the trailing rule (visible even with multiple entries). (MOB-1516)
            if let interval = gregorian.dateInterval(of: .weekOfYear, for: now) { return interval.end }
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
