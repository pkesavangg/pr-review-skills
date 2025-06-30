# AppSync Library

A Jetpack Compose-based Android library for scanning and interpreting FS003 protocol data from smart scales using camera-based detection.

## Overview

AppSync is a modern Android library that provides camera-based scanning capabilities for smart scale data. It uses native JNI libraries for image processing and implements the FS003 protocol for data interpretation. The library is built with Jetpack Compose and follows modern Android development practices.

## Features

- **Camera-based scanning**: Uses CameraX for real-time camera preview and image analysis
- **FS003 protocol support**: Interprets data from smart scales using the FS003 protocol
- **Native performance**: JNI-based image processing for optimal performance
- **Modern UI**: Built with Jetpack Compose for a modern, responsive interface
- **Zoom controls**: Interactive zoom functionality for better scanning accuracy
- **Low light detection**: Automatically detects and warns about low light conditions
- **Manual entry support**: Fallback option for manual data entry
- **Error handling**: Comprehensive error detection and reporting

## Installation

Add the AppSync library to your Android project:

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation(project(":appsync"))
}
```

## Usage

### Basic Usage

```kotlin
import com.greatergoods.libs.appsync.startAppSyncScan

// In your Activity or Fragment
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch AppSync scan
        lifecycleScope.launch {
            try {
                val result = startAppSyncScan(
                    context = this@MainActivity,
                    zoom = 1,
                    showManualEntryButton = true
                )

                // Handle the scan result
                when {
                    result.canceled -> {
                        // User canceled the scan
                        Log.d("AppSync", "Scan canceled")
                    }
                    result.manual -> {
                        // User chose manual entry
                        Log.d("AppSync", "Manual entry selected")
                    }
                    result.weight != null -> {
                        // Valid scan result
                        Log.d("AppSync", "Weight: ${result.weight} kg")
                        Log.d("AppSync", "Body Fat: ${result.fat}%")
                        Log.d("AppSync", "Muscle: ${result.muscle}%")
                        Log.d("AppSync", "Water: ${result.water}%")
                        Log.d("AppSync", "Mode: ${result.mode}")
                    }
                    else -> {
                        // Invalid scan result
                        Log.w("AppSync", "Invalid scan result")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppSync", "Scan failed", e)
            }
        }
    }
}
```

### Parameters

The `startAppSyncScan` function accepts the following parameters:

| Parameter               | Type      | Default  | Description                                |
| ----------------------- | --------- | -------- | ------------------------------------------ |
| `context`               | `Context` | Required | Activity context for launching the scan UI |
| `zoom`                  | `Int`     | `1`      | Initial zoom level (1-5)                   |
| `showManualEntryButton` | `Boolean` | `true`   | Whether to show the manual entry button    |

### Response Format

The scan returns an `AppSyncResult` object with the following properties:

```kotlin
data class AppSyncResult(
    val weight: Float?,           // Weight in kilograms (nullable if not available)
    val fat: Float?,              // Percent body fat (nullable if not available)
    val muscle: Float?,           // Percent muscle (nullable if not available)
    val water: Float?,            // Percent water (nullable if not available)
    val mode: String?,            // Measurement mode (e.g., "kg", "lb")
    val weightErrors: Int = 0,    // Number of errors in weight extraction
    val fatErrors: Int = 0,       // Number of errors in fat extraction
    val muscleErrors: Int = 0,    // Number of errors in muscle extraction
    val waterErrors: Int = 0,     // Number of errors in water extraction
    val modeErrors: Int = 0,      // Number of errors in mode extraction
    val errors: Int = 0,          // Total number of errors detected
    val zoom: Int = 1,            // Final zoom level used
    val canceled: Boolean = false, // True if the operation was canceled
    val manual: Boolean = false   // True if manual entry was triggered
)
```

### Response Scenarios

1. **Successful Scan**: All measurement values are populated with valid data
2. **Canceled**: `canceled = true`, all measurement values are null
3. **Manual Entry**: `manual = true`, all measurement values are null
4. **Invalid Scan**: `errors > 0`, some or all measurement values may be null

## Permissions

The library requires the following permissions:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Make sure to request camera permissions before calling the scan function:

```kotlin
// Request camera permission
if (ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.CAMERA
) != PackageManager.PERMISSION_GRANTED) {
    // Request permission
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST_CODE
    )
}
```

## Architecture

The library follows a modular architecture:

- **Activity Layer**: `AppSyncScanActivity` - Hosts the Compose UI
- **Screen Layer**: `AppSyncScanScreen` - Main Compose UI components
- **Component Layer**: Camera preview, overlay controls, buttons
- **Utility Layer**: Data interpretation, zoom management, result factory
- **Model Layer**: Data classes for results and configuration
- **Native Layer**: JNI bridge for image processing

## Configuration

The library uses constants defined in `AppSyncConstants`:

- **Camera Settings**: Zoom levels, animation durations
- **FS003 Protocol**: Hamming code parameters, data extraction constants
- **Error Thresholds**: Maximum allowed errors for valid scans

## Error Handling

The library provides comprehensive error detection:

- **Camera Errors**: Initialization failures, binding errors
- **Permission Errors**: Missing camera permissions
- **Scan Errors**: Invalid data, protocol violations
- **Low Light Warnings**: Poor lighting conditions

## Dependencies

The library depends on:

- **CameraX**: Camera functionality
- **Jetpack Compose**: UI framework
- **Material3**: Design system
- **Coroutines**: Asynchronous operations
- **Native Libraries**: JNI-based image processing

## Example Implementation

```kotlin
class ScaleScanScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var scanResult by remember { mutableStateOf<AppSyncResult?>(null) }
            var isScanning by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        isScanning = true
                        lifecycleScope.launch {
                            try {
                                val result = startAppSyncScan(
                                    context = this@ScaleScanScreen,
                                    zoom = 2,
                                    showManualEntryButton = true
                                )
                                scanResult = result
                            } catch (e: Exception) {
                                // Handle error
                            } finally {
                                isScanning = false
                            }
                        }
                    },
                    enabled = !isScanning
                ) {
                    Text(if (isScanning) "Scanning..." else "Start Scan")
                }

                scanResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scan Result:")
                    result.weight?.let { Text("Weight: $it kg") }
                    result.fat?.let { Text("Body Fat: $it%") }
                    result.muscle?.let { Text("Muscle: $it%") }
                    result.water?.let { Text("Water: $it%") }
                    result.mode?.let { Text("Mode: $it") }
                }
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

1. **Camera Permission Denied**: Ensure camera permissions are granted before calling the scan
2. **Invalid Scan Results**: Check lighting conditions and ensure the scale display is clearly visible
3. **Performance Issues**: The library uses native processing for optimal performance
4. **Build Errors**: Ensure all dependencies are properly configured

### Debug Information

Enable debug logging to troubleshoot issues:

```kotlin
// The library logs important events with the tag "AppSyncScan"
Log.d("AppSyncScan", "Debug information")
```

## License

This library is proprietary software. Please refer to your license agreement for usage terms.

## Support

For technical support or questions about the AppSync library, please contact the development team.
