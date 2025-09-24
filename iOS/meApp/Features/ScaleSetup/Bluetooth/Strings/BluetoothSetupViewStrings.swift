
import Foundation

struct BluetoothSetupViewStrings {
    struct ConnectingBluetoothViewStrings {
        static let title = "Press and hold the UNIT on the back of your scale."
        static let description = "Release the button when the animation on your scale's screen begins. It will then show brackets, and the scale will fall asleep."
        static let pairing = "Pairing"
        static let paired = "Paired!"
        static let pairAgain = "Pair Again"
        static let pairingFailed = "Pairing failed. Please try again."
    }
    
    struct SetUserViewStrings {
        static let title = "Set your user number on the scale."
        static let description: (Bool, Int) -> String = { isSeL, userNumber in
            "Press the \(isSeL ? "SEL" : "SET") button on the front of the scale and then use the arrow buttons to find your user number(U\(userNumber))."
        }
        static let boldWords: [String] = ["SEL", "SET"]
    }
    
    struct StepOnViewStrings {
        static let title = "Let’s weigh in!"
        static let description = "Set your scale on a hard, flat surface, step on, and wait for your results."
        static let syncingInfo : (Bool) -> String = { isSynced in
            isSynced ? "Synced!" : "Syncing ..."
        }
    }
}
