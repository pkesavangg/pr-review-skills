//  ScaleSettingsStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import Foundation
import SwiftUI
import Combine

@MainActor
class ScaleSettingsStore: ObservableObject {
    // MARK: - Published State
    @Published var scale: Device? = nil
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var firmwareVersion: String? = nil
    @Published var macAddress: String? = nil
    @Published var connectedWifiSSID: String? = nil
    @Published var deviceInfo: DeviceMetaData? = nil
    @Published var isImpedanceSwitchedOnForSession: Bool = false
    @Published var isScaleImpedanceSwitchedOn: Bool = false
    @Published var isFromScaleSetup: Bool = false
    @Published var isDeveloperMode: Bool = false
    @Published var isBodyMetrics: Bool = false
    @Published var isBluetoothScale: Bool = false
    @Published var isDeviceConnected: Bool = false
    @Published var canEnableTestingFeatures: Bool = false
    @Published var canShowDownloadLogOption: Bool = false
    @Published var showEditError: Bool = false
    @Published var showDeleteConfirm: Bool = false
    @Published var showFactoryResetConfirm: Bool = false
    @Published var showFirmwareUpdateConfirm: Bool = false
    @Published var showPermissionModal: Bool = false
    @Published var showModeModal: Bool = false
    @Published var showSettingModal: Bool = false
    @Published var showWifiSettingModal: Bool = false
    @Published var showAdditionalSettingsModal: Bool = false
    @Published var showSoftwareUpdateModal: Bool = false
    @Published var showMacModal: Bool = false
    @Published var showScaleUsers: Bool = false
    @Published var showErrorToast: ToastModel? = nil
    @Published var showNicknameAlert: Bool = false
    @Published var nicknameInput: String = ""

    // MARK: - Dependencies
    @Injector var scaleService: ScaleService
    @Injector var notificationService: NotificationHelperService
    // Add other dependencies as needed

    // MARK: - Actions (Migrated from Angular)
    func loadScale(_ scale: Device) async {
        self.scale = scale
        self.isBluetoothScale = scale.deviceType == "bluetooth"
        self.isDeviceConnected = scale.isConnected ?? false
        // Load additional info as needed
        await getDeviceInfo()
        await getConnectedWifiSSID()
    }

    func getDeviceInfo() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            if let device = try? await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                self.deviceInfo = device.metaData
                self.macAddress = device.mac
                self.firmwareVersion = device.metaData?.firmwareRevision
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func getConnectedWifiSSID() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        // Use wifiMac from Device as SSID equivalent (since DeviceMetaData has no wifiSSID)
        self.connectedWifiSSID = scale.wifiMac
    }

    func openLink(sku: String) {
        // TODO: Open product URL in browser
    }

    func startDeleteScale() {
        // TODO: Show delete confirmation modal
        showDeleteConfirm = true
    }

    func changeNickname() {
        showNicknameAlert = true
        nicknameInput = scale?.nickname ?? ""
    }

    func saveNickname() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            // Update nickname via PATCH /paired-scale/:id
            let properties: [String: Any] = ["nickname": nicknameInput]
            _ = try await scaleService.editDevice(scale.id, properties: properties)
            notificationService.showToast(ToastModel(title: "Saved", message: "Nickname updated"))
            self.scale?.nickname = nicknameInput
        } catch {
            errorMessage = error.localizedDescription
        }
        showNicknameAlert = false
    }

    func openPermissionModal() {
        // TODO: Present permission modal
        showPermissionModal = true
    }

    func openModeModal() {
        // TODO: Present mode modal
        showModeModal = true
    }

    func openSettingModal() {
        // TODO: Present settings modal
        showSettingModal = true
    }

    func openWifiSettingModal() {
        // TODO: Present WiFi settings modal
        showWifiSettingModal = true
    }

    func openAdditionalSettingsModal() {
        // TODO: Present additional settings modal
        showAdditionalSettingsModal = true
    }

    func openSoftwareUpdateModal() {
        // TODO: Present software update modal
        showSoftwareUpdateModal = true
    }

    func openMacModal() {
        // TODO: Present MAC address modal
        showMacModal = true
    }

    func openScaleUsers() {
        // TODO: Present scale users modal
        showScaleUsers = true
    }

    func factoryResetHandler() {
        // TODO: Show factory reset confirmation modal
        showFactoryResetConfirm = true
    }

    func confirmFactoryReset() async {
        // TODO: Call factory reset logic
    }

    func startFirmwareUpdate() {
        // TODO: Show firmware update confirmation modal
        showFirmwareUpdateConfirm = true
    }

    func confirmFirmwareUpdate() async {
        // TODO: Call firmware update logic
    }

    // MARK: - Bluetooth/Device Actions (stubs, implement with real BluetoothService if available)
    func fetchWifiMacAddress() async {
        // TODO: Integrate with BluetoothService if available
        isLoading = true
        defer { isLoading = false }
        do {
            // Simulate fetching WiFi MAC address
            self.macAddress = scale?.mac ?? "00:00:00:00:00:00"
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func getWifiMacAddress() async {
        await fetchWifiMacAddress()
        showMacModal = true
    }

    func factoryResetScale() async {
        isLoading = true
        defer { isLoading = false }
        do {
            // TODO: Integrate with BluetoothService for factory reset
            // Simulate success
            notificationService.showToast(ToastModel(title: "Factory Reset", message: "Scale reset to factory settings"))
        } catch {
            errorMessage = error.localizedDescription
        }
        showFactoryResetConfirm = false
    }

    func firmwareUpdateScale() async {
        isLoading = true
        defer { isLoading = false }
        do {
            // TODO: Integrate with BluetoothService for firmware update
            // Simulate success
            notificationService.showToast(ToastModel(title: "Firmware Update", message: "Firmware update started"))
        } catch {
            errorMessage = error.localizedDescription
        }
        showFirmwareUpdateConfirm = false
    }

    func openMacModalFlow() async {
        await getWifiMacAddress()
    }

    func openScaleUsersFlow() async {
        isLoading = true
        defer { isLoading = false }
        do {
            // TODO: Integrate with BluetoothService to fetch user list
            // Simulate user list fetch
            // Present modal in UI
            showScaleUsers = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func enableBodyMetricsForSession() async {
        // Show alert and enable body metrics for session
        // TODO: Integrate with BluetoothService if needed
        isImpedanceSwitchedOnForSession = true
        notificationService.showToast(ToastModel(title: "Body Metrics Enabled", message: "Body metrics enabled for this session"))
    }

    func toggleSessionImpedance(_ isOn: Bool) async {
        isImpedanceSwitchedOnForSession = isOn
        // TODO: Integrate with BluetoothService to update setting
        notificationService.showToast(ToastModel(title: "Impedance", message: isOn ? "Impedance enabled" : "Impedance disabled"))
    }

    // MARK: - Modal/Alert Flows
    func openDeleteScaleAlert() {
        // TODO: Show delete confirmation alert
        showDeleteConfirm = true
    }
    func openFactoryResetAlert() {
        // TODO: Show factory reset confirmation alert
        showFactoryResetConfirm = true
    }
    func openFirmwareUpdateAlert() {
        // TODO: Show firmware update confirmation alert
        showFirmwareUpdateConfirm = true
    }
    func openNicknameAlert() {
        // TODO: Show nickname edit alert
        showNicknameAlert = true
        nicknameInput = scale?.nickname ?? ""
    }
}

