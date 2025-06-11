package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.MeAppApplication
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.features.common.interfaces.ViewModelServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

abstract class BaseViewModel : ViewModel() {

    @Inject
    lateinit var navigationService: IAppEventService

    @Inject
    lateinit var dialogQueueService: IDialogQueueService

    init {
        injectDependencies()
    }

    private fun injectDependencies() {
        val entryPoint = EntryPointAccessors.fromApplication(
            MeAppApplication.instance,
            ViewModelServiceEntryPoint::class.java,
        )

        navigationService = entryPoint.navigationService
        dialogQueueService = entryPoint.dialogQueueService
    }
}
