package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class BabyProfileService @Inject constructor(
    private val babyProfileRepository: IBabyProfileRepository,
    private val accountRepository: IAccountRepository,
    private val analyticsService: IAnalyticsService,
) : IBabyProfileService {

    override fun observeAll(): Flow<List<BabyProfile>> =
        accountRepository.getActiveAccount().flatMapLatest { account ->
            val accountId = account?.id ?: return@flatMapLatest flowOf(emptyList())
            babyProfileRepository.observeAll(accountId)
        }

    override suspend fun save(profile: BabyProfile) {
        AppLog.d(TAG, "Saving baby profile: ${profile.id}")
        babyProfileRepository.save(profile)
        analyticsService.logEvent(IAnalyticsService.Events.BABY_PROFILE_CREATED)
        // NOTE: the server auto-adds "baby" to the account's productTypes. Mirroring
        // that into the local account model is deferred to MOB-377 (Account API
        // foundation), which introduces productTypes on Android.
    }

    override suspend fun update(profile: BabyProfile) {
        AppLog.d(TAG, "Updating baby profile: ${profile.id}")
        babyProfileRepository.update(profile)
    }

    override suspend fun delete(profileId: String) {
        AppLog.d(TAG, "Deleting baby profile: $profileId")
        babyProfileRepository.delete(profileId)
    }

    override suspend fun refresh() {
        val accountId = accountRepository.getActiveAccount().first()?.id ?: return
        AppLog.d(TAG, "Refreshing baby profiles for account: $accountId")
        babyProfileRepository.refresh(accountId)
    }

    companion object {
        private const val TAG = "BabyProfileService"
    }
}
