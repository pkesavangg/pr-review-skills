import Foundation

/// Represents each step in the signup flow.
/// The raw values are no longer used for step ordering — the store
/// computes the ordered `steps` array dynamically based on the
/// selected device type.
enum SignupStep: Int, CaseIterable {
    case name = 0
    case dateOfBirth
    case pickDevice
    case sex
    case height
    case goal
    case email
    case password
    case addBaby
    case babyList
        case profileReady
}

/// The device type the user selects during signup.
enum SignupDeviceType: String, CaseIterable, Identifiable {
    case babyScale
    case bpm
    case weightScale

    var id: String { rawValue }

    var title: String {
        switch self {
        case .babyScale: return SignupStrings.PickDeviceStep.babyScaleTitle
        case .bpm: return SignupStrings.PickDeviceStep.bpmTitle
        case .weightScale: return SignupStrings.PickDeviceStep.weightScaleTitle
        }
    }

    var subtitle: String {
        switch self {
        case .babyScale: return SignupStrings.PickDeviceStep.babyScaleSubtitle
        case .bpm: return SignupStrings.PickDeviceStep.bpmSubtitle
        case .weightScale: return SignupStrings.PickDeviceStep.weightScaleSubtitle
        }
    }

    var iconName: String {
        switch self {
        case .babyScale: return AppAssets.babyAppIcon
        case .bpm: return AppAssets.bpmIcon
        case .weightScale: return AppAssets.weightScaleIcon
        }
    }

    var profileReadyTitle: String {
        switch self {
        case .babyScale: return SignupStrings.ProfileReadyStep.babyScaleTitle
        case .bpm: return SignupStrings.ProfileReadyStep.bpmTitle
        case .weightScale: return SignupStrings.ProfileReadyStep.weightScaleTitle
        }
    }

    /// Device types that should drive HealthKit permission selection
    /// before any paired hardware has been saved.
    var healthKitFallbackDeviceTypes: Set<String> {
        switch self {
        case .babyScale:
            return [DeviceType.babyScale.rawValue]
        case .bpm:
            return [DeviceType.bpm.rawValue]
        case .weightScale:
            return [DeviceType.scale.rawValue]
        }
    }
}
