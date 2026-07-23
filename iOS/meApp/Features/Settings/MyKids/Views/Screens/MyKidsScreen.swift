//
//  MyKidsScreen.swift
//  meApp
//

import SwiftUI

struct MyKidsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject private var store = MyKidsStore()
    @State private var openItemID: UUID?
    @State private var expandedBabyIDs: Set<String> = []

    private let lang = MyKidsStrings.self

    /// Returns a deterministic UUID for the given baby.
    /// Baby IDs are server-assigned strings that are not in UUID format,
    /// so `UUID(uuidString:)` returns nil. The `?? UUID()` fallback creates
    /// a NEW random UUID on every render, which breaks the swipe-open state
    /// machine that relies on a stable itemID across re-renders.
    private func babyItemID(_ baby: Baby) -> UUID {
        if let uuid = UUID(uuidString: baby.id) { return uuid }
        // Hash the full ID string to avoid prefix-collision for long server IDs.
        var hasher = Hasher()
        hasher.combine(baby.id)
        let hashHigh = UInt64(bitPattern: Int64(hasher.finalize()))
        let salt: UInt64 = 0xDEAD_BEEF_CAFE_1234
        let hashLow = hashHigh ^ salt
        let highBytes: [UInt8] = (0..<8).map { UInt8((hashHigh >> ($0 * 8)) & 0xFF) }
        let lowBytes: [UInt8] = (0..<8).map { UInt8((hashLow >> ($0 * 8)) & 0xFF) }
        return UUID(uuid: (highBytes[0], highBytes[1], highBytes[2], highBytes[3],
                           highBytes[4], highBytes[5], highBytes[6], highBytes[7],
                           lowBytes[0], lowBytes[1], lowBytes[2], lowBytes[3],
                           lowBytes[4], lowBytes[5], lowBytes[6], lowBytes[7]))
    }

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            if store.babies.isEmpty {
                emptyState
            } else {
                babyList
            }
        }
        .navigationBarHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .screenAccessibilityRoot(AccessibilityID.myKidsScreenRoot)
        .task { await store.loadBabies() }
        .sheet(isPresented: $store.isShowingAddBaby) {
            addBabySheet
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: .spacingLG) {
            Text(lang.subtitle)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .padding(.horizontal, .spacingSM)

            ButtonView(
                text: lang.addABaby,
                type: .filledPrimary,
                size: .large,
                isDisabled: false
            ) {
                store.addBaby()
            }
            .appAccessibility(id: AccessibilityID.myKidsAddBabyButton)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, .spacingSM)

            Spacer()
        }
        .padding(.top, .spacingLG)
    }

    // MARK: - Baby List

    private var babyList: some View {
        ZStack {
            theme.backgroundSecondary.ignoresSafeArea()
            List {
                babyListSection
                addBabyCTA
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
    }

    @ViewBuilder
    private var babyListSection: some View {
        Section {
            ForEach(store.babies, id: \.id) { baby in
                babyRow(baby)
                    .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
                    .accessibilityElement(children: .contain)
                    .appAccessibility(id: AccessibilityID.myKidsBabyRow + "_" + baby.id)
            }
        } header: {
            Text(lang.subtitle)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .textCase(nil)
                .padding(.bottom, .spacingSM)
                .padding(.leading, -16)
        }
        .listRowBackground(Color.clear)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private var addBabyCTA: some View {
        VStack(alignment: .center, spacing: .spacingLG) {
            ButtonView(
                text: lang.addABaby,
                type: .outlinedPrimary,
                size: .large,
                isDisabled: false
            ) {
                store.addBaby()
            }
            .appAccessibility(id: AccessibilityID.myKidsAddBabyButton)
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    // MARK: - Baby Row

    private func babyRow(_ baby: Baby) -> some View {
        BabyProfileRowView(
            name: baby.name,
            details: store.detailRows(for: baby),
            isExpanded: expandedBabyIDs.contains(baby.id),
            babyId: baby.id,
            swipeItemID: babyItemID(baby),
            openSwipeItemID: $openItemID,
            onToggleExpand: { toggleExpanded(baby) },
            onEdit: {
                // Ignore an edit tap that lands while this row is swiped open — the tap
                // should close the swipe, not push the edit sheet.
                guard openItemID != babyItemID(baby) else { return }
                store.editBaby(baby)
            },
            onDelete: { store.confirmDeleteBaby(baby) }
        )
    }

    /// Toggles the expanded profile-details section for a baby row.
    ///
    /// The reveal is animated by the row's own `.animation(.easeInOut, value: isExpanded)`
    /// modifier (the same style as the History entry detail), so the mutation here is a plain
    /// state flip — no `withAnimation` wrapper (MOB-1605).
    private func toggleExpanded(_ baby: Baby) {
        if expandedBabyIDs.contains(baby.id) {
            expandedBabyIDs.remove(baby.id)
        } else {
            expandedBabyIDs.insert(baby.id)
        }
    }

    // MARK: - Add/Edit Baby Sheet

    private var addBabySheet: some View {
        NavigationStack {
            VStack(spacing: 0) {
                NavbarHeaderView(
                    title: lang.addBaby,
                    leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                    trailingContent: {
                        Button {
                            Task { await store.saveBabyProfile() }
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
                        .disabled(!store.isSaveEnabled)
                        .appAccessibility(id: AccessibilityID.myKidsSaveBabyButton)
                    },
                    onLeadingTap: { store.isShowingAddBaby = false },
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
            .screenAccessibilityRoot(AccessibilityID.addBabyScreenRoot)
        }
        .presentationDragIndicator(.visible)
    }
}
