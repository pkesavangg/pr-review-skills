# Dialog Queue System (Global Dialogs)

## Overview

The dialog queue system provides a global, priority-based dialog queue for Jetpack Compose apps. It allows any ViewModel or screen to enqueue dialogs, which are shown one at a time in priority order, with optional delay between dialogs.

## Components

- **DialogQueueService**: Singleton service managing the dialog queue (priority, delay, FIFO).
- **DialogQueueViewModel**: ViewModel wrapper for the service, exposes StateFlow for Compose.
- **DialogQueueHost**: Composable that observes the ViewModel and displays dialogs reactively.
- **DialogModel**: Sealed class for dialog types (Alert, Confirm, Custom), with priority and delay.

## Global Integration

- The dialog queue is globally available and integrated at the top level in `MeApp.kt`:
    ```kotlin
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    DialogQueueHost(dialogQueueViewModel)
    ```

## Injecting and Using the Service in Any ViewModel

### 1. Inject the ViewModel (recommended)

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val dialogQueueViewModel: DialogQueueViewModel
) : ViewModel() {
    fun showError() {
        dialogQueueViewModel.enqueue(
            DialogModel.Alert(
                title = "Error",
                message = "Something went wrong!",
                onDismiss = { /* handle dismiss */ },
                alertPriority = 10,
                alertDelayMillis = 500
            )
        )
    }
}
```

### 2. Inject the Service (advanced, for non-Compose or custom use)

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val dialogQueueService: DialogQueueService
) : ViewModel() {
    fun showCustomDialog() {
        dialogQueueService.enqueue(
            DialogModel.Custom(
                contentKey = "MyCustomDialog",
                params = mapOf("foo" to 123),
                onDismiss = { /* ... */ },
                customPriority = 20
            )
        )
    }
}
```

## Best Practices

- Use the provided ViewModel for Compose screens.
- Always call `onDismiss` in your dialog logic.
- Set `priority` and `delayMillis` as needed for UX.
- Dialogs are globally visible and managed in a single queue.

---
