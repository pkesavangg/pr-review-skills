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
        static let description: (Bool) -> String = { isA6 in
            isA6
                ? "Change the user by tapping the A/B button."
                : "Change the user by tapping the USER button."
        }
    }

    struct ConfirmUser {
        static let title = "Press the START/STOP button to confirm user selection."
        static let description = "The monitor will confirm and turn off."
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
        struct DifferentUser {
            static let title = "Device Already Paired"
            static let message: (String) -> String = { userLabel in
                "This monitor is already paired to User \(userLabel). Would you like to replace the existing pairing?"
            }
            static let replaceButton = "Replace"
        }

        struct SameUser {
            static let title = "Device Already Paired"
            static let message = "This monitor is already paired to this user. Please dismiss and use the existing pairing."
        }

}
}
