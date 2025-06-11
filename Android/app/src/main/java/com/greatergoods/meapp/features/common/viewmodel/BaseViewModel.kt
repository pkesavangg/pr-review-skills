package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.features.common.service.DialogQueueService
import javax.inject.Inject

abstract class BaseViewModel : ViewModel() {
    @Inject
    lateinit var navigationService: IAppEventService

    @Inject
    lateinit var dialogQueueService: DialogQueueService

    init {
        injectDependencies()
    }

    private fun injectDependencies() {
        AppLog.e("HomeViewModel", "HomeViewModel init")

        // val entryPoint =
        //     EntryPointAccessors.fromApplication(
        //         MeAppApplication.instance,
        //         ViewModelServiceEntryPoint::class.java,
        //     )
        //
        // navigationService = entryPoint.navigationService
        // dialogQueueService = entryPoint.dialogQueueService
    }
}
