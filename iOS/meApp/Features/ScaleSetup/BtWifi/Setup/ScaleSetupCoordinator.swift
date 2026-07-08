import Foundation

protocol ScaleSetupCoordinating {
    func adjustedIndex(
        from index: Int,
        direction: Int,
        steps: [BtWifiScaleSetupStep],
        canSkipPermissions: Bool,
        canSkipCompleteProfile: Bool
    ) -> Int

    func index(for step: BtWifiScaleSetupStep, in steps: [BtWifiScaleSetupStep]) -> Int?
}

struct ScaleSetupCoordinator: ScaleSetupCoordinating {
    func adjustedIndex(
        from index: Int,
        direction: Int,
        steps: [BtWifiScaleSetupStep],
        canSkipPermissions: Bool,
        canSkipCompleteProfile: Bool
    ) -> Int {
        var nextIndex = index
        while nextIndex >= 0 && nextIndex < steps.count {
            let step = steps[nextIndex]
            let skipPermissions = step == .permissions && canSkipPermissions
            let skipCompleteProfile = step == .completeProfile && canSkipCompleteProfile
            guard skipPermissions || skipCompleteProfile else { break }
            nextIndex += direction
        }
        return nextIndex
    }

    func index(for step: BtWifiScaleSetupStep, in steps: [BtWifiScaleSetupStep]) -> Int? {
        steps.firstIndex(of: step)
    }
}
