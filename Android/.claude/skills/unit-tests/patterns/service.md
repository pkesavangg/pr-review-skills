# Service Test Patterns

These patterns apply when testing classes in `core/service/` or `domain/services/` that extend `BaseService`.

## Network guard clause pattern

Every method with `requireNetworkAvailable()` must have an offline test:

```kotlin
@Test
fun `deleteAccount throws when offline`() = runTest {
    stubNetworkUnavailable()

    assertThrows(Exception::class.java) {
        runBlocking { service.deleteAccount(fakeAccount.id, isActiveAccount = true) }
    }
    coVerify(exactly = 0) { accountRepository.deleteAccount(any(), any()) }
}
```

Methods that use `isNetworkAvailable()` (soft check, no throw) — test both paths:

```kotlin
@Test
fun `refreshAccount skips API call when offline`() = runTest {
    stubNetworkUnavailable()

    service.refreshAccount()

    coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
}
```

## Gating condition pattern

Test methods that exit early based on state:

```kotlin
// Gate: account null — re-stub flow, re-create service
@Test
fun `changePassword returns false when no active account`() = runTest {
    every { accountRepository.getActiveAccount() } returns flowOf(null)
    service = createService()

    val result = service.changePassword("old", "new")

    assertThat(result).isFalse()
    coVerify(exactly = 0) { accountRepository.updatePassword(any(), any(), any()) }
}

// Gate: empty/null string parameter
@Test
fun `handleUnauthorizedLogout returns null when accountId is empty`() = runTest {
    val result = service.handleUnauthorizedLogout("")

    assertThat(result).isNull()
}
```

> **Important**: When changing a flow stub that the service collects at init, you MUST call `service = createService()` after re-stubbing.

## HttpException error path pattern

Test all HTTP error codes the service handles in catch blocks:

```kotlin
@Test
fun `login returns null on HttpException 401`() = runTest {
    coEvery { accountRepository.login(any(), any()) } throws httpException(401)

    val result = service.login(fakeAccount.email, "wrong")

    assertThat(result).isNull()
    coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
}

@Test
fun `login returns null on HttpException 0 (no internet)`() = runTest {
    coEvery { accountRepository.login(any(), any()) } throws httpException(0)

    val result = service.login(fakeAccount.email, "password")

    assertThat(result).isNull()
}
```

## Dialog callback pattern

```kotlin
/** Capture the enqueued dialog for callback testing. */
private fun captureDialog(): DialogModel {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    return dialogSlot.captured
}

@Test
fun `given dialog shown, when onConfirm invoked, then deletes account`() = runTest {
    val dialogSlot = slot<DialogModel>()
    every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    // Arrange to trigger dialog...
    service.showDeleteConfirmation(fakeAccount.id)

    val dialog = dialogSlot.captured as DialogModel.Confirm
    dialog.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify(exactly = 1) { accountRepository.deleteAccount(any()) }
}
```

> **Why `advanceUntilIdle()`?** Callbacks may launch new coroutines. `advanceUntilIdle()` drains pending coroutines before verify.

## Iteration side effects — per-item verification

```kotlin
@Test
fun `logoutAll resets notification for every account`() = runTest {
    every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, fakeAccount2))
    service = createService()
    coEvery { accountRepository.logoutAllAccounts() } returns true
    coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

    service.logoutAll()

    coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
    coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) }
}
```

## Max accounts / collection edge cases

```kotlin
@Test
fun `login throws MaxAccountsReachedException when at max and email is new`() = runTest {
    every { accountRepository.getLoggedInAccounts() } returns flowOf(
        (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) }
    )
    service = createService()

    assertThrows(MaxAccountsReachedException::class.java) {
        runBlocking { service.login("brandnew@example.com", "password") }
    }
}
```

## Service-specific success criteria

- [ ] Network routing (online/offline) tested for every method with `requireNetworkAvailable()` or `isNetworkAvailable()`
- [ ] Offline tests verify repository/API never called with `coVerify(exactly = 0)`
- [ ] Every distinct `catch` block triggered by its own test
- [ ] Every `when (e.code())` branch has one test per code + one for `else`
- [ ] Dialog callbacks tested via `slot<DialogModel>()` + `capture()` where applicable
- [ ] Gating conditions tested (account null, empty string, isExpired, max accounts, etc.)
- [ ] Iteration side effects verified per-item for methods that loop over collections
