package com.dmdbrands.gurus.weight.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
open class BaseViewModel
    @Inject
    constructor() : ViewModel() {
        @Inject
        lateinit var navigationService: IAppNavigationService

        @Inject
        lateinit var dialogQueueService: IDialogQueueService

        @Inject
        lateinit var customTabManager: ICustomTabManager

        /**
         * Opens a URL using the injected CustomTabManager.
         * @param url The URL to open.
         */
        fun openInAppBrowser(url: String) {
            customTabManager.openChromeTab(url)
        }
    }
