package com.greatergoods.meapp.features.common.interfaces

import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.features.common.service.DialogQueueService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ViewModelServiceEntryPoint {
    val navigationService: IAppEventService
    val dialogQueueService: DialogQueueService
}
