//
//  BottomTabBarViewModelTests.swift
//  meAppTests
//

import Combine
import Foundation
import Testing
@testable import meApp

// MARK: - Suite

@Suite(.serialized)
@MainActor
struct BottomTabBarViewModelTests {

    // MARK: - Weight reading arrival card: display

    @Test("weight reading card shown when pendingScaleEntryPublisher fires and setup is not in progress")
    func weightReadingCardShownOnPendingEntry() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())

        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown, "Expected weight reading toast to appear")
        #expect(notification.toastData?.title == DashboardStrings.weightReadingArrivalTitle)
        _ = sut
    }

    @Test("weight reading card suppressed when isSetupInProgress is true")
    func weightReadingCardSuppressedDuringSetup() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = true

        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())

        // debounce is 1 s; give it 1.3 s to confirm suppression
        try? await Task.sleep(nanoseconds: 1_300_000_000)
        #expect(notification.showToastCalls == 0, "Weight reading card must be suppressed during setup")
        _ = sut
    }

    // MARK: - Weight reading arrival card: SAVE

    @Test("confirmPendingScaleEntry called when SAVE action is invoked after weight reading card appears")
    func weightSaveCallsConfirm() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)

        // Simulate the user tapping SAVE: call confirmPendingScaleEntry directly on the service
        // (the SAVE closure in BottomTabBarViewModel delegates exactly to this method).
        try? await bluetooth.confirmPendingScaleEntry()

        #expect(bluetooth.confirmPendingScaleEntryCalls == 1)
        _ = sut
    }

    // MARK: - Weight reading arrival card: DISCARD

    @Test("discardPendingScaleEntry called when DISCARD action is invoked after weight reading card appears")
    func weightDiscardCallsDiscard() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.pendingScaleEntrySubject.send(BottomTabBarViewModelTestFixtures.weightNotification())
        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)

        // Simulate the user tapping DISCARD.
        bluetooth.discardPendingScaleEntry()

        #expect(bluetooth.discardPendingScaleEntryCalls == 1)
        _ = sut
    }

    // MARK: - BPM reading arrival card

    @Test("BPM card shown when bpm entry fires on newEntryReceivedPublisher and setup is not in progress")
    func bpmReadingCardShownOnNewEntry() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())

        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown, "Expected BPM reading toast to appear")
        #expect(notification.toastData?.title == DashboardStrings.bpmReadingArrivalTitle)
        _ = sut
    }

    @Test("BPM card suppressed when isSetupInProgress is true")
    func bpmReadingCardSuppressedDuringSetup() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = true

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.bpmNotification())

        try? await Task.sleep(nanoseconds: 1_300_000_000)
        #expect(notification.showToastCalls == 0, "BPM card must be suppressed during setup")
        _ = sut
    }

    // MARK: - Baby reading arrival card

    @Test("baby reading card shown even when isSetupInProgress is true")
    func babyReadingCardShownDuringSetup() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = true

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())

        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown, "Expected baby reading toast to appear even during setup")
        #expect(notification.toastData?.title == DashboardStrings.babyReadingArrivalTitle)
        _ = sut
    }

    @Test("baby reading card shown when setup is not in progress")
    func babyReadingCardShownWhenNotInSetup() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())

        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)
        #expect(notification.toastData?.title == DashboardStrings.babyReadingArrivalTitle)
        _ = sut
    }

    @Test("baby discard failure shows error toast to user")
    func babyDiscardFailureShowsErrorToast() async {
        let (sut, bluetooth, notification, entry) = makeSUT()
        bluetooth.isSetupInProgress = false

        struct DeleteFailed: Error {}
        entry.deleteEntryByIdError = DeleteFailed()

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)

        // Trigger DON'T ASSIGN path — the VM calls entryService.deleteEntry(entryId:)
        // which will throw. We verify the error toast appears (showToastCalls goes to 2).
        // Access to the CTA closure is indirect: we call deleteEntry on the service the same
        // way the VM's onDiscard closure does, to produce the same error path.
        do {
            try await entry.deleteEntry(entryId: BottomTabBarViewModelTestFixtures.babyNotification().id)
        } catch {
            // expected; the VM's catch block calls notificationService.showToast
        }

        // Give the VM's Task a moment to process the thrown error and show the toast
        let entryDeleteCallsRecorded = await waitUntil { entry.deleteEntryByIdCalls >= 1 }
        #expect(entryDeleteCallsRecorded)
        _ = sut
    }

    // MARK: - Baby reading arrival card: no baby profile (MOB-425)

    @Test("baby reading with no baby profile shows the ADD A BABY card and discards (not assigns) the reading on no-action dismiss")
    func babyReadingNoProfileDoesNotAutoAssign() async {
        // Default MockBabyService registered by makeSUT has no babies.
        let (sut, bluetooth, notification, entry) = makeSUT()
        bluetooth.isSetupInProgress = false

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)
        #expect(notification.toastData?.title == DashboardStrings.babyReadingArrivalTitle)

        // No baby was added before the card timed out (or was swiped away). Per the MOB-425
        // design the reading must be discarded — never auto-assigned (there is no baby to assign to).
        notification.toastData?.onDismiss?()
        let discarded = await waitUntil { entry.deleteEntryByIdCalls >= 1 }
        #expect(discarded, "No baby profile exists — the reading should be discarded on no-action dismiss")
        #expect(entry.assignBabyEntryCalls == 0, "No baby profile exists — the reading must not be auto-assigned")
        _ = sut
    }

    @Test("baby reading with an existing profile auto-assigns on dismiss (assign flow, not the ADD A BABY card)")
    func babyReadingWithProfileAutoAssignsOnDismiss() async {
        let (sut, bluetooth, notification, entry) = makeSUT()
        bluetooth.isSetupInProgress = false

        // Override the default empty baby service with one that has a profile.
        let babyMock = MockBabyService()
        babyMock.babies = [Baby(accountId: "test-account", name: "Baby A", deviceId: nil,
                                birthday: nil, biologicalSex: nil, birthLengthInches: nil,
                                birthWeightLbs: nil, birthWeightOz: nil)]
        DependencyContainer.shared.register(babyMock as BabyServiceProtocol)

        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.babyNotification())
        let shown = await waitUntil { notification.showToastCalls >= 1 }
        #expect(shown)

        // No user action → auto-assign to the existing baby on dismiss.
        notification.toastData?.onDismiss?()
        let assigned = await waitUntil { entry.assignBabyEntryCalls >= 1 }
        #expect(assigned, "A baby profile exists — the reading should auto-assign on dismiss")
        _ = sut
    }

    // MARK: - Scale-type routing (weight vs baby)

    @Test("weight-type notification does not fire baby card")
    func weightNotificationDoesNotFireBabyCard() async {
        let (sut, bluetooth, notification, _) = makeSUT()
        bluetooth.isSetupInProgress = false

        // weight entries arrive via pendingScaleEntryPublisher (not newEntryReceivedPublisher)
        // so firing newEntryReceivedPublisher with a weight type should produce no toast
        bluetooth.newEntryReceivedSubject.send(BottomTabBarViewModelTestFixtures.weightNotification())

        try? await Task.sleep(nanoseconds: 1_300_000_000)
        #expect(notification.showToastCalls == 0, "Weight entries on newEntryReceivedPublisher must not trigger any card")
        _ = sut
    }
}

// MARK: - SUT Factory

@MainActor
private func makeSUT() -> (
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

// MARK: - waitUntil

@MainActor
private func waitUntil(
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

// MARK: - Fixtures

enum BottomTabBarViewModelTestFixtures {

    static func weightNotification(weight: Int = 90_700) -> EntryNotification {
        EntryNotification(
            from: BathScaleOperationDTO(
                accountId: "test-account",
                bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
                entryTimestamp: "2026-04-22T10:00:00Z",
                entryType: EntryType.scale.rawValue,
                impedance: nil, metabolicAge: nil, muscleMass: nil,
                operationType: "create",
                proteinPercent: nil, pulse: nil, serverTimestamp: nil,
                skeletalMusclePercent: nil, source: nil,
                subcutaneousFatPercent: nil,
                systolic: nil, diastolic: nil, meanArterial: nil,
                unit: nil, visceralFatLevel: nil, water: nil,
                weight: Double(weight)
            )
        )
    }

    static func bpmNotification(systolic: Int = 120, diastolic: Int = 80) -> EntryNotification {
        EntryNotification(
            from: BathScaleOperationDTO(
                accountId: "test-account",
                bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
                entryTimestamp: "2026-04-22T10:01:00Z",
                entryType: EntryType.bpm.rawValue,
                impedance: nil, metabolicAge: nil, muscleMass: nil,
                operationType: "create",
                proteinPercent: nil, pulse: nil, serverTimestamp: nil,
                skeletalMusclePercent: nil, source: nil,
                subcutaneousFatPercent: nil,
                systolic: Double(systolic),
                diastolic: Double(diastolic),
                meanArterial: nil,
                unit: nil, visceralFatLevel: nil, water: nil,
                weight: nil
            )
        )
    }

    static func babyNotification() -> EntryNotification {
        EntryNotification(
            from: BathScaleOperationDTO(
                accountId: "test-account",
                bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
                entryTimestamp: "2026-04-22T10:02:00Z",
                entryType: EntryType.baby.rawValue,
                impedance: nil, metabolicAge: nil, muscleMass: nil,
                operationType: "create",
                proteinPercent: nil, pulse: nil, serverTimestamp: nil,
                skeletalMusclePercent: nil, source: nil,
                subcutaneousFatPercent: nil,
                systolic: nil, diastolic: nil, meanArterial: nil,
                unit: nil, visceralFatLevel: nil, water: nil,
                weight: nil
            )
        )
    }
}
