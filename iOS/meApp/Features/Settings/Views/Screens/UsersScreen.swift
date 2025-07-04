//
//  UsersScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct UsersScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var scaleStore = ScaleStore()
    let lang = UsersViewStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.usersTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: { scaleStore.saveUsers() }
                    ))
                },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )
            
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingMD) {
                    ListItemView(
                        title: scaleStore.currentUser,
                        subtitleTop: lang.userNameLabel,
                        trailing: Button(action: { scaleStore.deleteCurrentUser() }) {
                            Image(AppAssets.closeCircle)
                                .foregroundColor(theme.actionPrimary)
                        },
                        rowHeight: 56,
                        onTap: { scaleStore.deleteCurrentUser() }
                    )
                    .cornerRadius(.spacingXS)
                    .padding(.top, .spacingMD)
                    
                    VStack(alignment: .leading){
                        // Other Users section
                        Text(lang.otherUsersSection)
                            .fontOpenSans(.heading5)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)
                        
                        Text(lang.maxUsers)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.bottom, .radiusXS)
                        
                        VStack(spacing: 2) {
                            ForEach(scaleStore.otherUsers.indices, id: \.self) { idx in
                                ListItemView(
                                    title: scaleStore.otherUsers[idx],
                                    subtitle: lang.lastActive,
                                    trailing: Button(action: { scaleStore.deleteOtherUser(at: idx) }) {
                                        Image(AppAssets.trash)
                                            .foregroundColor(theme.actionPrimary)
                                    },
                                    rowHeight: 56,
                                    onTap: { scaleStore.deleteOtherUser(at: idx) },
                                    verticalPadding: .spacingXS
                                )
                                if idx < scaleStore.otherUsers.count - 1 {
                                    Divider()
                                        .background(theme.statusUtilityPrimary)
                                }
                            }
                        }
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.radiusXS)
                    }
                }
                .padding(.horizontal, .spacingSM)
                .frame(maxWidth: .infinity, alignment: .top)
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .background(theme.backgroundSecondary.ignoresSafeArea())
            .navigationBarBackButtonHidden(true)
        }
    }
}

#Preview{
    UsersScreen()
}
