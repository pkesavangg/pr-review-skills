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

// MARK: - MOB-1489 accessibility-id sweep (declared centrally)
extension AccessibilityID {
    static let babyHistoryDayBackButton = "baby_history_day_back_button"
    static let babyHistoryDayListScreenRoot = "baby_history_day_list_screen_root"
    static let babyHistoryDayRow = "baby_history_day_row"
    static let babyHistoryEditCloseButton = "baby_history_edit_close_button"
    static let babyHistoryEditCmField = "baby_history_edit_cm_field"
    static let babyHistoryEditDateButton = "baby_history_edit_date_button"
    static let babyHistoryEditInchesField = "baby_history_edit_inches_field"
    static let babyHistoryEditKgField = "baby_history_edit_kg_field"
    static let babyHistoryEditNotesField = "baby_history_edit_notes_field"
    static let babyHistoryEditOuncesField = "baby_history_edit_ounces_field"
    static let babyHistoryEditPoundsField = "baby_history_edit_pounds_field"
    static let babyHistoryEditSaveButton = "baby_history_edit_save_button"
    static let babyHistoryEditSheetRoot = "baby_history_edit_sheet_root"
    static let babyHistoryEditTimeButton = "baby_history_edit_time_button"
    static let babyHistoryEntryEditNoteButton = "baby_history_entry_edit_note_button"
    static let babyHistoryEntryMoreButton = "baby_history_entry_more_button"
    static let babyHistoryEntryRow = "baby_history_entry_row"
    static let bpHistoryEditCloseButton = "bp_history_edit_close_button"
    static let bpHistoryEditDateButton = "bp_history_edit_date_button"
    static let bpHistoryEditDiastolicField = "bp_history_edit_diastolic_field"
    static let bpHistoryEditNotesField = "bp_history_edit_notes_field"
    static let bpHistoryEditPulseField = "bp_history_edit_pulse_field"
    static let bpHistoryEditSaveButton = "bp_history_edit_save_button"
    static let bpHistoryEditSheetRoot = "bp_history_edit_sheet_root"
    static let bpHistoryEditSystolicField = "bp_history_edit_systolic_field"
    static let bpHistoryEditTimeButton = "bp_history_edit_time_button"
    static let bpHistoryEntryEditNoteButton = "bp_history_entry_edit_note_button"
    static let bpHistoryEntryRow = "bp_history_entry_row"
    static let bpHistoryMonthBackButton = "bp_history_month_back_button"
    static let bpHistoryMonthListScreenRoot = "bp_history_month_list_screen_root"
    static let bpHistoryMonthRow = "bp_history_month_row"
    static let historyMetricRow = "history_metric_row"
    static let historyMonthBackButton = "history_month_back_button"
    static let historyMonthListScreenRoot = "history_month_list_screen_root"
}
