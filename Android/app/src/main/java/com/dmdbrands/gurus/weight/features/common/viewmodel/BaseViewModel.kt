package com.dmdbrands.gurus.weight.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
open class BaseViewModel
    @Inject
    constructor() : ViewModel() {

        lateinit var navigationService: IAppNavigationService
            private set

        lateinit var dialogQueueService: IDialogQueueService
            private set

        lateinit var customTabManager: ICustomTabManager
            private set

        lateinit var productSelectionManager: IProductSelectionManager
            private set

        @Inject
        fun injectBaseDependencies(
            navigationService: IAppNavigationService,
            dialogQueueService: IDialogQueueService,
            customTabManager: ICustomTabManager,
            productSelectionManager: IProductSelectionManager,
        ) {
            this.navigationService = navigationService
            this.dialogQueueService = dialogQueueService
            this.customTabManager = customTabManager
            this.productSelectionManager = productSelectionManager
            onDependenciesReady()
        }

        /**
         * Called after all base dependencies are injected by Hilt.
         * Override in subclasses to run code that needs [navigationService],
         * [dialogQueueService], [productSelectionManager], etc.
         * Use this instead of [init] for dependency-dependent setup.
         */
        protected open fun onDependenciesReady() {}

        @Inject
        lateinit var productSelectionManager: IProductSelectionManager

        /**
         * Opens a URL using the injected CustomTabManager.
         * @param url The URL to open.
         */
        fun openInAppBrowser(url: String) {
            customTabManager.openChromeTab(url)
        }
    }
