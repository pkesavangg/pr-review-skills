import Foundation
@testable import meApp
import Testing

/// Locks the canonical confirm-exit alert copy standardised in MOB-1835 / MOB-1535:
/// RETURN (secondary) · EXIT (destructive), with two copy variants — entry forms vs
/// everything else. `AlertModifier` uppercases button titles when rendering, so the
/// assertions compare the rendered (uppercased) form the ACs are written against.
@Suite(.serialized)
struct ConfirmExitAlertStringsTests {

    private struct ExitAlertCopy {
        let name: String
        let title: String
        let message: String
        let exitButton: String
        let returnButton: String
    }

    /// Every confirm-exit alert that shares the Variant 2 ("all other flows") copy.
    private static let variant2: [ExitAlertCopy] = [
        ExitAlertCopy(
            name: "SignupExitAlert",
            title: AlertStrings.SignupExitAlert.title,
            message: AlertStrings.SignupExitAlert.message,
            exitButton: AlertStrings.SignupExitAlert.exitButton,
            returnButton: AlertStrings.SignupExitAlert.returnButton
        ),
        ExitAlertCopy(
            name: "LoginExitAlert",
            title: AlertStrings.LoginExitAlert.title,
            message: AlertStrings.LoginExitAlert.message,
            exitButton: AlertStrings.LoginExitAlert.exitButton,
            returnButton: AlertStrings.LoginExitAlert.returnButton
        ),
        ExitAlertCopy(
            name: "EditProfileExitAlert",
            title: AlertStrings.EditProfileExitAlert.title,
            message: AlertStrings.EditProfileExitAlert.message,
            exitButton: AlertStrings.EditProfileExitAlert.exitButton,
            returnButton: AlertStrings.EditProfileExitAlert.returnButton
        ),
        ExitAlertCopy(
            name: "ChangePasswordExitAlert",
            title: AlertStrings.ChangePasswordExitAlert.title,
            message: AlertStrings.ChangePasswordExitAlert.message,
            exitButton: AlertStrings.ChangePasswordExitAlert.exitButton,
            returnButton: AlertStrings.ChangePasswordExitAlert.returnButton
        ),
        ExitAlertCopy(
            name: "WeightLessExitAlert",
            title: AlertStrings.WeightLessExitAlert.title,
            message: AlertStrings.WeightLessExitAlert.message,
            exitButton: AlertStrings.WeightLessExitAlert.exitButton,
            returnButton: AlertStrings.WeightLessExitAlert.returnButton
        ),
        ExitAlertCopy(
            name: "GoalExitAlert",
            title: AlertStrings.GoalExitAlert.title,
            message: AlertStrings.GoalExitAlert.message,
            exitButton: AlertStrings.GoalExitAlert.exitButton,
            returnButton: AlertStrings.GoalExitAlert.returnButton
        )
    ]

    private static let variant1 = ExitAlertCopy(
        name: "ManualEntryExitAlert",
        title: AlertStrings.ManualEntryExitAlert.title,
        message: AlertStrings.ManualEntryExitAlert.message,
        exitButton: AlertStrings.ManualEntryExitAlert.exitButton,
        returnButton: AlertStrings.ManualEntryExitAlert.returnButton
    )

    @Test("Variant 2 flows all use the canonical Confirm! title and unsaved-changes body")
    func variant2SharesCanonicalCopy() {
        for alert in Self.variant2 {
            #expect(alert.title == "Confirm!", "\(alert.name) title")
            #expect(
                alert.message == "You have unsaved changes. Are you sure you want to exit?",
                "\(alert.name) message"
            )
        }
    }

    @Test("Entry-form variant keeps its own title and body")
    func variant1KeepsEntryFormCopy() {
        #expect(Self.variant1.title == "Your entry has not been saved!")
        #expect(Self.variant1.message == "Are you sure you want to exit?")
    }

    @Test("Every confirm-exit alert renders RETURN and EXIT — never GO BACK or YES EXIT")
    func ctaPairIsCanonical() {
        for alert in Self.variant2 + [Self.variant1] {
            #expect(alert.returnButton.uppercased() == "RETURN", "\(alert.name) secondary CTA")
            #expect(alert.exitButton.uppercased() == "EXIT", "\(alert.name) destructive CTA")
        }
    }
}
