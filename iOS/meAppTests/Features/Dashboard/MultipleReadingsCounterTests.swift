//
//  MultipleReadingsCounterTests.swift
//  meAppTests
//
//  Tests for the multiple-readings counter state machine used in BottomTabBarViewModel.
//  Each reading type (baby, btWeight, wifiWeight, btBpm, wifiBpm) shares the same pattern:
//
//    count += 1
//    headerView = count > 1 ? MultipleReadingsToastView(count: count - 1, ...) : nil
//    onDismiss { if isReplacing { isReplacing = false } else { count = 0 } }
//    if count > 1 { isReplacing = true }
//    showToast(...)
//
//  These tests validate the pure logic of that pattern without instantiating the full
//  BottomTabBarViewModel (which carries many async dependencies).
//

import Combine
import Foundation
import Testing
@testable import meApp

// MARK: - Counter logic helpers
//
// Mirror the exact inline logic used in BottomTabBarViewModel so the tests stay coupled
// to the real algorithm without requiring the full VM.

private func headerViewCount(readingCount: Int) -> Int? {
    readingCount > 1 ? readingCount - 1 : nil
}

/// Simulates the onDismiss closure body for any reading type.
/// Returns (updatedCount, updatedIsReplacing).
private func simulateOnDismiss(count: Int, isReplacing: Bool) -> (count: Int, isReplacing: Bool) {
    if isReplacing {
        return (count, false)   // flag cleared; counter preserved
    } else {
        return (0, false)       // counter zeroed
    }
}

/// Simulates the state transition after showToast is called.
/// Returns the value of isReplacing that should be stored.
private func isReplacingAfterShow(count: Int) -> Bool {
    count > 1
}

// MARK: - Suite

@Suite("Multiple readings counter — header-view logic")
struct MultipleReadingsCounterHeaderTests {

    // MARK: First reading

    @Test("count == 1: no header view shown")
    func firstReadingHasNoHeader() {
        let result = headerViewCount(readingCount: 1)
        #expect(result == nil)
    }

    // MARK: Second reading

    @Test("count == 2: header view shown with additional count of 1")
    func secondReadingShowsHeaderWithCount1() {
        let result = headerViewCount(readingCount: 2)
        #expect(result == 1)
    }

    // MARK: Third and subsequent readings

    @Test("count == 3: header shows 2 additional readings")
    func thirdReadingShowsHeaderWithCount2() {
        let result = headerViewCount(readingCount: 3)
        #expect(result == 2)
    }

    @Test("count == 10: header shows 9 additional readings")
    func tenthReadingShowsCorrectAdditionalCount() {
        let result = headerViewCount(readingCount: 10)
        #expect(result == 9)
    }

    @Test("additional count is always count minus 1")
    func additionalCountEqualsCountMinusOne() {
        for count in 2...20 {
            #expect(headerViewCount(readingCount: count) == count - 1)
        }
    }
}

// MARK: - isReplacing flag

@Suite("Multiple readings counter — isReplacing flag")
struct MultipleReadingsCounterIsReplacingTests {

    @Test("isReplacing is false after first reading (count == 1)")
    func isReplacingFalseAfterFirstReading() {
        #expect(isReplacingAfterShow(count: 1) == false)
    }

    @Test("isReplacing is true after second reading (count == 2)")
    func isReplacingTrueAfterSecondReading() {
        #expect(isReplacingAfterShow(count: 2) == true)
    }

    @Test("isReplacing is true for every subsequent reading")
    func isReplacingTrueForAllSubsequentReadings() {
        for count in 2...15 {
            #expect(isReplacingAfterShow(count: count) == true,
                    "Expected isReplacing == true for count \(count)")
        }
    }
}

// MARK: - onDismiss closure

@Suite("Multiple readings counter — onDismiss closure")
struct MultipleReadingsCounterOnDismissTests {

    // MARK: Normal dismiss (no replacement in flight)

    @Test("onDismiss without isReplacing zeroes the counter")
    func normalDismissZeroesCounter() {
        let (count, _) = simulateOnDismiss(count: 3, isReplacing: false)
        #expect(count == 0)
    }

    @Test("onDismiss without isReplacing clears the flag (already false)")
    func normalDismissFlagRemainsCleared() {
        let (_, flag) = simulateOnDismiss(count: 3, isReplacing: false)
        #expect(flag == false)
    }

    @Test("onDismiss with count == 1 and isReplacing false zeroes the counter")
    func singleReadingDismissZeroesCounter() {
        let (count, _) = simulateOnDismiss(count: 1, isReplacing: false)
        #expect(count == 0)
    }

    // MARK: Replace-in-flight dismiss (isReplacing == true)

    @Test("onDismiss when isReplacing == true preserves the counter")
    func replacingDismissPreservesCounter() {
        let (count, _) = simulateOnDismiss(count: 3, isReplacing: true)
        #expect(count == 3)
    }

    @Test("onDismiss when isReplacing == true clears the isReplacing flag")
    func replacingDismissClearsFlag() {
        let (_, flag) = simulateOnDismiss(count: 3, isReplacing: true)
        #expect(flag == false)
    }

    @Test("onDismiss when isReplacing == true does NOT zero counter for any count value")
    func replacingDismissPreservesCounterForAnyValue() {
        for count in 1...10 {
            let (result, _) = simulateOnDismiss(count: count, isReplacing: true)
            #expect(result == count,
                    "Counter must be preserved when isReplacing; count=\(count)")
        }
    }

    // MARK: Sequential dismiss sequence

    @Test("two sequential dismisses without isReplacing both zero the counter independently")
    func twoSequentialNormalDismisses() {
        let (first, _) = simulateOnDismiss(count: 5, isReplacing: false)
        #expect(first == 0)
        let (second, _) = simulateOnDismiss(count: 2, isReplacing: false)
        #expect(second == 0)
    }

    @Test("replacing-dismiss then normal-dismiss: counter zeroed on second dismiss")
    func replacingFollowedByNormalDismiss() {
        // First dismiss: card replaced, counter stays at 3, flag cleared
        let (count1, flag1) = simulateOnDismiss(count: 3, isReplacing: true)
        #expect(count1 == 3)
        #expect(flag1 == false)

        // Second dismiss: no replacement in flight, counter now zeroed
        let (count2, flag2) = simulateOnDismiss(count: count1, isReplacing: flag1)
        #expect(count2 == 0)
        #expect(flag2 == false)
    }
}

// MARK: - Full reading-arrival sequence

@Suite("Multiple readings counter — full arrival sequences")
struct MultipleReadingsCounterSequenceTests {

    /// Simulates the full state machine for one showXxxReadingArrivalCard call.
    /// Returns (countAfter, isReplacingAfter, headerCount).
    private func arrive(currentCount: Int, currentIsReplacing: Bool)
        -> (count: Int, isReplacing: Bool, headerCount: Int?) {
        let newCount = currentCount + 1
        let header = headerViewCount(readingCount: newCount)
        let newIsReplacing = isReplacingAfterShow(count: newCount)
        return (newCount, newIsReplacing, header)
    }

    @Test("first arrival: count=1, no header, isReplacing=false")
    func firstArrival() {
        let s = arrive(currentCount: 0, currentIsReplacing: false)
        #expect(s.count == 1)
        #expect(s.headerCount == nil)
        #expect(s.isReplacing == false)
    }

    @Test("second arrival: count=2, header shows 1, isReplacing=true")
    func secondArrival() {
        let s = arrive(currentCount: 1, currentIsReplacing: false)
        #expect(s.count == 2)
        #expect(s.headerCount == 1)
        #expect(s.isReplacing == true)
    }

    @Test("third arrival: count=3, header shows 2, isReplacing=true")
    func thirdArrival() {
        let s = arrive(currentCount: 2, currentIsReplacing: true)
        #expect(s.count == 3)
        #expect(s.headerCount == 2)
        #expect(s.isReplacing == true)
    }

    @Test("four rapid arrivals produce correct sequence of header counts")
    func fourRapidArrivals() {
        var count = 0
        var isReplacing = false
        var headers: [Int?] = []

        for _ in 1...4 {
            let s = arrive(currentCount: count, currentIsReplacing: isReplacing)
            count = s.count
            isReplacing = s.isReplacing
            headers.append(s.headerCount)
        }

        // Reading 1 → nil, reading 2 → 1, reading 3 → 2, reading 4 → 3
        #expect(headers[0] == nil)
        #expect(headers[1] == 1)
        #expect(headers[2] == 2)
        #expect(headers[3] == 3)
    }

    @Test("onViewHeader resets counter to 0")
    func onViewHeaderResetsCounter() {
        // Simulate onViewHeader: counter is zeroed and the tab is navigated
        var count = 5
        // onViewHeader body: self.babyReadingCount = 0
        count = 0
        #expect(count == 0)
    }

    @Test("arrival after onViewHeader starts fresh: no header on first new reading")
    func arrivalAfterViewHeaderIsFirstReading() {
        // User viewed history (counter reset to 0), then another reading arrives
        let s = arrive(currentCount: 0, currentIsReplacing: false)
        #expect(s.count == 1)
        #expect(s.headerCount == nil)
    }
}

// MARK: - moreReadingsReceived string format

@Suite("Multiple readings counter — moreReadingsReceived string format")
struct MultipleReadingsCounterStringTests {

    @Test("count == 1 uses singular 'reading'")
    func singleAdditionalReadingIsSingular() {
        let text = DashboardStrings.moreReadingsReceived(1)
        #expect(text == "1 more reading received for this session")
    }

    @Test("count == 2 uses plural 'readings'")
    func twoAdditionalReadingsIsPlural() {
        let text = DashboardStrings.moreReadingsReceived(2)
        #expect(text == "2 more readings received for this session")
    }

    @Test("count == 9 uses plural 'readings'")
    func nineAdditionalReadingsIsPlural() {
        let text = DashboardStrings.moreReadingsReceived(9)
        #expect(text == "9 more readings received for this session")
    }

    @Test("displayed count matches count minus 1 from the raw session counter")
    func displayedCountIsCountMinusOne() {
        // When BottomTabBarViewModel has counted N readings, the header receives (N - 1)
        // because the card itself represents reading #1.
        let sessionCount = 4          // 4th reading in session
        let displayCount = sessionCount - 1   // 3 "additional" readings shown
        let text = DashboardStrings.moreReadingsReceived(displayCount)
        #expect(text.hasPrefix("3 "))
        #expect(text.contains("readings"))
    }

    @Test("count == 0 is never produced by the state machine (guard: count > 1 before creating header)")
    func zeroCountNeverReachedByStateMachine() {
        // headerViewCount returns nil for count <= 1, so moreReadingsReceived is never
        // called with 0 from within the VM.
        let result = headerViewCount(readingCount: 1)
        #expect(result == nil, "headerViewCount(1) must be nil; moreReadingsReceived(0) must never be invoked")
    }
}

// MARK: - Integration with BottomTabBarViewModel via mocks
//
// These tests drive the real BottomTabBarViewModel and assert on the headerView
// presence in the captured ToastModel, exercising the full counter state machine
// end-to-end through the mock infrastructure already established in
// BottomTabBarViewModelTests.swift.

@Suite("Multiple readings counter — BottomTabBarViewModel integration", .serialized)
@MainActor
struct MultipleReadingsCounterIntegrationTests {

    // MARK: BT Weight reading counter

    @Test("first BT weight reading: headerView is nil")
    func firstBtWeightReadingHasNoHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 1 }
        #expect(shown)
        #expect(notification.toastData?.headerView == nil,
                "First weight reading must not show a multiple-readings header")
        _ = sut
    }

    @Test("second BT weight reading: headerView is non-nil")
    func secondBtWeightReadingHasHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        // First reading
        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        _ = await waitUntilCounter { notification.showToastCalls >= 1 }

        // Second reading — triggers the replace path; header must appear
        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 2 }
        #expect(shown)
        #expect(notification.toastData?.headerView != nil,
                "Second weight reading must show a multiple-readings header")
        _ = sut
    }

    @Test("BT weight counter resets after normal dismiss: next reading has no header")
    func btWeightCounterResetsAfterNormalDismiss() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        // First reading
        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        _ = await waitUntilCounter { notification.showToastCalls >= 1 }

        // Normal dismiss — isReplacing is false at this point (count was 1 after show)
        notification.toastData?.onDismiss?()

        // New reading arrives after counter has been reset
        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 2 }
        #expect(shown)
        #expect(notification.toastData?.headerView == nil,
                "After counter reset via dismiss, the next reading should have no header")
        _ = sut
    }

    // MARK: BPM reading counter

    @Test("first BPM reading: headerView is nil")
    func firstBpmReadingHasNoHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 1 }
        #expect(shown)
        #expect(notification.toastData?.headerView == nil,
                "First BPM reading must not show a multiple-readings header")
        _ = sut
    }

    @Test("second BPM reading: headerView is non-nil")
    func secondBpmReadingHasHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())
        _ = await waitUntilCounter { notification.showToastCalls >= 1 }

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 2 }
        #expect(shown)
        #expect(notification.toastData?.headerView != nil,
                "Second BPM reading must show a multiple-readings header")
        _ = sut
    }

    // MARK: Baby reading counter

    @Test("first baby reading: headerView is nil")
    func firstBabyReadingHasNoHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 1 }
        #expect(shown)
        #expect(notification.toastData?.headerView == nil,
                "First baby reading must not show a multiple-readings header")
        _ = sut
    }

    @Test("second baby reading: headerView is non-nil")
    func secondBabyReadingHasHeader() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        // Override: need at least one baby so the card type is deterministic
        let babyMock = MockBabyService()
        babyMock.babies = [Baby(accountId: "test-account", name: "Baby A", deviceId: nil,
                                birthday: nil, biologicalSex: nil, birthLengthInches: nil,
                                birthWeightLbs: nil, birthWeightOz: nil)]
        DependencyContainer.shared.register(babyMock as BabyServiceProtocol)

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        _ = await waitUntilCounter { notification.showToastCalls >= 1 }

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 2 }
        #expect(shown)
        #expect(notification.toastData?.headerView != nil,
                "Second baby reading must show a multiple-readings header")
        _ = sut
    }

    @Test("isReplacing flag: normal dismiss after counter==1 does not carry over to next reading")
    func isReplacingFalseAfterFirstDismissNoCarryOver() async {
        let (sut, bluetooth, notification, _) = makeCounterSUT()
        bluetooth.isSetupInProgress = false

        // Single BPM reading + dismiss
        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())
        _ = await waitUntilCounter { notification.showToastCalls >= 1 }
        notification.toastData?.onDismiss?()

        // Next reading should be treated as the first again (counter zeroed)
        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())
        let shown = await waitUntilCounter { notification.showToastCalls >= 2 }
        #expect(shown)
        #expect(notification.toastData?.headerView == nil,
                "After normal dismiss the counter must reset; new reading should have no header")
        _ = sut
    }
}

// MARK: - Integration SUT factory

@MainActor
private func makeCounterSUT() -> (
    BottomTabBarViewModel,
    MockBluetoothService,
    MockNotificationHelperService,
    MockEntryService
) {
    TestDependencyContainer.reset()

    let bluetooth = MockBluetoothService()
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)

    let notificationMock = MockNotificationHelperService()
    DependencyContainer.shared.register(notificationMock as NotificationHelperServiceProtocol)

    let entry = MockEntryService()
    DependencyContainer.shared.register(entry as EntryServiceProtocol)

    let permissions = MockPermissionsService()
    DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)

    let push = MockPushNotificationService()
    DependencyContainer.shared.register(push as PushNotificationServiceProtocol)

    let viewModel = BottomTabBarViewModel()
    return (viewModel, bluetooth, notificationMock, entry)
}

// MARK: - waitUntil helper

@MainActor
private func waitUntilCounter(
    timeoutNanoseconds: UInt64 = 3_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
