import Foundation

extension BathScale {
    func toSnapshot() -> BathScaleSnapshot {
        BathScaleSnapshot(scaleType: scaleType, bodyComp: bodyComp)
    }
}
