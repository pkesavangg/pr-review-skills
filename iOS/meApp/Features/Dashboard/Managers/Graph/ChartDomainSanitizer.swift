import Foundation

/// Pure guards that keep degenerate / non-finite domains from ever reaching Swift Charts' scale
/// modifiers (`.chartYScale`, `.chartXScale`, `.chartXVisibleDomain`).
///
/// A zero-width or non-finite domain makes Charts divide by zero while placing marks, which floods
/// the console with `Invalid frame dimension (negative or non-finite)` — one line per mark, every
/// layout pass (MOB-518 / W2). Sanitizing at the call site is a structural invariant of feeding
/// Charts safely: it holds regardless of the data shape produced upstream (single-value windows,
/// empty accounts, mid-transition frames, etc.), so it can never regress into the flood again.
///
/// No state, no side effects — trivially unit-testable.
enum ChartDomainSanitizer {

    /// Returns `range` unchanged when it is finite with width ≥ `minWidth`; otherwise a finite,
    /// positive-width range centered on the original midpoint (falling back to `fallback` when either
    /// bound is non-finite).
    static func finiteWidth(
        _ range: ClosedRange<Double>,
        minWidth: Double = 1,
        fallback: ClosedRange<Double> = 0...100
    ) -> ClosedRange<Double> {
        let lower = range.lowerBound
        let upper = range.upperBound
        guard lower.isFinite, upper.isFinite else { return fallback }
        guard upper - lower >= minWidth else {
            let mid = (lower + upper) / 2
            let half = minWidth / 2
            return (mid - half)...(mid + half)
        }
        return range
    }

    /// Returns `range` unchanged when its two dates are ≥ `minWidth` seconds apart; otherwise widens
    /// the upper bound so the date domain always has strictly-positive width.
    static func orderedDates(
        _ range: ClosedRange<Date>,
        minWidth: TimeInterval = 1
    ) -> ClosedRange<Date> {
        guard range.upperBound.timeIntervalSince(range.lowerBound) >= minWidth else {
            return range.lowerBound...range.lowerBound.addingTimeInterval(minWidth)
        }
        return range
    }

    /// Clamps a visible-domain length to a finite, strictly-positive value.
    static func positiveLength(_ length: TimeInterval, fallback: TimeInterval = 1) -> TimeInterval {
        guard length.isFinite, length > 0 else { return fallback }
        return length
    }
}
