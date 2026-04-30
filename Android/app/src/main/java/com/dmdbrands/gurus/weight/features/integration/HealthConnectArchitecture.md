# Health Connect Architecture - Improved Implementation

## Overview

The Health Connect integration has been improved to follow better architectural principles by implementing the `load()` functionality in the **HealthConnect.kt library** rather than the service layer.

## Architecture Decision

### ✅ **Current Implementation (Recommended)**
```
UI Layer (Activity/Composable)
    ↓
Service Layer (HealthConnectService)
    ↓ delegates load() to
Library Layer (HealthConnect.kt) ← Handles ActivityResultLauncher
    ↓
Health Connect SDK
```

### ❌ **Previous Implementation (Not Recommended)**
```
UI Layer (Activity/Composable)
    ↓
Service Layer (HealthConnectService) ← Handling ActivityResultLauncher
    ↓
Library Layer (HealthConnect.kt)
    ↓
Health Connect SDK
```

## Key Benefits

### 1. **Better Separation of Concerns**
- **Library Layer**: Handles all Health Connect SDK interactions including permission management
- **Service Layer**: Focuses on business logic, data management, and app-specific operations
- **UI Layer**: Handles user interactions and state management

### 2. **Encapsulation**
- ActivityResultLauncher setup is encapsulated within the library
- No need to expose internal Health Connect implementation details to the service
- Library provides clean, high-level API for permission handling

### 3. **Consistency with Plugin Pattern**
- Follows the same pattern as the original HealthConnectPlugin implementation
- Familiar pattern for developers working with Capacitor plugins
- Easier to maintain and understand

### 4. **Simplified Service Layer**
- Service layer is much cleaner and focused
- No complex permission handling logic in the service
- Easier to test and maintain

## Implementation Details

### Library Layer (HealthConnect.kt)

```kotlin
class HealthConnect(private val activity: Activity) : IHealthConnect {

    // Internal permission handling
    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>
    private var isLoaded = false

    /**
     * Loads Health Connect with permission handling setup
     */
    fun load(options: HealthConnectOptions, callback: ((HealthConnectRequestStatus) -> Unit)? = null) {
        // Sets up ActivityResultLauncher internally
        // Handles all permission callback logic
        // Provides clean callback interface
    }

    /**
     * Requests authorization using internal launcher
     */
    suspend fun requestAuthorizationWithLauncher(
        options: HealthConnectOptions,
        callback: ((HealthConnectRequestStatus) -> Unit)? = null
    ): HealthConnectRequestStatus {
        // Uses internal ActivityResultLauncher
        // Handles permission dialog launching
        // Returns results via callback
    }
}
```

### Service Layer (HealthConnectService.kt)

```kotlin
@Singleton
class HealthConnectService @Inject constructor(
    private val healthConnectRepository: IHealthConnectRepository,
    private val accountRepository: IAccountRepository
) : IHealthConnectService {

    /**
     * Delegates to library's load functionality
     */
    override fun load(activity: Activity) {
        healthConnect = HealthConnect(activity)
        healthConnect.load(requestingPermissions) { status ->
            // Handle app-specific status updates
        }
    }

    /**
     * Uses library's permission launcher
     */
    override suspend fun requestAuthorization(): HealthConnectRequestStatus {
        return suspendCancellableCoroutine { continuation ->
            healthConnect.requestAuthorizationWithLauncher(requestingPermissions) { result ->
                continuation.resume(result)
            }
        }
    }
}
```

### UI Layer Usage

```kotlin
@Composable
fun HealthConnectIntegrationScreen(
    viewModel: HealthConnectViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? Activity

    // Load Health Connect when screen loads
    LaunchedEffect(Unit) {
        activity?.let {
            viewModel.loadHealthConnect(it) // Delegates to service.load() → library.load()
        }
    }

    // Request permissions
    Button(onClick = {
        viewModel.requestPermissions() // Uses library's internal launcher
    }) {
        Text("Request Permissions")
    }
}
```

## Flow Diagram

```
1. UI calls viewModel.loadHealthConnect(activity)
   ↓
2. ViewModel calls healthConnectService.load(activity)
   ↓
3. Service calls healthConnect.load(options, callback)
   ↓
4. Library sets up ActivityResultLauncher internally
   ↓
5. UI calls viewModel.requestPermissions()
   ↓
6. Service calls healthConnect.requestAuthorizationWithLauncher()
   ↓
7. Library launches permission dialog using internal launcher
   ↓
8. Permission result handled by library's internal callback
   ↓
9. Result propagated back through callback chain
```

## Advantages Over Previous Implementation

### 1. **No Exposed Internal Details**
- Service doesn't need to know about ActivityResultLauncher
- Service doesn't need to handle permission contracts
- Service doesn't need complex permission processing logic

### 2. **Easier Testing**
- Service layer can be tested without mocking ActivityResultLauncher
- Library can be tested independently
- Clear separation of responsibilities

### 3. **Better Maintainability**
- Permission logic is centralized in the library
- Service focuses on business logic only
- Changes to permission handling don't affect service layer

### 4. **Consistent API**
- Same pattern as other Health Connect operations
- Familiar callback-based API
- Clean separation between library and app logic

## Migration Notes

If migrating from the previous implementation:

1. Remove ActivityResultLauncher setup from service
2. Remove permission processing logic from service
3. Update service to delegate to library's load() method
4. Update requestAuthorization to use library's launcher method
5. Test the simplified flow

## Best Practices

1. **Always call load() first** before any permission operations
2. **Use the callback pattern** for handling permission results
3. **Keep service layer focused** on business logic
4. **Let the library handle** all Health Connect SDK interactions
5. **Follow the established patterns** from the plugin implementation
