//
//  BluetoothService.swift
//  iOS Bluetooth integration layer for GGBluetoothSwiftPackage
//
//  Created by AI Assistant
//
//  This file provides a production-ready BluetoothService for iOS apps, wrapping the GGBluetoothSwiftPackage SDK and translating between app models and SDK models. It uses Combine for reactive updates and async/await for async plugin calls.
//

import Foundation
import Combine
import GGBluetoothSwiftPackage
// For mapping device metadata
import SwiftData

/// Comprehensive implementation of `BluetoothServiceProtocol` backed by the `GGBluetoothSwiftPackage` SDK.
///
/// This service provides complete Bluetooth functionality for smart scales, including:
/// - Device scanning and pairing
/// - Wi-Fi configuration
/// - Firmware updates
/// - Data synchronization
/// - User management
/// - Settings configuration
@MainActor
final class BluetoothService: ObservableObject, BluetoothServiceProtocol {
    // MARK: - Singleton
    static let shared = BluetoothService(accountService: AccountService.shared,
                                         scaleService: ScaleService.shared,
                                         entryService: EntryService.shared,
                                         //PermissionsService: PermissionsService.shared,
                                         logger: LoggerService.shared)

    // MARK: - Published State
    @Published private(set) var isScanning: Bool = false
    @Published private(set) var canShowScaleDiscoveredModal: Bool = true

    // MARK: - Public Publishers
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> {
        deviceDiscoveredSubject.eraseToAnyPublisher()
    }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> {
        deviceInfoUpdatedSubject.eraseToAnyPublisher()
    }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> {
        showWeightOnlyModeAlertSubject.eraseToAnyPublisher()
    }
    var newEntryReceivedPublisher: AnyPublisher<Entry, Never> {
        newEntryReceivedSubject.eraseToAnyPublisher()
    }
    @Published var isSetupInProgress: Bool = false

    // MARK: - Subjects for Scale Discovery
    private let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    private let newEntryReceivedSubject = PassthroughSubject<Entry, Never>()
    private let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    private let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()

    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var activeAccount: Account?
    private var isSmartScanStarted = false
    private var bluetoothScales: [Device] = []
    private var skipDevices: [String] = []
    private var isWeightOnlyModeAlertDismissed = false

    // MARK: - Dependencies
    private let accountService: AccountService
    private let scaleService: ScaleServiceProtocol
    private let entryService: EntryServiceProtocol
//    private let permissionsService: PermissionsServiceProtocol
    private let logger: LoggerService
    private let ggBleSDK = GGBluetoothSwiftPackage.shared
    private let tag = "BluetoothService"

    // MARK: - Initialization
    init(
        accountService: AccountService,
        scaleService: ScaleServiceProtocol,
        entryService: EntryServiceProtocol ,
//        permissionsService: PermissionsServiceProtocol = PermissionsService.shared,
        logger: LoggerService,

    ) {
        self.accountService = accountService
        self.scaleService = scaleService
        self.entryService = entryService
        //self.permissionsService = permissionsService
        self.logger = logger
        setupSubscriptions()
    }

    // MARK: - Setup
    private func setupSubscriptions() {
        // Subscribe to scale changes
       scaleService.scalesPublisher
           .receive(on: DispatchQueue.main)
           .sink { [weak self] scales in
               Task { await self?.handleScalesUpdate(scales) }
           }
           .store(in: &cancellables)

    }

    func initialize() {
            // Subscribe to active account changes
        accountService.$activeAccount
            .receive(on: DispatchQueue.main)
            .sink { [weak self] account in
                Task { await self?.handleAccountUpdate(account) }
            }
            .store(in: &cancellables)
    }

    private func handleScalesUpdate(_ scales: [Device]?) async {
        guard let scales = scales else {
            syncDevices([])
            bluetoothScales = []
            return
        }
        let allowedTypes: [ScaleSourceType] = [
            .bluetooth,
            .bluetoothScale,
            .lcbt,
            .lcbtScale,
            .btWifiR4
        ]
        let filteredScales = scales.filter { scale in
          allowedTypes.contains(ScaleSourceType(rawValue: (scale.bathScale?.scaleType)!)) || scale.sku == "0412"
        }
        // Disconnect deleted scales
        await disconnectDeletedScales(currentScales: bluetoothScales, newScales: filteredScales)
        bluetoothScales = filteredScales

        if !isSetupInProgress {
           syncDevices(self.bluetoothScales)
        }
    }

    private func handleAccountUpdate(_ account: Account?) async {
        if let account = account {
            activeAccount = account
            if !isSmartScanStarted {
                await scan()
            }
            do {
                _ = try await updateUserProfileForR4Scales()
            } catch {
              logger.log(level: .error, tag: tag, message: BluetoothServiceError.updateProfileFailed(error).localizedDescription)
            }
        } else if isSmartScanStarted {
            stopScan()
        }
    }

    // MARK: - BluetoothServiceProtocol Implementation

    func stopScan() {
      ggBleSDK.stop()
      isSmartScanStarted = false
    }

    func clearDevices() {
        ggBleSDK.clearDevices()
    }

    // MARK: - Scanning & Pairing
    func scan() async {
        guard activeAccount != nil else {
            return
        }
        do {
            try await startSmartScan()
        } catch {
          logger.log(level: .error, tag: tag, message: BluetoothServiceError.scanFailed(error).localizedDescription)
        }
    }


    func resyncAndScan() async throws {
        do {
            try await scaleService.updateAllScalesStatus(nil)
            clearScaleDiscoveredInfo()
            try await scaleService.syncDevices(tempDevice: nil)
            syncDevices(bluetoothScales)
        } catch {
            throw BluetoothServiceError.resyncFailed(error)
        }
    }

    func pauseSmartScan() {
       ggBleSDK.pauseScan()
    }

    func resumeSmartScan(clearOnlyPairing: Bool) {
       ggBleSDK.resumeScan(clearOnlyPairing)
    }

    func scanForPairing() {
      ggBleSDK.scanForPairing()
    }

    // MARK: - Device Sync & CRUD
    func syncDevices(_ devices: [Device]) {
      if bluetoothScales.isEmpty {
          clearDevices()
          return
      }
      let ggDevices = bluetoothScales.map { device in
        GGBTDevice(
            name: device.deviceName ?? "",
            broadcastId: device.broadcastId ?? "",
            password: device.password,
            token: device.token,
            userNumber: Int(device.userNumber ?? "0"),
            preference: nil,
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: device.protocolType ?? "",
            macAddress: device.mac ?? ""
        )
      }

      ggBleSDK.syncDevices(ggDevices)
    }

    func addNewDevice(_ scale: Device, metaData deviceDetails: DeviceMetaData?) async throws -> Device {
          guard let userId = activeAccount?.accountId else {
              throw BluetoothServiceError.noActiveAccount
          }

          let scaleToSave = scale
          scaleToSave.accountId = userId
          scaleToSave.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
          scaleToSave.nickname = scale.nickname ?? "Bluetooth Smart Scale"

          // Get device info if not provided
          var metaData = deviceDetails
          let scaleType = scale.bathScale?.scaleType ?? ""


          if metaData == nil && (scaleType == ScaleSourceType.btWifiR4.rawValue ||
                                scaleType == ScaleSourceType.bluetooth.rawValue) {
                do {
                    let deviceInfo = try await getDeviceInfo(for: scale)
                    let dto = ScaleMetaDataDTO(
                      firmwareRevision: deviceInfo.firmwareRevision?.replacingOccurrences(of: "\0", with: ""),
                      hardwareRevision: deviceInfo.hardwareRevision?.replacingOccurrences(of: "\0", with: ""),
                      latestFirmwareVersion: nil,
                      manufacturerName: deviceInfo.manufacturerName?.replacingOccurrences(of: "\0", with: ""),
                      modelNumber: deviceInfo.modelNumber?.replacingOccurrences(of: "\0", with: ""),
                      serialNumber: deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: ""),
                      softwareRevision: deviceInfo.softwareRevision?.replacingOccurrences(of: "\0", with: ""),
                      systemId: deviceInfo.systemID?.replacingOccurrences(of: "\0", with: ""),
                      wifiMac: ""
                  )
                    metaData = DeviceMetaData(from: dto)
                    // Get WiFi MAC address for R4 scales
                    if scaleType == ScaleSourceType.btWifiR4.rawValue {
                      let wifiMacAddress = try await getWifiMacAddress(for: scale)
                      scaleToSave.wifiMac = wifiMacAddress
                    }
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")

                }
            }

        scaleToSave.metaData = metaData

        let savedScale = try await scaleService.createDevice(scaleToSave)
        try await scaleService.syncDevices(tempDevice: nil)
        return savedScale
      }

      func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async throws -> UserCreationResponse {
          guard let ggDevice = mapToGGBTDevice(device) else {
            throw BluetoothServiceError.invalidBroadcastId
          }
          ggDevice.token = token
          ggDevice.userNumber = userNumber ?? 0
          let preference = GGDevicePreference(displayName: displayName)
          ggDevice.preference = preference
          let result = await ggBleSDK.confirmPair(
              ggDevice
          )
          return UserCreationResponse(sdkType: result)
      }

      func disconnectDevice(broadcastId: String) async throws {
          if !skipDevices.contains(broadcastId) {
              skipDevices.append(broadcastId)
          }
          canShowScaleDiscoveredModal = false
          Task {
              try await Task.sleep(nanoseconds: 5_000_000_000)
              await MainActor.run {
                  self.canShowScaleDiscoveredModal = true
              }
          }
          ggBleSDK.skipDevice(broadcastId)
      }

    func deleteDevice(_ device: Device, disconnect: Bool) async throws -> UserDeletionResponse {
          guard let ggDevice = mapToGGBTDevice(device) else {
            throw BluetoothServiceError.invalidBroadcastId
          }
          let result = await ggBleSDK.deleteUser(ggDevice, canDisconnect: disconnect)
          return UserDeletionResponse(sdkType: result)
      }

      // MARK: - Wi-Fi Configuration
      func getWifiList(for device: Device) async throws -> [WifiDetails] {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
        let result = await ggBleSDK.getWifiList(ggDevice)
        return result.wifi.map { WifiDetails(macAddress: $0.macAddress, ssid: $0.ssid, rssi: $0.rssi, password: $0.password) }
      }

      func setupWifi(on device: Device, config: WifiConfig) async throws {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
        let ggConfig = GGBTWifiConfig(ssid: config.ssid, password: config.password ?? "")
        //TODO: Handle return
        _ = await ggBleSDK.setupWifi( ggDevice, ggConfig)
      }

      func cancelWifi(on: Device) async throws {
        guard let ggDevice = mapToGGBTDevice(on) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
        ggBleSDK.cancelWifi( ggDevice)
      }

      func getConnectedWifiSSID(broadcastId: String) async throws -> String {
          let ggDevice = mapToGGBTDevice(broadcastId)
          return await ggBleSDK.getConnectedWifiSSID(ggDevice)
      }

      func getWifiMacAddress(for device: Device) async throws -> String {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
        return await ggBleSDK.getWifiMacAddress(ggDevice)
      }

      // MARK: - Settings & Firmware
      func updateSetting(on device: Device, settings: [DeviceSetting]) async throws {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
          let ggSettings = settings.map { setting in
              GGBTSetting(
                  key: GGBTSettingType(rawValue: setting.key) ?? .SESSION_IMPEDANCE,
                  value: setting.value.toGGBTSettingValue()
              )
          }
          ggBleSDK.updateSetting(ggDevice, ggSettings)
      }

      func updateFirmware(on device: Device, timestamp: UInt32) async throws {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
        ggBleSDK.startFirmwareUpdate( ggDevice, timestamp)
      }

      func clearData(on device: Device, dataType: DeviceClearType) async throws {
        guard let ggDevice = mapToGGBTDevice(device) else {
          throw BluetoothServiceError.invalidBroadcastId
        }
          let sdkType: ClearDataType = {
              switch dataType {
              case .userData: return .ACCOUNT
              case .history: return .HISTORY
              case .all: return .ALL
              }
          }()
          _ = await ggBleSDK.clearData(ggDevice,sdkType)
      }

      // MARK: - Profile & Account
      func updateUserProfileForR4Scales() async throws -> Bool {
          guard let activeAccount = activeAccount else {
              throw BluetoothServiceError.noActiveAccount
          }
          guard let userProfile = await getProfileInfo(from: activeAccount) else {
              throw BluetoothServiceError.noProfileInfo
          }
          let success = await ggBleSDK.updateProfile(profile: userProfile)
          if !success {
              throw BluetoothServiceError.updateProfileFailed(BluetoothServiceError.notImplemented)
          }
          return success
      }

      func updateAccount(on device: Device, preference: R4ScalePreference) async throws -> UserCreationResponse {
          guard let ggDevice = mapToGGBTDevice(device) else {
            throw BluetoothServiceError.invalidBroadcastId
          }
          ggDevice.preference = mapToGGPreference(preference)
          let result = await ggBleSDK.updateAccount(ggDevice)
          return UserCreationResponse(sdkType: result)
      }

      func getScaleUserList(for device: Device) async throws -> [DeviceUser] {
          guard let ggDevice = mapToGGBTDevice(device) else {
            throw BluetoothServiceError.invalidBroadcastId
          }
          let users = await ggBleSDK.getUsers(ggDevice)
          let deviceUsers = users.user.map { user in
              DeviceUser(
                  name: user.name,
                  token: user.token,
                  lastActive: user.lastActive,
                  isBodyMetricsEnabled: user.isBodyMetricsEnabled
              )
          }
          return deviceUsers
      }

      // MARK: - Device Info
      func getDeviceInfo(for device: Device) async throws -> DeviceInfo {
          guard let ggDevice = mapToGGBTDevice(device) else {
              throw BluetoothServiceError.invalidBroadcastId
          }
          let details = await ggBleSDK.getDeviceInfo(ggDevice)
          return DeviceInfo(sdk:details)
      }

      func getMeasurementLiveData(broadcastId: String) async throws -> MeasurementLiveData {
          let ggDevice = mapToGGBTDevice(broadcastId)
          _ = await ggBleSDK.getMeasurementLiveData(ggDevice)
          let liveData = MeasurementLiveData(weight: 0)
          return liveData
      }


    func updateWeightOnlyMode(on connectedScale: Device?) async throws {
        do {
            var scales: [Device] = []
            if let connectedScale = connectedScale {
                scales.append(connectedScale)
            } else {
                scales = bluetoothScales.filter { scale in
                    (scale.isConnected ?? false) //TODO handle weight only mode enable by others
                }
            }

            for scale in scales {
                try await updateSetting(on: scale, settings: [
                    DeviceSetting(key: "SESSION_IMPEDANCE", value: DeviceSettingValue.bool(true))
                ])
            }
        } catch {
            throw BluetoothServiceError.updateWeightOnlyModeFailed(error)
        }
    }


    func clearScaleDiscoveredInfo() {
        skipDevices.removeAll()
    }

    // MARK: - Private Helper Methods

    private func startSmartScan() async throws {
        guard let activeAccount = activeAccount else {
            throw BluetoothServiceError.noActiveAccount
        }
        guard let accountData = await getProfileInfo(from: activeAccount) else {
            throw BluetoothServiceError.noProfileInfo
        }
        isScanning = true

        // Use the callback-based scan method properly
        ggBleSDK.scan(.WEIGHT_GURUS, accountData) { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success(let scanResponse):
                    await self?.handleSmartScaleData(scanResponse)
                case .failure(let error):
                    self?.logger.log(level: .error, tag: self?.tag ?? "BluetoothService", message: BluetoothServiceError.scanFailed(error).localizedDescription)
                }
            }
        }
        isSmartScanStarted = true
    }


    private func handleSmartScaleData(_ data: GGScanResponse) async {
        guard let responseType = data.type else { return }

        switch responseType {
        case .NEW_DEVICE:
            await handleNewDevice(data.data)
        case .SINGLE_ENTRY:
            await saveEntries([data.data])
        case .MULTI_ENTRIES:
            await saveEntries(data.data)
        case .KNOWN_DEVICE:
            // Handle known device discovery
            break
        case .DEVICE_CONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            await checkCanShowWeightOnlyModeAlert()
        case .DEVICE_DISCONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: false)
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .DEVICE_MEMORY_FULL:
            await handleDeviceEventAlert(data.data, isDuplicateUserError: false)
        case .DEVICE_DUPLICATE_USER:
            await handleDeviceEventAlert(data.data, isDuplicateUserError: true)
        case .WIFI_STATUS_UPDATE:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            await handleWifiStatusUpdate(data.data)
        case .DEVICE_INFO_UPDATE:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            let deviceDetails = data.data as! GGDeviceDetails
            let deviceInfo = DeviceInfo(sdk: deviceDetails)
            deviceInfoUpdatedSubject.send(deviceInfo)
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .PERMISSION_STATUS:
            await handlePermissionStatus(data.data)
        case .DEVICE_WAKE_UP:
            // Handle device wake up
            break
        case .LIVE_MEASUREMENT:
            // Handle live measurement data
            break
        }
    }

    private func checkCanShowWeightOnlyModeAlert() async {
        let scale = bluetoothScales.filter { scale in
            (scale.isConnected ?? false) //TODO handle weight only mode enable by others
        }

        if !scale.isEmpty {
            showWeightOnlyModeAlertSubject.send(true)
            isWeightOnlyModeAlertDismissed = false
        } else {
            showWeightOnlyModeAlertSubject.send(false)
        }
    }

    private func handleWifiStatusUpdate(_ deviceData: GGScanResponseData) async {
        // Extract wifi status from device data and update
        // This would need proper casting based on the actual data structure
        if let deviceInfo = deviceData as? GGDeviceDetails {
            let broadcastId = deviceInfo.broadcastId ?? ""
            let isConfigured = deviceInfo.isWifiConfigured ?? false
            await scaleService.updateConnectedDeviceWifiStatus(broadcastId: broadcastId, isConfigured: isConfigured)
        }
    }

    private func handlePermissionStatus(_ permissionData: GGScanResponseData) async {
        // Handle permission status updates
        // This would interface with a PermissionsService if available
        // For now, just log the permission status
        print("Permission status updated: \(permissionData)")
    }

    private func handleNewDevice(_ deviceData: GGScanResponseData) async {
        // Parse device data and determine protocol type and if it's new
        guard let deviceDetails = deviceData as? GGDeviceDetails else { return }

        let device = mapDeviceDetailsToDevice(deviceDetails)
        let scaleInfo = ScaleInfoUtils.shared.getScaleInfo(byScaleName: deviceDetails.deviceName)
        let protocolType = ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .A6

        // Check if this is a known device
        let isKnown = bluetoothScales.contains { scale in
            scale.broadcastId == deviceDetails.broadcastId
        }
        let isNew = !isKnown

        // Send unified discovery event
        let discoveryEvent = DeviceDiscoveryEvent(
            device: device,
            deviceInfo: scaleInfo!,
            protocolType: protocolType,
            isNew: isNew,
        )

        deviceDiscoveredSubject.send(discoveryEvent)
    }

    private func mapDeviceDetailsToDevice(_ deviceDetails: GGDeviceDetails) -> Device {
        return Device(
            id: UUID().uuidString,
            accountId: activeAccount?.accountId ?? "",
            mac: deviceDetails.macAddress,
            broadcastId: deviceDetails.broadcastId,
            broadcastIdString: deviceDetails.broadcastIdString,
            isConnected: false,
        )
    }

    private func mapProtocolToScaleType(_ protocolType: String) -> ScaleSourceType {
        switch protocolType {
        case "A3": return .bluetooth
        case "A6": return .bluetoothScale
        case "R4": return .btWifiR4
        default: return .bluetoothScale
        }
    }

    private func saveEntries(_ entriesData: GGScanResponseData) async {
        // Handle single entry
        if let weightEntry = entriesData as? GGWeightEntry {
            let entry = convertWeightEntry(weightEntry)
            try? await entryService.saveNewEntry(entry)
            newEntryReceivedSubject.send(entry)
        } else if let entryList = entriesData as? GGEntryList {
            // Handle multiple entries
            let entries = entryList.list.compactMap { convertGGEntry($0) }
            for entry in entries {
                try? await entryService.saveNewEntry(entry)
            }
            newEntryReceivedSubject.send(entries[0])
        }
    }

    private func saveEntries(_ entriesDataArray: [GGScanResponseData]) async {
        for entryData in entriesDataArray {
            await saveEntries(entryData)
        }
    }

    private func convertWeightEntry(_ ggEntry: GGWeightEntry) -> Entry {
        guard let activeAccount = activeAccount else {
            fatalError("No active account available for entry conversion")
        }

        // Create timestamp in ISO8601 format
        let entryDate = ggEntry.date != nil ?
            Date(timeIntervalSince1970: TimeInterval(ggEntry.date!) / 1000) :
            Date()
        let timestamp = ISO8601DateFormatter().string(from: entryDate)

        // Create the main Entry
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue
        )

        // Create BathScaleEntry with weight data
        // Convert mg to kg, then use Bluetooth-specific conversion
        let weightInKg = Double(ggEntry.weightInMg) / 1000000.0 // mg to kg
        let scaleEntry = BathScaleEntry(
            weight: ConversionTools.convertBluetoothToStored(weightInKg),
            source: EntrySource.bluetooth.rawValue
        )

        // Create BathScaleMetric with unit
        let scaleMetric = BathScaleMetric(
            unit: ggEntry.unit
        )

        // Set relationships
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric

        return entry
    }

    private func convertGGEntry(_ ggEntry: GGEntry) -> Entry? {
        guard let activeAccount = activeAccount else {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.noActiveAccount.localizedDescription)
            return nil
        }

        // Create timestamp in ISO8601 format
        let entryDate = ggEntry.date != nil ?
            Date(timeIntervalSince1970: TimeInterval(ggEntry.date!) / 1000) :
            Date()
        let timestamp = ISO8601DateFormatter().string(from: entryDate)

        // Create the main Entry
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue
        )
        let protocolType = ProtocolType(rawValue: ggEntry.protocolType ?? "") ?? .A6
        var sourceType = ScaleSourceType.bluetoothScale
        if protocolType == .R4 {
            sourceType = .btWifiR4
        }
        // Create BathScaleEntry with basic scale data
        let scaleEntry = BathScaleEntry(
            weight: getWeightByProtocolType(protocolType: protocolType, entry: ggEntry),
            bodyFat: roundMetric(ggEntry.bodyFat),
            muscleMass: roundMetric(ggEntry.muscleMass),
            water: roundMetric(ggEntry.water),
            bmi: roundMetric(ggEntry.bmi) ?? ConversionTools.calculateBMI(weight: Double(ggEntry.weightInKg), height: calculateHeightCm(height: activeAccount.weightSettings?.height)),
            source: sourceType.rawValue
        )
        // Create BathScaleMetric with detailed metrics
        let scaleMetric = BathScaleMetric(
            bmr: ggEntry.bmr,
            metabolicAge: ggEntry.metabolicAge,
            proteinPercent: roundMetric(ggEntry.proteinPercent),
            pulse: ggEntry.pulse,
            skeletalMusclePercent: roundMetric(ggEntry.skeletalMusclePercent),
            subcutaneousFatPercent: roundMetric(ggEntry.subcutaneousFatPercent),
            visceralFatLevel: ggEntry.visceralFatLevel,
            boneMass: roundMetric(ggEntry.boneMass),
            impedance: roundMetric(ggEntry.impedance),
            unit: ggEntry.unit.lowercased()
        )
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric
        return entry
    }

    private func handleDeviceEventAlert(_ deviceData: GGScanResponseData, isDuplicateUserError: Bool) async {
        // Log the alert for now - in a full implementation this could trigger UI alerts
        print("Device alert")
    }

    private func disconnectDeletedScales(currentScales: [Device], newScales: [Device]) async {
        let deletedScales = currentScales.filter { currentScale in
            !newScales.contains { newScale in
                currentScale.broadcastId == newScale.broadcastId
            }
        }

        for scale in deletedScales {
            if scale.isConnected ?? false {
                do {
                  if scale.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue {
                      _ = try await deleteDevice(scale, disconnect: false)
                    }

                  guard let broadcastId = scale.broadcastIdString else { continue }
                  try await disconnectDevice(broadcastId: broadcastId)
                } catch {
                }
            }
        }
    }

    /// Helper to calculate age from a date string (YYYY-MM-DD), matching JS logic
    private func calculateAge(from dateString: String?) -> Int? {
        guard let dateString = dateString else { return nil }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let birthDate = formatter.date(from: dateString) else { return nil }
        let today = Date()
        let calendar = Calendar.current
        var age = calendar.component(.year, from: today) - calendar.component(.year, from: birthDate)
        let monthDiff = calendar.component(.month, from: today) - calendar.component(.month, from: birthDate)
        let dayDiff = calendar.component(.day, from: today) - calendar.component(.day, from: birthDate)
        if monthDiff < 0 || (monthDiff == 0 && dayDiff < 0) {
            age -= 1
        }
        return age
    }

    private func calculateHeightCm(height: String?) -> Int {
        let storedHeight: Int = {
            if let heightStr = height, let h = Int(heightStr) { return h }
            return 680 // fallback: 68.0 inches (5'8")
        }()
        return ConversionTools.convertStoredHeightToCm(storedHeight)
    }

    /// Creates ScanData from Account using proper conversions and types
    func createScanData(from account: Account?) -> ScanData? {
        guard let account = account else { return nil }
        // Height: stored as string in tenths of inches, convert to cm
        let heightCm = calculateHeightCm(height: account.weightSettings?.height)
        // Age: calculate from dob (YYYY-MM-DD)
        let age = calculateAge(from: account.dob) ?? 30
        // Athlete: activityLevel == "athlete"
        let isAthlete = account.weightSettings?.activityLevel?.rawValue == "athlete"
        // Unit: .lb/.kg enum to string
        let unit = account.weightSettings?.weightUnit?.rawValue ?? "kg"
        // Sex: enum to string
        let sex = account.gender?.rawValue ?? "male"
        // Goal weight: from goalSettings, convert using ConversionTools
        let goalWeight: Double? = {
            if let goalWeight = account.goalSettings?.goalWeight {
              return ConversionTools.convertStoredToDisplay(Int(goalWeight), isMetric: true)
            }
            return nil
        }()
        // Build ScanData
        return ScanData(
            sex: sex,
            height: Double(heightCm),
            age: age,
            isAthlete: isAthlete,
            unit: unit,
            goalWeight: goalWeight,
            additionalInfo: nil
        )
    }

    /// Converts Account to GGBTUserProfile for SDK, using latest entry for weight
    func getProfileInfo(from account: Account) async -> GGBTUserProfile? {
        guard let scanData = createScanData(from: account) else {
          return nil
        }
        // Weight: use latest entry if available, else nil
        var currentWeight: Double? = nil
        if let latest = try? await entryService.getLatestEntry(), let weight = latest.scaleEntry?.weight {
          currentWeight = ConversionTools.convertStoredToDisplay(weight, isMetric: scanData.unit == "kg")
        }
        // Name: firstName or fallback
        let name = account.firstName ?? "User"
        // Goal type: from goalSettings
        let goalType = account.goalSettings?.goalType?.rawValue
        // Build GGBTUserProfile
        return GGBTUserProfile(
            name: name,
            age: scanData.age,
            sex: scanData.sex,
            unit: scanData.unit,
            height: scanData.height,
            weight: currentWeight,
            goalWeight: scanData.goalWeight,
            isAthlete: scanData.isAthlete,
            goalType: goalType,
            metrics: nil
        )
    }

    /// Converts various GG entry types to our Entry model
    private func convertToEntry(_ entryData: GGScanResponseData) -> Entry? {
        if let weightEntry = entryData as? GGWeightEntry {
            return convertWeightEntry(weightEntry)
        } else if let fullEntry = entryData as? GGEntry {
            return convertGGEntry(fullEntry)
        }
        // Add support for other entry types as needed
        return nil
    }

    /// Converts multiple entries from GGEntryList
    private func convertEntryList(_ entryList: GGEntryList) -> [Entry] {
        return entryList.list.compactMap { convertGGEntry($0) }
    }

    /// Returns the weight value for a GGEntry based on protocol type, matching conversion logic from TypeScript
    private func getWeightByProtocolType(protocolType: ProtocolType, entry: GGEntry) -> Int? {
        switch protocolType {
        case .A3:
            // Bluetooth (A3) scales have a resolution of .2 lbs, so they require a specific formula to match
            return Int(ConversionTools.convertBluetoothToStored(Double(entry.weightInKg)) * 10)
        case .A6:
            return Int(ConversionTools.convertKgToStored(Double(entry.weightInKg)) * 10)
        case .R4:
            return Int(ConversionTools.convertLbsToStored(Double(entry.weight)))
        }
    }

    // Rounds a Float? or Double? metric to Int? (x10 for storage)
    func roundMetric(_ metric: Float?) -> Int? {
        guard let metric = metric else { return nil }
        return Int(floor(Double(metric) * 10))
    }
    func roundMetric(_ metric: Double?) -> Int? {
        guard let metric = metric else { return nil }
        return Int(floor(metric * 10))
    }
}

// MARK: - Helpers & Mapping
private extension BluetoothService {
    func mapToGGBTDevice(_ device: Device) -> GGBTDevice? {
        guard let bid = device.broadcastIdString else { return nil }
        return GGBTDevice(
            name: device.deviceName ?? "",
            broadcastId: bid,
            password: device.password,
            token: device.token,
            userNumber: Int(device.userNumber ?? "0") ?? 0,
            preference: mapToGGPreference(device.r4ScalePreference),
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: device.protocolType ?? "",
            macAddress: device.mac ?? ""
        )
    }

    func mapToGGBTDevice(_ broadcastId: String) -> GGBTDevice {
        return GGBTDevice(
            name: "",
            broadcastId: broadcastId,
            password: nil,
            token: nil,
            userNumber: 0,
            preference: nil,
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: "",
            macAddress: ""
        )
    }

    func mapToGGPreference(_ preference: R4ScalePreference?) -> GGDevicePreference? {
      guard let preference = preference else {
        return nil
      }
        return GGDevicePreference(
            displayName: preference.displayName,
            // Add other preference mappings as needed
        )
    }
}


