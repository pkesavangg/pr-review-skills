package com.greatergoods.libs.appsync.strings

/**
 * Static strings used in the AppSync module.
 */
object AppSyncStrings {
    // Camera and scan related
    const val CameraPermissionRequired = "Camera permission required"
    const val CameraInitializationFailed = "Camera initialization failed. Please try again."
    const val LoadingCamera = "Loading camera"
    const val ScanComplete = "Scan complete!"
    const val ScanCompletePlaceholder = "Scan complete! (Result placeholder)"

    // UI controls
    const val CloseScan = "Close scan"
    const val ZoomIn = "Zoom in"
    const val ZoomOut = "Zoom out"
    const val ManualEntry = "Manual entry"

    // Low light warning
    const val LowLightWarning = "Low light detected"
    const val LowLightMessage = "Please move to a brighter area for better scanning"

    // Log messages
    const val CameraBindingFailed = "Camera binding failed"
    const val EmptyBitArrayReceived = "Empty bit array received"
    const val InvalidScanDetected = "Invalid scan detected - all values are invalid"
    const val InterpreterReturnedNull = "Interpreter returned null (invalid scan)"
    const val NativeDetectorReturnedNull = "Native detector returned null or empty result"
    const val JniFrameProcessingFailed = "JNI frame processing failed"

    // Debug messages
    const val Initializes = "Initializes"
    const val Initializes1 = "Initializes 1"
}
