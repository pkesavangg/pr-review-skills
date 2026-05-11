//
//  AdditionalSettingsViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/09/25.
//
import SwiftUI

// MARK: - AdditionalSettingsViewModel
@MainActor
final class AdditionalSettingsViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var accountService: AccountServiceProtocol

    private let scaleIdString: String

    /// Reads the current snapshot directly from the service — the single source of truth.
    var deviceSnapshot: DeviceSnapshot? {
        scaleService.scales.first(where: { $0.id == scaleIdString })
    }

    @Published var deviceInfo: DeviceInfo?
    @Published var startAnimationEnabled: Bool = false
    @Published var endAnimationEnabled: Bool = false

    var isDeviceConnected: Bool { deviceSnapshot?.isConnected ?? false }
    var isWifiConfigured: Bool { deviceSnapshot?.isWifiConfigured ?? false }
    var timeFormat: String { deviceSnapshot?.r4ScalePreference?.timeFormat ?? "12" }

    private let tag = "AdditionalSettingsViewModel"

    init(scale: Device) {
        self.scaleIdString = scale.id
    }

    func load() async {
        await getDeviceInfo()
    }

    func getDeviceInfo() async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let result = await bluetoothService.getDeviceInfo(broadcastId: broadcastId)
        switch result {
        case .success(let info):
            deviceInfo = info
            startAnimationEnabled = info.startAnimationState ?? false
            endAnimationEnabled = info.endAnimationState ?? false
            logger.log(level: .info, tag: tag, message: "Fetched device info")
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed device info: \(error.localizedDescription)")
        }
    }

    func setStartAnimation(_ enabled: Bool) async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let setting = DeviceSetting(key: "INITIAL_LOGO_ANIM", value: .bool(enabled))
        let res = await bluetoothService.updateSetting(broadcastId: broadcastId, settings: [setting])
        if case .success = res { startAnimationEnabled = enabled } else {
            if case .failure(let err) = res { logger.log(level: .error, tag: tag, message: "Start anim failed: \(err.localizedDescription)") }
        }
    }

    func setEndAnimation(_ enabled: Bool) async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let setting = DeviceSetting(key: "FINAL_LOGO_ANIM", value: .bool(enabled))
        let res = await bluetoothService.updateSetting(broadcastId: broadcastId, settings: [setting])
        if case .success = res { endAnimationEnabled = enabled } else {
            if case .failure(let err) = res { logger.log(level: .error, tag: tag, message: "End anim failed: \(err.localizedDescription)") }
        }
    }

    func setTimeFormat(_ format: String) async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let deviceId = scaleIdString
        let res = await bluetoothService.updateSetting(broadcastId: broadcastId, settings: [DeviceSetting(key: "TIME_FORMAT", value: .string(format))])
        switch res {
        case .success:
            if let preference = deviceSnapshot?.r4ScalePreference {
                var dto = preference.toDTO()
                dto.timeFormat = (format == "12H") ? "12" : "24"
                try? await scaleService.updateScalePreference(deviceId, fromDTO: dto)
            }
            notificationService.showToast(ToastModel(title: ToastStrings.saved, message: "Time format updated"))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Time format failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
    }

    func clearData(_ type: DeviceClearType) async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        notificationService.showLoader(LoaderModel(text: LoaderStrings.pleaseWait))
        let res = await bluetoothService.clearData(broadcastId: broadcastId, dataType: type)
        switch res {
        case .success:
            notificationService.showToast(ToastModel(title: ToastStrings.deleted, message: "Data cleared"))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Clear data failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
        notificationService.dismissLoader()
    }

    func resetFirmware() async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let res = await bluetoothService.updateSetting(broadcastId: broadcastId, settings: [DeviceSetting(key: "RESET_FIRMWARE", value: .bool(true))])
        switch res {
        case .success:
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: "Firmware reset requested"))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Reset firmware failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
    }

    func restoreFactorySettings() async {
        guard isDeviceConnected, let broadcastId = deviceSnapshot?.broadcastIdString else { return }
        let res = await bluetoothService.updateSetting(broadcastId: broadcastId, settings: [DeviceSetting(key: "RESTORE_FACTORY", value: .bool(true))])
        switch res {
        case .success:
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: "Factory reset requested"))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Restore factory failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
    }

    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(ModelNumberHelpModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
}
