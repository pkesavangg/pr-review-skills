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
    @StateObject private var userNameForm = UserNameForm()
    @StateObject private var viewModel: UsersViewModel
    @State private var focusedField: FocusField?
    let scale: Device
    let usersList: [DeviceUser]
    let lang = UsersViewStrings.self
    let loaderLang = LoaderStrings.self
    
    init(scale: Device, usersList: [DeviceUser]) {
        self.scale = scale
        self.usersList = usersList
        _viewModel = StateObject(wrappedValue: UsersViewModel(scale: scale, initialUsersList: usersList))
    }
    
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
                            isDisabled: viewModel.isLoadingUsers ||
                            !userNameForm.displayName.isValid ||
                            userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines) == (viewModel.currentDeviceUser?.name ?? ""),
                            action: {
                                Task {
                                    let trimmedName = userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
                                    await viewModel.saveUsers(newName: trimmedName) {
                                        router.navigateBack()
                                    }
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
                                placeholder: viewModel.currentDeviceUser?.name ?? lang.enterUserName,
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
                            if viewModel.isLoadingUsers {
                                ProgressView()
                                    .scaleEffect(1.5)
                                    .padding(.vertical, 50)
                                    .frame(maxWidth: .infinity)
                            } else if !viewModel.otherDeviceUsersList.isEmpty {
                                Text(lang.otherUsersSection)
                                    .fontOpenSans(.heading5)
                                    .fontWeight(.bold)
                                    .foregroundColor(theme.textHeading)
                                
                                Text(lang.maxUsers)
                                    .fontOpenSans(.subHeading2)
                                    .foregroundColor(theme.textSubheading)
                                
                                DeviceUserListView(
                                    users: viewModel.otherDeviceUsersList,
                                    onDeleteUser: { user in
                                        viewModel.showDeleteUserAlert(for: user) {
                                            // Alert dismissed, no additional action needed
                                        }
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
        .onAppear {
            Task {
                await viewModel.loadUsers()
                await MainActor.run {
                    let currentName = viewModel.currentDeviceUser?.name ?? ""
                    userNameForm.setDisplayName(currentName)
                    let scaleUsers = viewModel.otherDeviceUsersList.map { deviceUser in
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
    UsersScreen(
        scale: Device(
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
        ),
        usersList: [
            DeviceUser(name: "John Doe", token: "token1", lastActive: 1234567890, isBodyMetricsEnabled: true),
            DeviceUser(name: "Jane Smith", token: "token2", lastActive: 1234567800, isBodyMetricsEnabled: false)
        ]
    )
}

@MainActor
final class UsersViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var bluetoothService: BluetoothService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService
    
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false
    
    private let scale: Device
    private let tag = "UsersViewModel"
    
    var otherDeviceUsersList: [DeviceUser] {
        return deviceUsers.filter { $0.token != currentDeviceUser?.token }
    }
    
    init(scale: Device, initialUsersList: [DeviceUser] = []) {
        self.scale = scale
        if !initialUsersList.isEmpty {
            self.deviceUsers = initialUsersList
            self.currentDeviceUser = initialUsersList.first
        }
    }
    
    func loadUsers() async {
        // If users were already provided during initialization, don't fetch again
        guard deviceUsers.isEmpty else {
            logger.log(level: .info, tag: tag, message: "Users already loaded from initial list")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Scale is not connected, cannot load users")
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale)
        
        await MainActor.run {
            switch result {
            case .success(let users):
                self.deviceUsers = users
                // Find current user (typically the first one or the one that matches our account)
                self.currentDeviceUser = users.first
                logger.log(level: .info, tag: tag, message: "Successfully loaded \(users.count) users from scale")
            case .failure(let error):
                logger.log(level: .error, tag: tag, message: "Failed to load users from scale: \(error.localizedDescription)")
                self.deviceUsers = []
                self.currentDeviceUser = nil
            }
            isLoadingUsers = false
        }
    }
    
    func saveUsers(newName: String, onSuccess: (() -> Void)? = nil) async {
        guard !newName.isEmpty else {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: "User name cannot be empty"))
            return
        }
        
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        
        do {
            // Update the current user's name via Bluetooth service
            if currentDeviceUser != nil,
               let preference = scale.r4ScalePreference {
                preference.displayName = newName
                try await scaleService.updateScalePreference(
                    scale.id,
                    preference
                )
                await scaleService.pushLocalChangesToServer()
                let result = await bluetoothService.updateAccount(on: scale, preference: preference)
                switch result {
                case .success(_):
                    currentDeviceUser?.name = newName
                    notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userNameUpdated))
                    logger.log(level: .info, tag: tag, message: "User name updated successfully", data: ["scaleId": scale.id, "newName": newName])
                    onSuccess?()
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to update user name: \(error.localizedDescription)")
                    notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
                }
            } else {
                logger.log(level: .error, tag: tag, message: "No current user or preference found for scale")
                notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save user name: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
        }
        
        notificationService.dismissLoader()
    }
    
    func showDeleteUserAlert(for user: DeviceUser, onDelete: @escaping () -> Void) {
        let alert = AlertModel(
            title: AlertStrings.DeleteUserAlert.title(user.name),
            message: AlertStrings.DeleteUserAlert.message(user.name),
            buttons: [
                AlertButtonModel(title: AlertStrings.DeleteUserAlert.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: AlertStrings.DeleteUserAlert.removeButton, type: .primary) { _ in
                    Task {
                        await self.deleteUser(user)
                        onDelete()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    private func deleteUser(_ user: DeviceUser) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        
        
        let result = await bluetoothService.deleteDevice(scale, disconnect: false)
        switch result {
        case .success(_):
            deviceUsers.removeAll { $0.token == user.token }
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userDeleted))
            logger.log(level: .info, tag: tag, message: "User deleted successfully", data: ["userName": user.name])
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to delete user: \(error.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorDeletingUser))
        }
        notificationService.dismissLoader()
    }
}
