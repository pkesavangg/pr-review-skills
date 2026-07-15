//
//  MyKidsAddBabyScreen.swift
//  meApp
//

import SwiftUI

/// Standalone screen that directly opens the add-baby form.
/// Used when navigating from Manual Entry's empty-state CTA so the user
/// lands straight on the form instead of going through My Kids first.
struct MyKidsAddBabyScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @StateObject private var store = MyKidsStore()
    @State private var isSaving = false

    private let lang = MyKidsStrings.self

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: lang.addBaby,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    Button {
                        guard !isSaving else { return }
                        isSaving = true
                        Task {
                            await store.saveBabyProfile()
                            isSaving = false
                        }
                    } label: {
                        Text(lang.save)
                            .fontOpenSans(.heading5)
                            .fontWeight(.bold)
                            .foregroundColor(
                                store.isSaveEnabled
                                    ? theme.actionPrimary
                                    : theme.textSubheading
                            )
                    }
                    .disabled(!store.isSaveEnabled || isSaving)
                    .appAccessibility(id: AccessibilityID.myKidsSaveBabyButton)
                },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )

            BabyProfileFormView(
                form: store.babyProfileForm,
                showDatePicker: $store.showBabyDatePicker,
                hideHeader: true,
                hideUnitToggle: true
            )
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarHidden(true)
        .screenAccessibilityRoot(AccessibilityID.addBabyScreenRoot)
        .onAppear { store.addBaby() }
        .onChange(of: store.isShowingAddBaby) { _, isShowing in
            if !isShowing { router.navigateBack() }
        }
        .onChange(of: store.lastSavedBabyId) { _, babyId in
            guard let babyId, tabViewModel.pendingBabyAssignmentEntryId != nil else { return }
            Task { await tabViewModel.assignPendingEntry(to: babyId) }
        }
    }
}
