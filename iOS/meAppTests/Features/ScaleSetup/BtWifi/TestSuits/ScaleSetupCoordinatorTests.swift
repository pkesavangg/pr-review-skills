//
//  ScaleSetupCoordinatorTests.swift
//  meAppTests
//
//  Covers the step-skipping logic in ScaleSetupCoordinator, including the
//  Complete Profile skip added for MOB-1388.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct ScaleSetupCoordinatorTests {
    private let coordinator = ScaleSetupCoordinator()
    private let steps = BtWifiScaleSetupStep.allCases

    private func index(_ step: BtWifiScaleSetupStep) -> Int {
        steps.firstIndex(of: step) ?? -1
    }

    // MARK: - Complete Profile skipping (MOB-1388)

    @Test("Forward: skips completeProfile when profile complete")
    func forward_skipsCompleteProfile_whenComplete() {
        // Starting at completeProfile, moving forward, profile complete -> land on wakeup.
        let result = coordinator.adjustedIndex(
            from: index(.completeProfile),
            direction: 1,
            steps: steps,
            canSkipPermissions: true,
            canSkipCompleteProfile: true
        )
        #expect(steps[result] == .wakeup)
    }

    @Test("Forward: stays on completeProfile when profile incomplete")
    func forward_staysOnCompleteProfile_whenIncomplete() {
        let result = coordinator.adjustedIndex(
            from: index(.completeProfile),
            direction: 1,
            steps: steps,
            canSkipPermissions: true,
            canSkipCompleteProfile: false
        )
        #expect(steps[result] == .completeProfile)
    }

    @Test("Forward: skips both permissions and completeProfile consecutively")
    func forward_skipsPermissionsAndCompleteProfile() {
        // Starting at permissions, both skippable -> land on wakeup.
        let result = coordinator.adjustedIndex(
            from: index(.permissions),
            direction: 1,
            steps: steps,
            canSkipPermissions: true,
            canSkipCompleteProfile: true
        )
        #expect(steps[result] == .wakeup)
    }

    @Test("Backward: skips completeProfile back to permissions-or-intro when complete")
    func backward_skipsCompleteProfile_whenComplete() {
        // From wakeup going back: skip completeProfile, then skip permissions -> intro.
        let result = coordinator.adjustedIndex(
            from: index(.completeProfile),
            direction: -1,
            steps: steps,
            canSkipPermissions: true,
            canSkipCompleteProfile: true
        )
        #expect(steps[result] == .intro)
    }

    @Test("Non-skippable step is returned unchanged")
    func nonSkippableStep_returnedUnchanged() {
        let result = coordinator.adjustedIndex(
            from: index(.wakeup),
            direction: 1,
            steps: steps,
            canSkipPermissions: true,
            canSkipCompleteProfile: true
        )
        #expect(steps[result] == .wakeup)
    }
}
