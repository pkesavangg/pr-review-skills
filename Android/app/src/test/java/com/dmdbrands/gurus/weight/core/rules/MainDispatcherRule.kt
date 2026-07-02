package com.dmdbrands.gurus.weight.core.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A JUnit 5/6 Extension that swaps [Dispatchers.Main] with a [TestDispatcher] for the
 * duration of each test, then restores the original dispatcher afterward.
 *
 * The rule owns a single [TestCoroutineScheduler]; the swapped-in Main [dispatcher] is built
 * on it. Tests MUST share that same scheduler with their `runTest` block:
 *
 * ```
 * @JvmField
 * @RegisterExtension
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * @Test fun example() = runTest(mainDispatcherRule.scheduler) { … }
 * ```
 *
 * Sharing the scheduler is what keeps `runTest` and the ViewModel's `viewModelScope`/Main
 * coroutines on ONE virtual clock. Without it, Main runs on a separate scheduler that the
 * test never drives, so a `viewModelScope` coroutine (e.g. an `init{}` flow collector) leaks
 * past the test and later throws — surfacing as a `UncaughtExceptionsBeforeTest` blamed on an
 * unrelated subsequent test (MOB-1010).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {

    /** The dispatcher's virtual clock — pass to `runTest(mainDispatcherRule.scheduler)` so the
     * test body and the swapped-in Main dispatcher share ONE scheduler (see class KDoc). */
    val scheduler = dispatcher.scheduler

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
