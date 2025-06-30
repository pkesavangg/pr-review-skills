package com.greatergoods.libs.appsync.strings

/**
 * Static strings used throughout the AppSync module.
 *
 * This object contains all the string constants used in the AppSync library,
 * organized by category for better maintainability. These strings are used
 * for user-facing messages, accessibility descriptions, log messages, and
 * debug output.
 *
 * The strings are organized into the following categories:
 * - Camera and scan related messages
 * - UI control accessibility descriptions
 * - Low light warning messages
 * - Log and error messages
 * - Debug messages
 */
object AppSyncStrings {
    // ============================================================================
    // Camera and scan related messages
    // ============================================================================

    /** Message displayed when camera permission is not granted */
    const val CameraPermissionRequired = "Camera permission required"

    /** Error message when camera initialization fails */
    const val CameraInitializationFailed = "Camera initialization failed. Please try again."

    /** Loading message displayed while camera is initializing */
    const val LoadingCamera = "Loading camera"

    /** Success message displayed when scan completes successfully */
    const val ScanComplete = "Scan complete!"

    /** Placeholder message for scan completion (used in result screen) */
    const val ScanCompletePlaceholder = "Scan complete! (Result placeholder)"

    // ============================================================================
    // UI controls accessibility descriptions
    // ============================================================================

    /** Accessibility description for the close scan button */
    const val CloseScan = "Close scan"

    /** Accessibility description for the zoom in button */
    const val ZoomIn = "Zoom in"

    /** Accessibility description for the zoom out button */
    const val ZoomOut = "Zoom out"

    /** Text for the manual entry button */
    const val ManualEntry = "Manual entry"

    // ============================================================================
    // Low light warning messages
    // ============================================================================

    /** Accessibility description for the low light warning icon */
    const val LowLightWarning = "Low light detected"

    /** Message suggesting user move to brighter area (currently unused) */
    const val LowLightMessage = "Please move to a brighter area for better scanning"

    // ============================================================================
    // Log messages for debugging and error tracking
    // ============================================================================

    /** Log message when camera binding fails */
    const val CameraBindingFailed = "Camera binding failed"

    /** Log message when empty bit array is received from native detector */
    const val EmptyBitArrayReceived = "Empty bit array received"

    /** Log message when scan is detected but all values are invalid */
    const val InvalidScanDetected = "Invalid scan detected - all values are invalid"

    /** Log message when FS003 interpreter returns null result */
    const val InterpreterReturnedNull = "Interpreter returned null (invalid scan)"

    /** Log message when native detector returns null or empty result */
    const val NativeDetectorReturnedNull = "Native detector returned null or empty result"

    /** Log message when JNI frame processing encounters an error */
    const val JniFrameProcessingFailed = "JNI frame processing failed"

    // ============================================================================
    // Debug messages for development and testing
    // ============================================================================

    /** Debug message for initialization tracking */
    const val Initializes = "Initializes"

    /** Debug message for initialization tracking (variant) */
    const val Initializes1 = "Initializes 1"
}
