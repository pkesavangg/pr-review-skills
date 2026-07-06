package com.dmdbrands.gurus.weight.features.myKids.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MyKidsViewModel @Inject constructor(
    private val babyProfileService: IBabyProfileService,
    private val accountRepository: IAccountRepository,
) : BaseIntentViewModel<MyKidsState, MyKidsIntent>(MyKidsReducer()) {

    override fun provideInitialState() = MyKidsState()

    override fun onDependenciesReady() {
        observeBabies()
        loadActiveBabyId()
        loadMeasurementUnits()
        refreshBabies()
    }

    private fun loadMeasurementUnits() {
        viewModelScope.launch {
            val units = accountRepository.getActiveAccount().first()?.measurementUnits ?: return@launch
            handleIntent(MyKidsIntent.SetMeasurementUnits(units))
        }
    }

    override fun handleIntent(intent: MyKidsIntent) {
        super.handleIntent(intent)
        when (intent) {
            is MyKidsIntent.SaveBaby -> saveBaby(intent)
            is MyKidsIntent.DeleteBaby -> deleteBaby(intent.babyId)
            else -> {}
        }
    }

    private fun observeBabies() {
        viewModelScope.launch {
            babyProfileService.observeAll().collect { list ->
                handleIntent(MyKidsIntent.SetBabies(list.toImmutableList()))
            }
        }
    }

    private fun loadActiveBabyId() {
        viewModelScope.launch {
            val babyId = accountRepository.getActiveBabyId()
            handleIntent(MyKidsIntent.SetActiveBabyId(babyId))
        }
    }

    private fun refreshBabies() {
        viewModelScope.launch {
            try {
                babyProfileService.refresh()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to refresh baby profiles", e)
            }
        }
    }

    private fun saveBaby(intent: MyKidsIntent.SaveBaby) {
        viewModelScope.launch {
            handleIntent(MyKidsIntent.SetLoading(true))
            try {
                val activeAccount = accountRepository.getActiveAccount().first()
                val accountId = activeAccount?.id ?: return@launch

                val birthdateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(Date(intent.birthdayMillis))
                val isEditing = intent.babyId != null
                val profile = BabyProfile(
                    // Editing reuses the existing id so the server PUT targets the same baby;
                    // creating mints a fresh id.
                    id = intent.babyId ?: UUID.randomUUID().toString(),
                    accountId = accountId,
                    name = intent.name,
                    birthdate = birthdateStr,
                    sex = intent.biologicalSex,
                    birthWeightDecigrams = intent.birthWeightDecigrams,
                    birthLengthMillimeters = intent.birthLengthMillimeters,
                )
                var savedBaby: BabyProfile? = null
                if (isEditing) {
                    babyProfileService.update(profile)
                } else {
                    // save() returns the persisted profile carrying the server-assigned id
                    // (the client id is discarded), which is what activeBabyId must reference.
                    val saved = babyProfileService.save(profile)
                    savedBaby = saved
                    // A newly-added baby always becomes the active baby, overriding any
                    // previous active baby.
                    accountRepository.setActiveBabyId(accountId, saved.id)
                    handleIntent(MyKidsIntent.SetActiveBabyId(saved.id))
                }

                // Refresh available products so the new baby appears in the dropdown
                // (and replaces the "Baby Scale" empty entry) without an app restart. (MOB-416)
                productSelectionManager.loadAvailableProducts(accountId)

                // A newly-added baby also becomes the active product view: switch the dashboard
                // to that baby and leave snapshot mode so it opens on the baby's detail dashboard.
                savedBaby?.let { saved ->
                    productSelectionManager.selectProduct(ProductSelection.Baby(saved))
                    productSelectionManager.setSnapshotMode(false)
                }

                AppLog.d(TAG, "Saved baby profile: ${profile.id}")
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to save baby profile", e)
            } finally {
                handleIntent(MyKidsIntent.SetLoading(false))
            }
        }
    }

    private fun deleteBaby(babyId: String) {
        viewModelScope.launch {
            try {
                val accountId = accountRepository.getActiveAccount().first()?.id
                babyProfileService.delete(babyId)
                // If deleted baby was the active one, move active to the next or clear it
                if (_state.value.activeBabyId == babyId && accountId != null) {
                    val remaining = _state.value.babies.filter { it.id != babyId }
                    val nextId = remaining.firstOrNull()?.id
                    if (nextId != null) {
                        accountRepository.setActiveBabyId(accountId, nextId)
                        handleIntent(MyKidsIntent.SetActiveBabyId(nextId))
                    } else {
                        accountRepository.clearActiveBabyId(accountId)
                        handleIntent(MyKidsIntent.SetActiveBabyId(null))
                    }
                }
                // Refresh available products so the dropdown and baby surfaces update live:
                // deleting the last baby surfaces "Baby Scale" when a scale is owned (empty
                // state + ADD A BABY), otherwise drops the baby entry entirely. (MOB-416)
                if (accountId != null) {
                    productSelectionManager.loadAvailableProducts(accountId)
                }
                AppLog.d(TAG, "Deleted baby profile: $babyId")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to delete baby profile", e)
            }
        }
    }

    companion object {
        private const val TAG = "MyKidsViewModel"
    }
}
