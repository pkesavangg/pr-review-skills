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
                    // Current User section
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.userNameLabel)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        
                        ListItemView(
                            title: scaleStore.currentUser,
                            trailing: Button(action: { scaleStore.deleteCurrentUser() }) {
                                Image(AppAssets.closeCircle)
                                    .foregroundColor(theme.actionPrimary)
                            },
                            rowHeight: 56,
                            onTap: { scaleStore.deleteCurrentUser() }
                        )
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.radiusXS)
                    }
                    .padding(.top, .spacingMD)
                    
                    // Other Users section
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.otherUsersSection)
                            .fontOpenSans(.heading5)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)
                        
                        Text(lang.maxUsers)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        
                        if !scaleStore.otherUsers.isEmpty {
                            DeviceUserListView(
                                users: scaleStore.otherDeviceUsers,
                                onDeleteUser: { index in
                                    // TODO: Need to handle deletion of other users
                                    // scaleStore.deleteOtherUser(at: index)
                                }
                            )
                        }
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
