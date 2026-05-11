import Foundation

/// Value-type snapshot of BathScale. Sendable, safe across async boundaries.
struct BathScaleSnapshot: Equatable, Sendable {
    let scaleType: String?
    let bodyComp: Bool?
}
