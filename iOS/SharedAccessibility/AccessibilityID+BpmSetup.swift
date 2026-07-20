//
//  AccessibilityID+BpmSetup.swift
//  BpmSetup feature — blood-pressure monitor pairing/onboarding.
//

extension AccessibilityID {
    // MARK: - BPM Setup
    static let bpmSetupScreenRoot = "bpm_setup_screen_root"
    static let bpmSetupCloseButton = "bpm_setup_close_button"
    static let bpmSetupHelpButton = "bpm_setup_help_button"
    static let bpmSetupBackButton = "bpm_setup_back_button"
    static let bpmSetupNextButton = "bpm_setup_next_button"
}

// MARK: - MOB-1489 accessibility-id sweep (declared centrally)
extension AccessibilityID {
    static let bpmLearnHowButton = "bpm_learn_how_button"
    static func bpmModelCard(_ sku: String) -> String { "bpm_model_card_\(sku)" }
    static let bpmNicknameField = "bpm_nickname_field"
    static let bpmUserSlot1Button = "bpm_user_slot1_button"
    static let bpmUserSlot2Button = "bpm_user_slot2_button"
}
