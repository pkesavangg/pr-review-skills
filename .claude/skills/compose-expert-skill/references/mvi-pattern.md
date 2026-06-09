# MVI Pattern — MeApp Android

## Architecture

Every feature follows a strict three-file MVI structure:

```
Feature/
├── viewmodel/
│   ├── FooState.kt       — immutable data class
│   ├── FooIntent.kt      — sealed class of user actions
│   ├── FooReducer.kt     — pure (State, Intent) → State?
│   └── FooViewModel.kt   — extends BaseIntentViewModel; handles side effects
└── screens/
    └── FooScreen.kt
```

## Reducer

```kotlin
// Pure function — NO coroutines, NO side effects, NO DB/network calls
class FooReducer : IReducer<FooState, FooIntent> {
    override fun reduce(state: FooState, intent: FooIntent): FooState? = when (intent) {
        is FooIntent.SetBar -> state.copy(bar = intent.bar)
        is FooIntent.SetLoading -> state.copy(isLoading = intent.loading)
        else -> state.copy()   // return unchanged state for intents handled in ViewModel
    }
}
```

## ViewModel

```kotlin
@HiltViewModel
class FooViewModel @Inject constructor(
    private val fooService: IFooService,
) : BaseIntentViewModel<FooState, FooIntent>(FooReducer()) {

    override fun provideInitialState() = FooState()

    // Use onDependenciesReady() — NOT init {} — for BaseViewModel lateinit fields
    override fun onDependenciesReady() {
        observeFoo()
    }

    override fun handleIntent(intent: FooIntent) {
        super.handleIntent(intent)  // always call super — routes to reducer
        when (intent) {
            is FooIntent.Save -> save()
            else -> {}
        }
    }

    private fun save() {
        viewModelScope.launch {
            handleIntent(FooIntent.SetLoading(true))
            try {
                fooService.save(...)
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(TAG, "Save failed", e)
            } finally {
                handleIntent(FooIntent.SetLoading(false))
            }
        }
    }
}
```

## Key Rules

- **Never access `BaseViewModel` `lateinit` fields in `init {}`** — `navigationService`, `dialogQueueService`, `productSelectionManager`, `customTabManager` are field-injected AFTER constructor. Use `onDependenciesReady()` instead.
- State objects are immutable data classes; update via `.copy()`.
- Reducers must be pure — no network, no DB, no coroutines.
- Side effects (network, navigation, toasts) belong in the ViewModel, not the Reducer.

## Navigation

```kotlin
// From ViewModel — navigationService is already injected via BaseViewModel
navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(scaleId))
navigationService.navigateBack()
```

Routes are defined as sealed classes in `core/navigation/AppRoute.kt`.

## Global Dialogs & Toasts

```kotlin
// Use dialogQueueService — injected via BaseViewModel
dialogQueueService.enqueue(DialogModel.Confirm(title = ..., onConfirm = { ... }))
dialogQueueService.showLoader(message = "Saving...")
dialogQueueService.dismissLoader()
dialogQueueService.showToast(Toast(title = null, message = "Saved!", action = null))
```

## AssistedInject (runtime params)

```kotlin
@HiltViewModel(assistedFactory = FooViewModel.Factory::class)
class FooViewModel @AssistedInject constructor(
    @Assisted val scaleId: String,
    private val deviceService: IDeviceService,
) : BaseIntentViewModel<FooState, FooIntent>(FooReducer()) {
    @AssistedFactory interface Factory { fun create(scaleId: String): FooViewModel }
}
```
