import Foundation
import Testing
@testable import meApp

extension SettingsStoreTests {
    @Suite("Core State")
    @MainActor
    struct Core {
        @Test("initial state hydrates account-driven values and entry presence")
        func initialStateHydratesAccountState() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account, SettingsStoreTestFixtures.makeAccount(id: "acct-2", email: "two@example.com", firstName: "Two")], active: account)
            let entryService = MockEntryService()
            entryService.getMonthsAllResult = .success([
                HistoryMonth(id: "2026-03", weight: 150, entryTimestamp: "2026-03", count: 3, weights: nil, change: nil, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, date: nil, time: nil, month: "03", year: "2026", min: nil, max: nil)
            ])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, entryService: entryService)

            await SettingsStoreTestFixtures.waitUntil {
                store.activeAccount?.accountId == account.accountId && store.hasEntries
            }

            #expect(store.activeAccount?.accountId == account.accountId)
            #expect(store.canShowLogOutAllItems == true)
            #expect(store.hasEntries == true)
            #expect(entryService.getMonthsAllCalls > 0)
        }

        @Test("derived texts reflect active account settings")
        func derivedTextsReflectAccountSettings() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            #expect(store.profileInitial == "L")
            #expect(store.profileName == "Lakshmi")
            #expect(store.profileEmail == "lakshmi@example.com")
            #expect(store.biologicalSexText == "Female")
            #expect(store.activityLevelText == "Athlete")
            #expect(store.heightText == ConversionTools.convertToFormattedHeight(681, isMetric: true))
            #expect(store.unitTypeText == CommonStrings.unitKgCm)
            #expect(store.notificationsOnText == "\(CommonStrings.on) w/ Weight")
            #expect(store.streaksOnText == CommonStrings.on)
        }

        @Test("derived texts return defaults without active account")
        func derivedTextsReturnDefaultsWithoutActiveAccount() {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            #expect(store.profileInitial == "")
            #expect(store.profileName == "")
            #expect(store.profileEmail == "")
            #expect(store.biologicalSexText == "")
            #expect(store.activityLevelText == "")
            #expect(store.heightText == "")
            #expect(store.unitTypeText == "")
            #expect(store.notificationsOnText == CommonStrings.off)
            #expect(store.notificationPreference == .disable)
        }

        @Test("weightless text shows on with converted value")
        func weightlessTextShowsFormattedValue() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)
            let display = ConversionTools.convertStoredToKg(1550)
            let expected = "\(CommonStrings.on) - \(String(format: "%.1f", display)) \(WeightValueConvertor.unitForDisplay(value: display, unit: .kg))"

            #expect(store.weightlessText == expected)
        }

        @Test("weightless text shows not set when enabled without stored weight")
        func weightlessTextShowsNotSetWhenEnabledWithoutStoredWeight() {
            let account = SettingsStoreTestFixtures.makeAccount()
            account.weightlessSettings?.isWeightlessOn = true
            account.weightlessSettings?.weightlessWeight = nil
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            #expect(store.weightlessText == "\(CommonStrings.on) - Not Set")
        }

        @Test("messages title includes unread count only when badge is enabled")
        func messagesTitleUsesUnreadCountWhenBadgeEnabled() {
            let feed = MockFeedService()
            feed.unreadFeedCount = 4
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(feedService: feed)

            #expect(store.messagesTitleText == SettingsStrings.messagesWithNew(4))
            store.canShowFeedNotificationBadge = false
            #expect(store.messagesTitleText == SettingsStrings.messages)
        }

        @Test("browser presentation setter clears flags and url")
        func browserPresentationSetterClearsState() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.showPrivacyBrowser = true
            store.browserURL = URL(string: "https://example.com")

            store.isBrowserPresented.wrappedValue = false

            #expect(store.showPrivacyBrowser == false)
            #expect(store.showTermsBrowser == false)
            #expect(store.showGreaterGoodsBrowser == false)
            #expect(store.browserURL == nil)
        }

        @Test("open link handlers set browser state")
        func openLinkHandlersSetBrowserState() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.openPrivacy()
            #expect(store.showPrivacyBrowser == true)
            #expect(store.browserURL == AppConstants.LegalURLs.privacyPolicy)

            store.openTerms()
            #expect(store.showTermsBrowser == true)
            #expect(store.browserURL == AppConstants.LegalURLs.termsOfService)

            store.openGreaterGoods()
            #expect(store.showGreaterGoodsBrowser == true)
            #expect(store.browserURL == AppConstants.LegalURLs.greaterGoodsWebsite)
        }

        @Test("notification badge publisher updates messages title state")
        func notificationBadgePublisherUpdatesState() async {
            let feed = MockFeedService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(feedService: feed)

            feed.notificationBadgeUpdated.send(false)
            await SettingsStoreTestFixtures.waitUntil { store.canShowFeedNotificationBadge == false }

            #expect(store.canShowFeedNotificationBadge == false)
        }

        @Test("present picker helpers toggle sheet flags")
        func presentPickerHelpersToggleSheetFlags() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.presentAppearancePicker()
            store.presentNotificationPicker()
            store.presentGenderPicker()
            store.presentUnitPicker()
            store.presentActivityPicker()
            store.presentHeightPicker()

            #expect(store.showAppearancePicker == true)
            #expect(store.showNotificationPicker == true)
            #expect(store.showGenderPicker == true)
            #expect(store.showUnitPicker == true)
            #expect(store.showActivityPicker == true)
            #expect(store.showHeightCmPicker == true)
        }

        @Test("init reacts to account publisher updates and syncs forms")
        func initReactsToAccountPublisherUpdatesAndSyncsForms() async {
            let accountService = MockAccountService()
            accountService.seedAccounts([], active: nil)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)
            let account = SettingsStoreTestFixtures.makeAccount()
            account.weightSettings?.height = "650"

            accountService.seedAccounts([account], active: account)
            await SettingsStoreTestFixtures.waitUntil {
                store.activeAccount?.accountId == account.accountId &&
                store.editProfileForm.firstName.value == "Lakshmi" &&
                store.weightlessForm.isOn.value == true &&
                store.selectedHeightCm != ["1", "7", "8"]
            }

            #expect(store.activeAccount?.accountId == account.accountId)
            #expect(store.editProfileForm.firstName.value == "Lakshmi")
            #expect(store.weightlessForm.isOn.value == true)
        }

        @Test("init reacts to all accounts publisher updates")
        func initReactsToAllAccountsPublisherUpdates() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            accountService.seedAccounts(
                [account, SettingsStoreTestFixtures.makeAccount(id: "acct-2", email: "two@example.com", firstName: "Two")],
                active: account
            )
            await SettingsStoreTestFixtures.waitUntil { store.canShowLogOutAllItems == true }

            #expect(store.canShowLogOutAllItems == true)
        }

        @Test("entry saved publisher refreshes has entries")
        func entrySavedPublisherRefreshesHasEntries() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let entryService = MockEntryService()
            entryService.getMonthsAllResult = .success([])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, entryService: entryService, seedDefaultAccount: false)
            await SettingsStoreTestFixtures.waitUntil { store.hasEntries == false }
            entryService.getMonthsAllResult = .success([
                HistoryMonth(id: "2026-03", weight: 150, entryTimestamp: "2026-03", count: 3, weights: nil, change: nil, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, date: nil, time: nil, month: "03", year: "2026", min: nil, max: nil)
            ])

            entryService.entrySaved.send(EntryNotification(from: EntryTestFixtures.makeEntry()))
            await SettingsStoreTestFixtures.waitUntil { store.hasEntries == true }

            #expect(store.hasEntries == true)
        }

        @Test("individual picker helpers set expected flags")
        func individualPickerHelpersSetExpectedFlags() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.presentAppearancePicker()
            #expect(store.showAppearancePicker == true)
            store.presentNotificationPicker()
            #expect(store.showNotificationPicker == true)
            store.presentGenderPicker()
            #expect(store.showGenderPicker == true)
            store.presentUnitPicker()
            #expect(store.showUnitPicker == true)
            store.presentActivityPicker()
            #expect(store.showActivityPicker == true)
        }

        @Test("present picker helpers show modals when modal picker is enabled")
        func presentPickerHelpersShowModalsWhenModalPickerIsEnabled() {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                seedDefaultAccount: false
            )
            store.useModalPicker = true

            store.presentAppearancePicker()
            store.presentNotificationPicker()
            store.presentGenderPicker()
            store.presentUnitPicker()
            store.presentActivityPicker()
            store.presentHeightPicker()

            #expect(notification.showModalCalls == 6)
            #expect(notification.modalViewData.count == 6)
            #expect(store.showAppearancePicker == false)
            #expect(store.showNotificationPicker == false)
            #expect(store.showGenderPicker == false)
            #expect(store.showUnitPicker == false)
            #expect(store.showActivityPicker == false)
            #expect(store.showHeightCmPicker == false)
        }

        @Test("presentHeightPicker uses inches sheet for imperial account")
        func presentHeightPickerUsesInchesSheetForImperialAccount() {
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, seedDefaultAccount: false)

            store.presentHeightPicker()

            #expect(store.showHeightInchesPicker == true)
            #expect(store.showHeightCmPicker == false)
        }

        @Test("presentHeightPicker shows imperial modal when modal picker is enabled")
        func presentHeightPickerShowsImperialModalWhenModalPickerIsEnabled() {
            let account = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(
                notification: notification,
                accountService: accountService,
                seedDefaultAccount: false
            )
            store.useModalPicker = true

            store.presentHeightPicker()

            #expect(notification.showModalCalls == 1)
            #expect(notification.modalViewData.count == 1)
            #expect(store.showHeightInchesPicker == false)
            #expect(store.showHeightCmPicker == false)
        }

        @Test("showHeightPicker respects active account unit")
        func showHeightPickerRespectsUnit() {
            let kgAccount = SettingsStoreTestFixtures.makeAccount()
            let kgService = MockAccountService()
            kgService.seedAccounts([kgAccount], active: kgAccount)
            let (kgStore, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: kgService)

            kgStore.showHeightPicker()
            #expect(kgStore.showHeightCmPicker == true)
            #expect(kgStore.showHeightInchesPicker == false)

            let lbAccount = SettingsStoreTestFixtures.makeAccount(unit: .lb)
            let lbService = MockAccountService()
            lbService.seedAccounts([lbAccount], active: lbAccount)
            let (lbStore, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: lbService)

            lbStore.showHeightPicker()
            #expect(lbStore.showHeightInchesPicker == true)
            #expect(lbStore.showHeightCmPicker == false)
        }

        @Test("presentingBrowserURL falls back to Greater Goods url")
        func presentingBrowserURLFallsBackWhenUnset() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            #expect(store.presentingBrowserURL == AppConstants.LegalURLs.greaterGoodsWebsite)
        }

        @Test("weightless and notification text handle off and partial states")
        func derivedStateHandlesOffAndPartialStates() {
            let account = SettingsStoreTestFixtures.makeAccount()
            account.weightlessSettings?.isWeightlessOn = false
            account.weightlessSettings?.weightlessWeight = nil
            account.notificationSettings?.shouldSendEntryNotifications = true
            account.notificationSettings?.shouldSendWeightInEntryNotifications = false
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService)

            #expect(store.weightlessText == CommonStrings.off)
            #expect(store.notificationsOnText == "\(CommonStrings.on) w/o Weight")
            #expect(store.notificationPreference == .enable)
        }

        @Test("appearance text reflects theme mode")
        func appearanceTextReflectsThemeMode() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            let original = Theme.shared.appearanceMode
            Theme.shared.appearanceMode = .light
            #expect(store.appearanceModeText == CommonStrings.light)
            Theme.shared.appearanceMode = .dark
            #expect(store.appearanceModeText == CommonStrings.dark)
            Theme.shared.appearanceMode = .system
            #expect(store.appearanceModeText == CommonStrings.system)
            Theme.shared.appearanceMode = original
        }

        @Test("presentAddAccountModalIfNeeded does nothing for multiple accounts")
        func presentAddAccountModalIfNeededDoesNothingForMultipleAccounts() async {
            KvStorageService.shared.clearValue(forKey: KvStorageKeys.addMultipleAccountsModal.rawValue)
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account, SettingsStoreTestFixtures.makeAccount(id: "acct-2", email: "two@example.com", firstName: "Two")], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, seedDefaultAccount: false)
            let router = Router<SettingsRoute>()

            store.presentAddAccountModalIfNeeded(router: router)
            await Task.yield()

            #expect(notification.showModalCalls == 0)
        }

        @Test("presentAddAccountModalIfNeeded does nothing when modal already seen")
        func presentAddAccountModalIfNeededDoesNothingWhenSeen() async {
            KvStorageService.shared.setValue(true, forKey: KvStorageKeys.addMultipleAccountsModal.rawValue)
            defer { KvStorageService.shared.clearValue(forKey: KvStorageKeys.addMultipleAccountsModal.rawValue) }
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, seedDefaultAccount: false)
            let router = Router<SettingsRoute>()

            store.presentAddAccountModalIfNeeded(router: router)
            await Task.yield()

            #expect(notification.showModalCalls == 0)
        }

        @Test("presentAddAccountModalIfNeeded presents modal for eligible single account")
        func presentAddAccountModalIfNeededPresentsModal() async {
            KvStorageService.shared.clearValue(forKey: KvStorageKeys.addMultipleAccountsModal.rawValue)
            defer { KvStorageService.shared.clearValue(forKey: KvStorageKeys.addMultipleAccountsModal.rawValue) }
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService, seedDefaultAccount: false)
            let router = Router<SettingsRoute>()

            store.presentAddAccountModalIfNeeded(router: router)
            await SettingsStoreTestFixtures.waitUntil(timeoutNanoseconds: 2_000_000_000) { notification.showModalCalls == 1 }

            #expect(notification.showModalCalls == 1)
            #expect((KvStorageService.shared.getValue(forKey: KvStorageKeys.addMultipleAccountsModal.rawValue) as? Bool) == true)
        }

        @Test("entry publisher refresh can clear hasEntries on failure")
        func entryPublisherRefreshCanClearHasEntriesOnFailure() async {
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let entryService = MockEntryService()
            entryService.getMonthsAllResult = .success([
                HistoryMonth(id: "2026-03", weight: 150, entryTimestamp: "2026-03", count: 3, weights: nil, change: nil, bodyFat: nil, muscleMass: nil, water: nil, bmi: nil, date: nil, time: nil, month: "03", year: "2026", min: nil, max: nil)
            ])
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(accountService: accountService, entryService: entryService, seedDefaultAccount: false)
            await SettingsStoreTestFixtures.waitUntil { store.hasEntries == true }
            entryService.getMonthsAllResult = .failure(AccountTestError.apiFailed)

            entryService.entryDeleted.send(EntryNotification(from: EntryTestFixtures.makeEntry()))
            await SettingsStoreTestFixtures.waitUntil { store.hasEntries == false }

            #expect(store.hasEntries == false)
        }
    }
}
