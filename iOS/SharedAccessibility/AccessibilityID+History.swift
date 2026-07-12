//
//  AccessibilityID+History.swift
//  History feature — list, entry rows & actions.
//

extension AccessibilityID {
    // MARK: - History
    static let historyScreenRoot = "history_screen_root"
    static let historyEntryRow = "history_entry_row"
    static let historyMonthRow = "history_month_row"
    /// Blood-pressure history row expand/collapse control (whole row toggles the notes
    /// section). Gives automation a stable selector instead of a bounds-based tap (MOB-1475).
    static let historyBPRowExpand = "history_bp_row_expand"
    static let historyDeleteButton = "history_delete_button"
    static let historyDownloadButton = "history_download_button"
    static let historyEditNoteButton = "history_edit_note_button"

    // MARK: - Weight history edit sheet (MOB-1172)
    static let weightHistoryEditScreenRoot = "weight_history_edit_screen_root"
    static let weightHistoryEditWeightField = "weight_history_edit_weight_field"
    static let weightHistoryEditNoteField = "weight_history_edit_note_field"
    static let weightHistoryEditDateButton = "weight_history_edit_date_button"
    static let weightHistoryEditTimeButton = "weight_history_edit_time_button"
    static let weightHistoryEditCloseButton = "weight_history_edit_close_button"
    static let weightHistoryEditSaveButton = "weight_history_edit_save_button"

    // MARK: - Empty-state CTAs (MOB-1220)
    static let emptyStatePrimaryButton = "empty_state_primary_button"
    static let emptyStateSecondaryButton = "empty_state_secondary_button"
}
