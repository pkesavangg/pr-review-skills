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
    @EnvironmentObject var scaleStore: ScaleStore
    @StateObject private var userNameForm = UserNameForm()
    @State private var focusedField: FocusField?
    let scale: Device
    let lang = UsersViewStrings.self
    let loaderLang = LoaderStrings.self
    


    var body: some View {
        ZStack {
            VStack(alignment: .center, spacing: 0) {
                NavbarHeaderView(
                    title: lang.usersTitle,
                    leadingContent: { Image(AppAssets.chevronLeft) },
                    trailingContent: {
                        AnyView(ButtonView(
                            text: CommonStrings.save.uppercased(),
                            type: .inlineTextPrimary,
                            size: .small,
                            isDisabled: scaleStore.isLoadingUsers ||
                                !userNameForm.displayName.isValid ||
                                userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines) == (scaleStore.currentDeviceUser?.name ?? ""),
                            action: {
                                Task {
                                    await scaleStore.saveUsers(newName: userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines))
                                    router.navigateBack()
                                }
                            }
                        ))
                    },
                    onLeadingTap: { router.navigateBack() },
                    canShowBorder: true
                )
                
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: .spacingMD) {
                        // Current User section
                        AppInputField(
                            config: TextInputConfig(
                                label: lang.userNameLabel,
                                placeholder: scaleStore.currentDeviceUser?.name ?? lang.enterUserName,
                                inputType: .text,
                                errorMessage: userNameForm.getError(for: userNameForm.displayName),
                                focusField: .userName
                            ),
                            value: Binding(
                                get: { userNameForm.displayName.value },
                                set: { userNameForm.displayName.value = $0 }
                            ),
                            focusedField: $focusedField
                        ) {
                            // Handle commit action if needed
                        }
                        .padding(.top, .spacingLG)
                                                
                        // Other Users section
                        VStack(alignment: .leading, spacing: .spacingXS) {
                            Text(lang.otherUsersSection)
                                .fontOpenSans(.heading5)
                                .fontWeight(.bold)
                                .foregroundColor(theme.textHeading)
                            
                            Text(lang.maxUsers)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                            
                            if scaleStore.isLoadingUsers {
                                ProgressView()
                                    .scaleEffect(1.5)
                                    .padding(.vertical, 50)
                                    .frame(maxWidth: .infinity)
                            } else if !scaleStore.otherDeviceUsersList.isEmpty {
                                DeviceUserListView(
                                    users: scaleStore.otherDeviceUsersList,
                                    onDeleteUser: { user in
                                        scaleStore.showDeleteUserAlert(for: user) {
                                            // Alert dismissed, no additional action needed
                                        }
                                    }
                                )
                            } else {
                                Text(lang.noOtherUsers)
                                    .fontOpenSans(.body1)
                                    .foregroundColor(theme.textSubheading)
                                    .padding(.vertical, .spacingMD)
                                    .frame(maxWidth: .infinity)
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
        .onAppear {
            Task {
                await scaleStore.loadScale(scale)
                await MainActor.run {
                    let currentName = scaleStore.currentDeviceUser?.name ?? ""
                    userNameForm.setDisplayName(currentName)
                    let scaleUsers = scaleStore.otherDeviceUsersList.map { deviceUser in
                        ScaleUser(name: deviceUser.name, token: deviceUser.token)
                    }
                    userNameForm.updateUserList(scaleUsers)
                }
            }
        }
    }
    
    private func formatLastActiveTimestamp(_ timestamp: Int) -> String {
        return DateTimeTools.getFormattedDateFromTimestamp(Int64(timestamp)).toLowerCase()
    }
}

#Preview{
    UsersScreen(scale: Device(
        id: "preview-scale",
        accountId: "preview-account",
        nickname: "Preview Scale",
        sku: "0412",
        mac: "00:00:00:00:00:00",
        password: 3692707582,
        deviceName: "Preview Scale",
        deviceType: "bluetooth",
        broadcastId: 123456,
        broadcastIdString: "123456",
        userNumber: "1",
        protocolType: "R4",
        createdAt: "2024-01-01T00:00:00Z",
        isConnected: true,
        wifiMac: nil,
        token: "preview-token",
        bathScale: nil,
        r4ScalePreference: nil,
        metaData: nil  
    ))
}
