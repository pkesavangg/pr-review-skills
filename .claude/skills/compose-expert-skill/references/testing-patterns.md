# Unit Testing — MeApp Android

## Stack

| Library | Version | Purpose |
|---|---|---|
| MockK | 1.14.9 | Mocking (`coEvery`, `coVerify`, `relaxed = true`) |
| Google Truth | 1.4.5 | Assertions (`assertThat(x).isEqualTo(y)`) |
| Turbine | 1.2.1 | Flow testing (`.test { awaitItem() }`) |
| kotlinx-coroutines-test | 1.10.2 | `runTest`, `UnconfinedTestDispatcher` |
| JUnit Jupiter | 6.0.3 | Test runner |

## Coverage targets

- Minimum: **80% line** (JaCoCo, enforced in CI)
- Auth/account paths: **85%**
- Run: `./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification`

## Test structure (given/when/then)

```kotlin
// JUnit 5 style used throughout the project
class FooServiceTest {

    @JvmField @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()  // UnconfinedTestDispatcher for Dispatchers.Main

    @MockK(relaxUnitFun = true) private lateinit var fooRepository: IFooRepository
    @MockK private lateinit var accountRepository: IAccountRepository

    private lateinit var service: FooService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { accountRepository.getActiveAccount() } returns flowOf(mockk(relaxed = true))
        service = FooService(fooRepository, accountRepository)
    }

    @Test
    fun `save delegates to repository and succeeds`() = runTest {
        val item = Foo(id = "1", name = "test")
        coEvery { fooRepository.save(item) } just Runs

        service.save(item)

        coVerify { fooRepository.save(item) }
    }
}
```

## ViewModel tests

```kotlin
class FooViewModelTest {
    @JvmField @RegisterExtension val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true) lateinit var fooService: IFooService
    private lateinit var viewModel: FooViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { fooService.observeAll() } returns flowOf(emptyList())
        viewModel = FooViewModel(fooService).initTestDependencies(
            navigationService = mockk(relaxed = true),
            dialogQueueService = mockk(relaxed = true),
        )
    }

    @Test
    fun `initial state has default values`() = runTest {
        advanceUntilIdle()
        assertThat(viewModel.state.value.items).isEmpty()
    }
}
```

## Repository tests

```kotlin
@Test
fun `save posts to API then mirrors into DAO`() = runTest {
    coEvery { api.createFoo(any()) } returns response
    
    repository.save(foo)

    coVerifyOrder {
        api.createFoo(any())
        dao.insert(any())
    }
}

@Test
fun `save rethrows and skips local write when API fails`() {
    coEvery { api.createFoo(any()) } throws IOException("network")

    assertThrows(IOException::class.java) { runTest { repository.save(foo) } }
    coVerify(exactly = 0) { dao.insert(any()) }
}
```

## Flow testing with Turbine

```kotlin
@Test
fun `observeAll emits updated list`() = runTest {
    val flow = MutableStateFlow(listOf(item1))
    every { dao.observeAll("acc-1") } returns flow

    service.observeAll().test {
        assertThat(awaitItem()).hasSize(1)
        flow.value = listOf(item1, item2)
        assertThat(awaitItem()).hasSize(2)
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Reducer tests (pure, no dispatcher needed)

```kotlin
@Test
fun `SetBar updates bar in state`() {
    val result = reducer.reduce(FooState(), FooIntent.SetBar("hello"))
    assertThat(result?.bar).isEqualTo("hello")
}
```

## TestFixtures

Reuse pre-built instances from `testutil/TestFixtures.kt`:
```kotlin
val entry = TestFixtures.weightEntry           // ScaleEntry
val account = TestFixtures.activeAccount       // Account
val bpmEntry = TestFixtures.bpmEntry           // BpmEntry
// Or use builders:
val custom = TestFixtures.aWeightEntry(weight = 800.0)
```

## initTestDependencies

`ViewModelTestExtensions.initTestDependencies()` injects `BaseViewModel` lateinit fields via reflection:
```kotlin
viewModel = MyViewModel(service).initTestDependencies(
    navigationService = mockk(relaxed = true),
    dialogQueueService = mockk(relaxed = true),
)
```
