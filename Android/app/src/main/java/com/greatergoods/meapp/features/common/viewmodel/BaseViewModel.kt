package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.features.common.service.DialogQueueService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
 class BaseViewModel @Inject constructor() : ViewModel() {
    @Inject
    lateinit var navigationService: IAppEventService

    @Inject
    lateinit var dialogQueueService: DialogQueueService

}
