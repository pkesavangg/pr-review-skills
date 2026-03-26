package com.dmdbrands.gurus.weight.testutil

import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.viewmodel.BaseViewModel
import io.mockk.mockk

/**
 * Injects [BaseViewModel]'s three `@Inject lateinit var` fields via reflection,
 * allowing ViewModel unit tests to run without Hilt.
 *
 * Usage:
 * ```
 * viewModel = MyViewModel(service).initTestDependencies()
 * ```
 *
 * Pass explicit mocks when you need to verify interactions:
 * ```
 * val nav = mockk<IAppNavigationService>(relaxed = true)
 * viewModel = MyViewModel(service).initTestDependencies(navigationService = nav)
 * coVerify { nav.navigateTo(ExpectedRoute) }
 * ```
 */
fun <T : BaseViewModel> T.initTestDependencies(
    navigationService: IAppNavigationService = mockk(relaxed = true),
    dialogQueueService: IDialogQueueService = mockk(relaxed = true),
    customTabManager: ICustomTabManager = mockk(relaxed = true),
): T {
    val baseClass = BaseViewModel::class.java
    listOf(
        "navigationService" to navigationService,
        "dialogQueueService" to dialogQueueService,
        "customTabManager" to customTabManager,
    ).forEach { (name, mock) ->
        baseClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@initTestDependencies, mock)
        }
    }
    return this
}
