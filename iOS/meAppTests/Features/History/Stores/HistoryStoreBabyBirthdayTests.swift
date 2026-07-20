//
//  HistoryStoreBabyBirthdayTests.swift
//  meAppTests
//
//  Coverage for baby History birthday behaviour (MOB-1450): adding a baby with birth
//  weight/length never auto-creates a History entry — History stays empty until the user
//  logs a real measurement. A real measurement dated on a birthday shows as a normal,
//  tappable row flagged with the birthday balloon (no empty placeholder rows).
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HistoryStoreBabyBirthdayTests {

    // MARK: - Setup helpers

    struct SUT {
        let store: HistoryStore
        let entryService: MockEntryService
        let notificationService: TestNotificationHelperService
        let accountService: MockAccountService
        let logger: MockLoggerService
        let productTypeStore: MockProductTypeStore
    }

    func makeSUT(
        selection: ProductSelection = .myWeight,
        measurementUnits: String? = nil
    ) -> SUT {
        TestDependencyContainer.reset()

        let entryService = MockEntryService()
        let notificationService = TestNotificationHelperService()
        let accountService = MockAccountService()
        let logger = MockLoggerService()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = selection

        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(productTypeStore as ProductTypeStoreProtocol)

        if let measurementUnits {
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1", isActiveAccount: true, measurementUnits: measurementUnits
            )
            accountService.seedAccounts([account], active: account)
        }

        let store = HistoryStore()
        store.entryService = entryService
        store.notificationService = notificationService
        store.accountService = accountService
        store.logger = logger
        store.productTypeStore = productTypeStore

        return SUT(
            store: store,
            entryService: entryService,
            notificationService: notificationService,
            accountService: accountService,
            logger: logger,
            productTypeStore: productTypeStore
        )
    }

    func waitUntil(timeoutIterations: Int = 300, condition: @escaping @MainActor () -> Bool) async -> Bool {
        for _ in 0..<timeoutIterations {
            if condition() { return true }
            await Task.yield()
        }
        return false
    }

    // MARK: - Birthday rows (MOB-1450)

    @Test("every birthday year shows a row — DOB shows birth values, later birthdays show dashes (MOB-1450)")
    func allBirthdaysShownWithDobValues() async {
        // Baby born two years ago, no logged entries: the DOB and both anniversaries each get a
        // purple birthday row (Weeks 1 / 53 / 105). The DOB shows the profile's birth weight;
        // later birthdays show dashes. No real entry is created (display-only).
        let dob = Calendar.current.date(byAdding: .year, value: -2, to: Date()) ?? Date()
        let profile = BabyProfile(
            id: "baby-1",
            name: "Sona",
            birthday: dob,
            biologicalSex: "female",
            birthLengthInches: 19.5,
            birthWeightLbs: 7,
            birthWeightOz: 5.5
        )
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.imperialLbOz.rawValue)
        sut.entryService.fetchAllEntrySnapshotsResult = .success([])

        sut.store.loadMonths()
        let loaded = await waitUntil { sut.store.babyWeeks.count == 3 }
        #expect(loaded == true)
        #expect(sut.store.babyWeeks.map { $0.weekNumber } == [105, 53, 1])
        #expect(sut.store.babyWeeks.allSatisfy { $0.days.count == 1 && $0.days[0].isBirthdayPlaceholder && $0.days[0].isBirthday })
        // DOB (Week 1) shows the profile's birth weight; the 1-year birthday shows dashes.
        #expect(sut.store.babyWeeks.first { $0.weekNumber == 1 }?.days.first?.weightDisplay.contains("5.5") == true)
        #expect(sut.store.babyWeeks.first { $0.weekNumber == 53 }?.days.first?.weightDisplay
            == "-- \(HistoryListStrings.lb) -- \(HistoryListStrings.oz)")
    }

    @Test("DOB birthday row shows dashes when the baby was added without birth weight/length (MOB-1450)")
    func dobRowShowsDashesWithoutBirthData() async {
        let profile = BabyProfile(
            id: "baby-1",
            name: "Sona",
            birthday: Calendar.current.date(byAdding: .day, value: -3, to: Date()),
            biologicalSex: "female",
            birthLengthInches: nil,
            birthWeightLbs: nil,
            birthWeightOz: nil
        )
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.imperialLbOz.rawValue)
        sut.entryService.fetchAllEntrySnapshotsResult = .success([])

        sut.store.loadMonths()
        let loaded = await waitUntil { !sut.store.babyWeeks.isEmpty }
        #expect(loaded == true)
        let row = sut.store.babyWeeks.first?.days.first
        #expect(row?.isBirthdayPlaceholder == true)
        #expect(row?.weightDisplay == "-- \(HistoryListStrings.lb) -- \(HistoryListStrings.oz)")
        #expect(row?.lengthDisplay == "-- \(HistoryListStrings.inUnit)")
    }

    @Test("a real measurement on the birthday date replaces the placeholder with a tappable row (MOB-1450)")
    func realEntryOnBirthdayIsNotPlaceholder() async {
        let dob = Calendar.current.date(byAdding: .day, value: -3, to: Date()) ?? Date()
        let profile = BabyProfile(
            id: "baby-1",
            name: "Sona",
            birthday: dob,
            biologicalSex: "female",
            birthLengthInches: 19.5,
            birthWeightLbs: 7,
            birthWeightOz: 5.5
        )
        let dobTimestamp = DateTimeTools.isoFormatter(useUTC: true).string(from: dob)
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.imperialLbOz.rawValue)
        sut.entryService.fetchAllEntrySnapshotsResult = .success([
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: dobTimestamp, babyId: profile.id, weight: 5200, length: 520)
        ])

        sut.store.loadMonths()
        let done = await waitUntil { !sut.store.babyWeeks.isEmpty }
        #expect(done == true)
        let allDays = sut.store.babyWeeks.flatMap { $0.days }
        #expect(allDays.count == 1)
        let day = allDays.first
        // Real (navigable) row flagged as birthday — not a placeholder, real value (not dashes).
        #expect(day?.isBirthday == true)
        #expect(day?.isBirthdayPlaceholder == false)
        #expect(day?.entryCount == 1)
        #expect(day?.weightDisplay.contains("--") == false)
        #expect(sut.store.babyWeeks.first?.containsBirthday == true)
    }
}
