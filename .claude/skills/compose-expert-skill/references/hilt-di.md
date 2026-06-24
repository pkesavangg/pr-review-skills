# Hilt DI — MeApp Android

## Module locations

All DI modules live in `core/di/`. Repositories and services are bound to `SingletonComponent`.

| Module | Contents |
|---|---|
| `ApiModule.kt` | Retrofit interface providers (`httpClient.createService(...)`) |
| `RepositoryModule.kt` | Repository bindings |
| `ServiceModule.kt` | Service bindings + complex multi-dep providers |
| `DatabaseModule.kt` | Room DAOs |
| `DataStoreModule.kt` | UserDataStore |

## Adding a new API + Repository + Service

```kotlin
// ApiModule.kt
@Provides @Singleton
fun provideMyAPI(httpClient: HttpClient): IMyAPI =
    httpClient.createService(IMyAPI::class.java)

// RepositoryModule.kt
@Provides @Singleton
fun provideMyRepository(
    myDao: MyDao,
    myApi: IMyAPI,
): IMyRepository = MyRepository(myDao, myApi)

// ServiceModule.kt
@Provides @Singleton
fun provideMyService(
    myService: MyService,
): IMyService = myService   // when class has @Inject constructor
```

## Interface naming

- Interfaces use `I` prefix: `IAccountRepository`, `IBabyProfileService`
- Implementations drop the `I`: `AccountRepository`, `BabyProfileService`

## Binding patterns

```kotlin
// Simple — class has @Inject constructor, just expose the interface
@Provides @Singleton
fun provideFooService(fooService: FooService): IFooService = fooService

// Complex — multiple deps, manual wiring
@Provides @Singleton
fun provideEntryService(
    entryRepository: IEntryRepository,
    accountRepository: IAccountRepository,
    @ApplicationScope appScope: CoroutineScope,
): IEntryService = EntryService(entryRepository, accountRepository, appScope)
```

## ApplicationScope

```kotlin
// Inject a long-lived coroutine scope
@ApplicationScope private val appScope: CoroutineScope
```

`@ApplicationScope` is defined in `core/di/ApplicationScope.kt` and provided by `AppModule`.

## Patterns to avoid

- Never call `hiltViewModel()` in non-screen composables — pass the ViewModel down as a lambda.
- Don't inject services that are already available in `BaseViewModel` (navigationService, dialogQueueService, productSelectionManager, customTabManager).
