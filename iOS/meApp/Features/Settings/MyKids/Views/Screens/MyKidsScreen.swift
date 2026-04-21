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

    private let lang = MyKidsStrings.self
    private let swipeButtonWidth: CGFloat = 56

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
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
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
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    // MARK: - Baby Row

    private func babyRow(_ baby: Baby) -> some View {
        HStack(spacing: .spacingSM) {
            let firstInitial = baby.name.firstAlphabeticCharacter().uppercased()
            InitialIconView(
                character: firstInitial,
                textColor: theme.backgroundPrimary,
                backgroundColor: theme.statusIconPrimary,
                size: 32,
                style: .fill
            )

            Text(baby.name)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            Spacer()

            Button {
                let itemID = UUID(uuidString: baby.id) ?? UUID()
                guard openItemID != itemID else { return }
                store.editBaby(baby)
            } label: {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 20))
                    .foregroundColor(theme.statusIconPrimary)
            }
        }
        .padding(.spacingSM)
        .frame(height: 72)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundPrimary)
        .swipeableActions(
            buttonWidth: swipeButtonWidth,
            buttons: [
                SwipeButton(
                    tint: theme.textError,
                    action: { store.confirmDeleteBaby(baby) },
                    label: {
                        AnyView(
                            AppIconView(icon: AppAssets.trash, size: IconSize(width: 24, height: 24))
                                .foregroundColor(theme.backgroundPrimary)
                        )
                    }
                )
            ],
            itemID: UUID(uuidString: baby.id) ?? UUID(),
            openItemID: $openItemID,
            openThresholdFraction: 0.1,
            closeWithoutAnimationOnAction: true,
            trailingCornerRadius: .radiusSM
        )
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
                    },
                    onLeadingTap: { store.isShowingAddBaby = false },
                    canShowBorder: true
                )

                BabyProfileFormView(
                    form: store.babyProfileForm,
                    showDatePicker: $store.showBabyDatePicker,
                    showSexPicker: $store.showBabySexPicker,
                    hideHeader: true
                )
                    .padding(.horizontal, .spacingSM)
            }
            .background(theme.backgroundSecondary.ignoresSafeArea())
        }
        .presentationDragIndicator(.visible)
    }
}
