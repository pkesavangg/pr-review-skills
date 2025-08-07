//
//  ScaleNameScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ScaleNameScreen : View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let scale: Device
    let lang = ScaleSettingsStrings.self
    let commonLang = CommonStrings.self

    @State private var editedName: String = ""
    @State private var focusedField: FocusField? = nil
    @StateObject private var scaleNameForm = ScaleNameForm()
    @StateObject private var viewModel: ScaleNameViewModel

    init(scale: Device) {
        self.scale = scale
        _viewModel = StateObject(wrappedValue: ScaleNameViewModel(scale: scale))
    }

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.scaleName,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: commonLang.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !scaleNameForm.isValid || editedName.trimmingCharacters(in: .whitespacesAndNewlines) == (scale.nickname ?? scale.deviceName ?? ""),
                        action: {
                            Task {
                                let trimmedName = editedName.trimmingCharacters(in: .whitespacesAndNewlines)
                                await viewModel.saveScaleName(trimmedName) {
                                    router.navigateBack()
                                }
                            }
                        }
                    ))
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            VStack(spacing: .spacingMD) {
                AppInputField(
                    config: TextInputConfig(
                        label: lang.scaleName,
                        placeholder: lang.scaleName,
                        inputType: .text,
                        errorMessage: scaleNameForm.getError(for: .scaleName),
                        focusField: .scaleName
                    ),
                    value: $editedName,
                    focusedField: $focusedField
                ) {
                    // Optional: handle commit
                }
                .onChange(of: editedName) {
                    scaleNameForm.setScaleName(editedName)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingMD)

                Spacer()
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            editedName = scale.nickname ?? scale.deviceName ?? ""
            scaleNameForm.setScaleName(editedName)
        }
    }
}


#Preview{
    let mockDevice = Device(
        id: "1",
        accountId: "demo-account",
        sku: "0412",
        deviceName: "AccuCheck Verve Smart Scale"
    )
    ScaleNameScreen(scale: mockDevice)
}

import SwiftUI

@MainActor
final class ScaleNameViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService
    
    private let scale: Device
    private let tag = "ScaleNameViewModel"
    
    init(scale: Device) {
        self.scale = scale
    }
    
    func saveScaleName(_ newName: String, onSuccess: (() -> Void)? = nil) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        do {
            _ = try await scaleService.editDevice(scale.id, properties: ["nickname": newName])
            await scaleService.pushLocalChangesToServer()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.scaleNameUpdated))
            logger.log(level: .info, tag: tag, message: "Scale name updated successfully", data: ["scaleId": scale.id, "newName": newName])
            onSuccess?()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save scale name: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.errorEditingScale, message: ToastStrings.restartApp))
        }
        notificationService.dismissLoader()
    }
}
