//
//  AdditionalSettingsViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/09/25.
//
import SwiftUI
import SwiftData

// MARK: - AdditionalSettingsViewModel
@MainActor
final class AdditionalSettingsViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector var logger: LoggerService
    @Injector var accountService: AccountService

    // Store the device ID for safe refetching from MainActor context
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String

    // Cached scale for fallback when model not found in context
    private var cachedScale: Device?

    // Returns the cached scale - use refreshScale() to update from database
    var scale: Device {
        if let cached = cachedScale {
            return cached
        }
        logger.log(level: .error, tag: tag, message: "No cached scale available")
        return Device(id: "", accountId: "", deviceName: "Error", deviceType: "")
    }

    /// Refreshes the scale from the database. Call this before operations that need fresh data.
    func refreshScale() {
        // First try registeredModel for already-loaded models (fastest path)
        if let freshScale: Device = PersistenceController.shared.context.registeredModel(for: scaleId) {
            cachedScale = freshScale
            return
        }

        // If not in identity map, fetch from persistent store using FetchDescriptor
        let idToFind = scaleIdString
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { device in
                device.id == idToFind
            }
        )
        do {
            let results = try PersistenceController.shared.context.fetch(descriptor)
            if let freshScale = results.first {
                cachedScale = freshScale
                return
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch scale from store: \(error.localizedDescription)")
        }

        // Keep existing cached value if fetch failed
        if cachedScale != nil {
            logger.log(level: .debug, tag: tag, message: "Using existing cached scale after refresh failed")
        }
    }

    @Published var deviceInfo: DeviceInfo? = nil
    @Published var isDeviceConnected: Bool = false
    @Published var isWifiConfigured: Bool = false
    @Published var startAnimationEnabled: Bool = false
    @Published var endAnimationEnabled: Bool = false

    private let tag = "AdditionalSettingsViewModel"

    init(scale: Device) {
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale
        self.isDeviceConnected = scale.isConnected ?? false
        self.isWifiConfigured = scale.isWifiConfigured ?? false
    }

    func load() async {
        await getDeviceInfo()
    }

    func getDeviceInfo() async {
        guard isDeviceConnected else { return }
        refreshScale()
        let result = await bluetoothService.getDeviceInfo(for: scale)
        switch result {
        case .success(let info):
            deviceInfo = info
            startAnimationEnabled = info.startAnimationState ?? false
            endAnimationEnabled = info.endAnimationState ?? false
            isWifiConfigured = info.isWifiConfigured ?? isWifiConfigured
            logger.log(level: .info, tag: tag, message: "Fetched device info")
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed device info: \(error.localizedDescription)")
        }
    }

    func setStartAnimation(_ enabled: Bool) async {
        guard isDeviceConnected else { return }
        refreshScale()
        let setting = DeviceSetting(key: "INITIAL_LOGO_ANIM", value: .bool(enabled))
        let res = await bluetoothService.updateSetting(on: scale, settings: [setting])
        if case .success = res { startAnimationEnabled = enabled } else {
            if case .failure(let err) = res { logger.log(level: .error, tag: tag, message: "Start anim failed: \(err.localizedDescription)") }
        }
    }

    func setEndAnimation(_ enabled: Bool) async {
        guard isDeviceConnected else { return }
        refreshScale()
        let setting = DeviceSetting(key: "FINAL_LOGO_ANIM", value: .bool(enabled))
        let res = await bluetoothService.updateSetting(on: scale, settings: [setting])
        if case .success = res { endAnimationEnabled = enabled } else {
            if case .failure(let err) = res { logger.log(level: .error, tag: tag, message: "End anim failed: \(err.localizedDescription)") }
        }
    }

    func setTimeFormat(_ format: String) async {
        guard isDeviceConnected else { return }
        refreshScale()
        let deviceId = scaleIdString
        let res = await bluetoothService.updateSetting(on: scale, settings: [DeviceSetting(key: "TIME_FORMAT", value: .string(format))])
        switch res {
        case .success:
            refreshScale()
            // R9: Use DTO to update preference instead of mutating @Model directly after await
            if let preference = scale.r4ScalePreference {
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
        guard isDeviceConnected else { return }
        refreshScale()
        notificationService.showLoader(LoaderModel(text: LoaderStrings.pleaseWait))
        let res = await bluetoothService.clearData(on: scale, dataType: type)
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
        guard isDeviceConnected else { return }
        refreshScale()
        let res = await bluetoothService.updateSetting(on: scale, settings: [DeviceSetting(key: "RESET_FIRMWARE", value: .bool(true))])
        switch res {
        case .success:
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: "Firmware reset requested"))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Reset firmware failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
    }

    func restoreFactorySettings() async {
        guard isDeviceConnected else { return }
        refreshScale()
        let res = await bluetoothService.updateSetting(on: scale, settings: [DeviceSetting(key: "RESTORE_FACTORY", value: .bool(true))])
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
            presentedView: AnyView(ModelNumberHelpModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
}
