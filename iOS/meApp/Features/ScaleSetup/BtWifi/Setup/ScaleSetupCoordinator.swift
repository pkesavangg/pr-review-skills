import Foundation

protocol ScaleSetupCoordinating {
    func adjustedIndex(
        from index: Int,
        direction: Int,
        steps: [BtWifiScaleSetupStep],
        canSkipPermissions: Bool
    ) -> Int

    func index(for step: BtWifiScaleSetupStep, in steps: [BtWifiScaleSetupStep]) -> Int?
}

struct ScaleSetupCoordinator: ScaleSetupCoordinating {
    func adjustedIndex(
        from index: Int,
        direction: Int,
        steps: [BtWifiScaleSetupStep],
        canSkipPermissions: Bool
    ) -> Int {
        var nextIndex = index
        while nextIndex >= 0 &&
                nextIndex < steps.count &&
                steps[nextIndex] == .permissions &&
                canSkipPermissions {
            nextIndex += direction
        }
        return nextIndex
    }

    func index(for step: BtWifiScaleSetupStep, in steps: [BtWifiScaleSetupStep]) -> Int? {
        steps.firstIndex(of: step)
    }
}
