# Health Connect Android Library

A robust, idiomatic, and reusable Android library for integrating with Google Health Connect, with modern Kotlin APIs, Jetpack Compose UI, and background sync support.

---

## Features

- **Idiomatic Kotlin API**: Coroutines, data classes, sealed results, and clear error handling.
- **Permission & Consent Flows**: Easy permission checks, requests, and consent management.
- **Composable UI**: Jetpack Compose onboarding/permission screen.
- **Background Sync**: WorkManager-based sync worker.
- **Modular & Testable**: Each model, enum, and interface in its own file; comprehensive testability.
- **Extensible**: Designed for future data types and flows.

---

## Setup

1. **Add the library module to your project.**
2. **Add dependencies** to your `build.gradle`:

```kotlin
dependencies {
    implementation "androidx.health.connect:connect-client:1.1.0-alpha07"
    implementation "androidx.work:work-runtime-ktx:2.7.1"
    implementation "androidx.compose.material3:material3:1.1.0"
    // ...other dependencies as needed
}
```

---

## Usage

### 1. Repository API

```kotlin
import com.greatergoods.libs.healthconnect.service.HealthConnectRepositoryImpl
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.enum.DataType

val repo = HealthConnectRepositoryImpl(context)

// Check availability and status
val isAvailable = repo.isAvailable()
val status = repo.getStatus()

// Check and request permissions
val options = HealthConnectOptions(
    writeTypes = setOf(DataType.Weight, DataType.BloodPressure),
    readTypes = setOf(DataType.Weight, DataType.BloodPressure)
)
val permissionStatus = repo.getPermissionStatus(options)
val requestResult = repo.requestAuthorization(options)

// Save health data
val result = repo.saveData(dataList)
```

### 2. Onboarding UI (Jetpack Compose)

```kotlin
import com.greatergoods.libs.healthconnect.ui.HealthConnectOnboardingScreen

HealthConnectOnboardingScreen(
    permissionStatus = permissionStatus.name, // Use enum in real usage
    onRequestPermissions = { /* launch permission flow */ }
)
```

- Previews for both light and dark mode are included in the source.

### 3. Background Sync

```kotlin
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.greatergoods.libs.healthconnect.helper.HealthConnectSyncWorker

val workRequest =
    OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
        .build()
WorkManager.getInstance(context).enqueue(workRequest)
```

---

## Data Models

- **HealthConnectData**: Represents a single health data entry (type, value, timestamp, etc.).
- **HealthConnectOptions**: Specifies which data types to request for read/write.
- **BloodPressureData**: For blood pressure records.

See the `model/` folder for all data classes.

---

## Enums

- **DataType**: Supported health data types (Weight, BloodPressure, etc.).
- **HealthConnectStatus**: Availability status (INSTALLED, UNAVAILABLE, etc.).
- **HealthConnectPermissionStatus**: Permission state (ALL, PARTIAL, NONE).
- **HealthConnectRequestStatus**: Result of a permission request.

See the `enum/` folder for all enums.

---

## API Reference

- All public APIs are documented with KDoc in the source files.
- Main entry point: `HealthConnectRepositoryImpl` (implements all repository functions).
- See `interfaces/` for the full interface contract.

---

## UI Components

- **HealthConnectOnboardingScreen**: Jetpack Compose onboarding/permission screen.
    - Previews for light and dark mode.
    - Uses Material 3 theming.

---

## Background Sync

- **HealthConnectSyncWorker**: Extend this class to implement background sync logic using WorkManager.

---

## Project Structure

- `model/`: Data classes (one per file)
- `enum/`: Enums (one per file)
- `interfaces/`: Interfaces (one per file)
- `service/`: Main repository/service implementations
- `ui/`: Jetpack Compose UI components
- `helper/`: Background sync and utility classes

---

## Contributing

- Follow the one-type-per-file rule for all models, enums, and interfaces.
- Add KDoc for all public APIs.
- See `TASKS.md` for migration and development progress.

---

## License

[Your License Here]
