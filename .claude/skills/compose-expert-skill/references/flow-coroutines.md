# Kotlin Flow & Coroutines — MeApp Android

## Coroutine scopes

| Scope | Use case |
|---|---|
| `viewModelScope` | ViewModel-bound work (cancelled on ViewModel clear) |
| `@ApplicationScope` (injected) | App-lifetime work in services |
| `viewModelScope.launch` | Fire-and-forget side effects from ViewModel |

Never create a raw `CoroutineScope()` inside a ViewModel or Composable — use the injected scopes.

## Flow patterns

```kotlin
// Reactive DB query — emits on every DB change
fun observeItems(accountId: String): Flow<List<Foo>> =
    fooDao.observeByAccountId(accountId)

// Account-scoped flows — a very common pattern in this codebase
fun observeAll(): Flow<List<Foo>> =
    accountRepository.getActiveAccount().flatMapLatest { account ->
        val accountId = account?.id ?: return@flatMapLatest flowOf(emptyList())
        fooRepository.observeAll(accountId)
    }

// StateFlow in ViewModel
private val _isUpdating = MutableStateFlow(false)
override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
```

## Null safety with flows

```kotlin
// Early-exit on null — preferred pattern
val accountId = accountRepository.getActiveAccount().first()?.id ?: return

// Local val for smart cast (required for var class properties or cross-module)
val localAccount = account  // then use localAccount in null check
```

## Collecting in Compose

```kotlin
// Always use collectAsStateWithLifecycle (lifecycle-aware, stops on background)
val state by viewModel.state.collectAsStateWithLifecycle()

// LaunchedEffect for one-shot effects
LaunchedEffect(viewModel.events) {
    viewModel.events.collect { event ->
        when (event) { ... }
    }
}
```

## suspend functions

- All API methods must be `suspend`.
- Repository read methods return `Flow<T>` (reactive); write methods are `suspend`.
- Service methods that call API are `suspend` and wrap in try/catch + `AppLog.e`.

```kotlin
// Repository write — suspend, throws on failure
override suspend fun save(foo: Foo) {
    try {
        val response = api.createFoo(foo.toRequest())
        dao.insert(response.toDomain().toEntity())
    } catch (e: Exception) {
        AppLog.e(TAG, "Failed to save foo", e)
        throw e
    }
}
```

## Error handling

- Don't swallow exceptions silently — always `AppLog.e(TAG, "...", e)` before rethrowing or deciding to suppress.
- Repositories rethrow — let the service/ViewModel decide whether to surface the error.
- In ViewModels: catch in `viewModelScope.launch { try { ... } catch (e: Exception) { ... } }`.

## Flow operators to know

```kotlin
.flatMapLatest { }     // switch to new flow when input changes (used for account changes)
.distinctUntilChanged()  // skip identical consecutive emissions
.map { }               // transform values
.first()               // get one value from a Flow (suspends until first emission)
.collect { }           // terminal operator in coroutine
.launchIn(scope)       // launch collection in a scope (hot)
```
