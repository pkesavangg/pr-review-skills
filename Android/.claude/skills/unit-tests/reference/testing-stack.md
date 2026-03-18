# Testing Stack Detection

Before writing any test, read `gradle/libs.versions.toml` and `app/build.gradle.kts` to detect the project's testing stack.

## Detect dependencies

```
grep -i "junit" gradle/libs.versions.toml app/build.gradle.kts
grep -i "mockk\|mockito\|turbine\|truth\|coroutines-test" gradle/libs.versions.toml
```

## JUnit 4 vs JUnit 5

| Aspect | JUnit 4 (MeApp current) | JUnit 5 |
|---|---|---|
| **Setup/teardown** | `@Before` / `@After` | `@BeforeEach` / `@AfterEach` |
| **Test annotation** | `@org.junit.Test` | `@org.junit.jupiter.api.Test` |
| **Assertions** | `org.junit.Assert.assertThrows` | `org.junit.jupiter.api.assertThrows` (Kotlin ext) |
| **Rules** | `@get:Rule val rule = MainDispatcherRule()` | `@RegisterExtension val ext = MainDispatcherExtension()` |
| **Exception testing** | `assertThrows(X::class.java) { runBlocking { } }` | `assertThrows<X> { runBlocking { } }` (reified) |
| **Parameterized** | `@RunWith(Parameterized::class)` | `@ParameterizedTest` + `@ValueSource` / `@CsvSource` |

## Library detection

| Library | Detect via | Import prefix |
|---|---|---|
| **MockK** | `mockk` in versions.toml | `io.mockk.*` |
| **Mockito** | `mockito` in versions.toml | `org.mockito.*` |
| **Truth** | `truth` in versions.toml | `com.google.common.truth.Truth.assertThat` |
| **AssertJ** | `assertj` in versions.toml | `org.assertj.core.api.Assertions.assertThat` |

> Always match the project's existing test dependencies. If an existing test file exists in the same package, read it to follow the same patterns.

## MeApp current stack

| Library | Version | Config |
|---|---|---|
| JUnit | 4.13.2 | `testImplementation` |
| MockK | 1.14.9 | `testImplementation` |
| Truth | 1.4.5 | `testImplementation` |
| Turbine | 1.2.1 | `testImplementation` |
| coroutines-test | 1.10.2 | `testImplementation` |

> If the project migrates to JUnit 5, update all `@Before` -> `@BeforeEach`, `@After` -> `@AfterEach`, `@Rule` -> `@RegisterExtension`, and `org.junit.Test` -> `org.junit.jupiter.api.Test`.
