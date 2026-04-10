///
///  BpmSetupStrings.swift
///  meApp
///

import Foundation

struct BpmSetupStrings {
    static let setupHeader: (String) -> String = { sku in
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Device Setup - \(displaySku)"
    }

    struct A3Permissions {
        static let title = "Permission Settings"
        static let description = "Balance Health needs access to the following permissions to connect to your monitor."
        static let authorizeBluetoothAccess = "Authorize Bluetooth access"
        static let bluetoothTurnedOff = "Bluetooth is turned off"
        static let authorizeLocationAccess = "Authorize Location access"
        static let locationTurnedOff = "Location is turned off"
    }

    struct ModelSelection {
        static let title = "Select your monitor model."
        static let description = "Choose the model number printed on the bottom of your device."
    }

    struct SelectUser {
        static let title = "Which user do you want to be?"
        static let description = "Make sure to pick a user no one else is using."
    }


    struct SetUser {
        static let title: (String) -> String = { userLabel in
            "Set the monitor to User \(userLabel)."
        }
        static func description(for item: ScaleItemInfo) -> String {
            if item.toggleButton {
                return "With the monitor off, toggle the user switch to change users."
            }
            if item.hasNumericUsers {
                return "Change the user by tapping the USER button."
            }
            return "Change the user by tapping the A/B button."
        }
    }

    struct ConfirmUser {
        static let title = "Press the START/STOP button to confirm user selection."
        static func description(for sku: String) -> String {
            switch sku {
            case "0603":
                return "The monitor will confirm and turn off."
            default:
                return "The monitor will say done and turn off."
            }
        }
    }

    struct PowerSwitch {
        static let title = "Set the monitor\u{2019}s power switch to ON."
        static let description = "Keep the switch set to ON unless you need to reset the monitor. "
            + "To enter sleep mode, press SET or let the monitor enter it automatically after one minute of non-use."
    }

    struct PrePairing {
        static let title = "Press and hold the MEM button."
        static let description = "Hold until \"PAIR\" starts flashing on the screen of the monitor."
    }

    struct Scanning {
        static let title = "Searching for monitor..."
    }

    struct ConnectionErrorAlert {
        static let title = "Unable to Connect"
        static let message = "This may be caused by interference from another Bluetooth device. Try again or contact customer service."
        static let tryAgainButton = "TRY AGAIN"
    }

    struct Nickname {
        static let title = "What should this monitor be called?"
        static let placeholder = "nickname"
        static let defaultName = "Smart Wrist Blood Pressure Monitor"
    }

    struct Paired {
        static let title = "Your monitor is paired!"
        static let learnLink = "learn how to measure"
        static let descriptionPrefix = "You can wrap things up by tapping FINISH, or "
        static let descriptionSuffix = " with a quick tutorial."
    }

    struct MeasureSetup {
        static let title = "Let's take a measurement."
        static let description = "Put on the wrist cuff and make sure it's lined up properly. "
            + "Sit down with your feet flat on the floor and your arm angled so that the monitor is level "
            + "with your heart. Press the START button to turn on the monitor."
    }

    struct MeasureStart {
        static let title = "Relax and take a deep breath."
        static let description = "Then press the START button again to begin taking your measurement."
    }

    struct Complete {
        static let title = "Your measurement has been recorded!"
        static let description = "That's it! When you want to record your next measurement simply open the app, "
            + "put the cuff on, and press start on the monitor."
    }

    struct DeviceConflictAlert {

        struct SameUser {
            static let title = "Caution: User Already Paired"
            static let message = "This monitor is already paired under the same User. By continuing the connection will be reset."
            static let continueButton = "CONTINUE"
        }

    }

    struct UserMismatchAlert {
        static let title = "Unable to Connect"
        static let message = "The user settings chosen on the monitor and in the app do not match. Please review and try again."
        static let cancelSetupButton = "CANCEL SETUP"
        static let reviewButton = "REVIEW"
    }
}
