//
//  BabyScaleSetupStrings.swift
//  meApp
//

import Foundation

// MARK: - BabyScaleSetupStrings
struct BabyScaleSetupStrings {

    // MARK: - Header
    static let setupHeader: (String) -> String = { sku in
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Scale Setup - \(displaySku)"
    }
    static let monitorSetupHeader: (String) -> String = { sku in
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Monitor Setup - \(displaySku)"
    }

    // MARK: - Intro
    struct Intro {
        static let smartBabyScale = "Smart Baby Scale"
        // swiftlint:disable:next line_length
        static let troubleSettingUp = "If you have any trouble setting up your monitor, you can connect with our team via the help button in the top right."
        static let cantFindModelNumber = "CAN'T FIND YOUR MODEL NUMBER?"
    }

    // MARK: - Permissions
    struct Permissions {
        static let title = "Permission Settings"
        static let subtitle = "meApp needs access to the following permissions to connect with your scale monitor."
        static let turnOnBluetooth = "Turn on Bluetooth"
        static let nearbyDevicesNotAuthorized = "Nearby Devices is not authorized"
    }

    // MARK: - Wakeup
    struct Wakeup {
        static let title = "Turn on your Scale"
        static let searching = "Searching..."
    }

    // MARK: - Connection
    struct Connection {
        static let unableToConnect = "Unable to connect to your device"
        // swiftlint:disable:next line_length
        static let interferenceMessage = "This may be caused by interference from another Bluetooth device. Tap PAIR AGAIN to try again or contact customer service."
        static let pairAgain = "PAIR AGAIN"
        static let support = "SUPPORT"
    }

    // MARK: - Scale Name
    struct ScaleName {
        static let title = "Give your scale a name."
        static let nicknamePlaceholder = "Smart Baby Scale"
        static let nicknameLabel = "nickname"
    }

    // MARK: - Paired
    struct Paired {
        static let title = "You're Paired!"
        static let subtitle = "Add a baby profile to personalize weight tracking. You can do this later from Settings."
    }

    // MARK: - Baby Profile
    struct BabyProfile {
        static let title = "Complete Baby Profile"
        static let subtitle = "Let's add a baby. This helps personalize your baby's scale readings."
        static let namePlaceholder = "name"
        static let birthdayLabel = "baby's birthday"
        static let biologicalSexLabel = "Biological Sex"
        static let birthLengthLabel = "birth length"
        static let birthWeightLabel = "birth weight"
        static let male = "Male"
        static let female = "Female"
        static let required = "Required."
        static let invalidWeight = "Please enter a valid weight."
        static let invalidLength = "Please enter a valid length."
    }

    // MARK: - Baby Added
    struct BabyAdded {
        static let title = "Your Baby Has Been Added!"
        static let subtitle = "This helps personalize your baby's scale readings."
        static let addABaby = "ADD A BABY"
    }

    // MARK: - Buttons
    struct Buttons {
        static let next = "NEXT"
        static let back = "BACK"
        static let save = "SAVE"
        static let continueButton = "CONTINUE"
        static let finish = "FINISH"
    }

    // MARK: - Errors
    struct Errors {
        static let bluetoothOff = "Bluetooth is turned off. Please enable Bluetooth in Settings."
        static let connectionFailed = "Failed to connect to the scale. Please try again."
        static let pairingFailed = "Failed to pair with the scale. Please try again."
        static let profileSaveFailed = "Failed to save baby profile. Please try again."
    }
}
