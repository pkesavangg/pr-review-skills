package com.dmdbrands.gurus.weight.features.ScaleSetup.helper

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [switchActiveProductAfterSetup] — the MOB-422 auto-switch applied once a
 * device-setup flow completes.
 */
class SetupProductSwitchTest {

    private val productSelectionManager = mockk<IProductSelectionManager>(relaxed = true)

    @Test
    fun `null selection is a no-op`() = runTest {
        productSelectionManager.switchActiveProductAfterSetup(null)

        coVerify(exactly = 0) { productSelectionManager.selectProduct(any()) }
        verify(exactly = 0) { productSelectionManager.setSnapshotMode(any()) }
    }

    @Test
    fun `blood pressure selection selects product and leaves snapshot mode`() = runTest {
        productSelectionManager.switchActiveProductAfterSetup(ProductSelection.BloodPressure)

        coVerify(exactly = 1) { productSelectionManager.selectProduct(ProductSelection.BloodPressure) }
        verify(exactly = 1) { productSelectionManager.setSnapshotMode(false) }
    }

    @Test
    fun `my weight selection selects product and leaves snapshot mode`() = runTest {
        productSelectionManager.switchActiveProductAfterSetup(ProductSelection.MyWeight)

        coVerify(exactly = 1) { productSelectionManager.selectProduct(ProductSelection.MyWeight) }
        verify(exactly = 1) { productSelectionManager.setSnapshotMode(false) }
    }

    @Test
    fun `baby selection selects product and leaves snapshot mode`() = runTest {
        val baby = ProductSelection.Baby(
            BabyProfile(id = "baby-1", name = "Timmy", birthdate = "2026-01-10", accountId = "acct-1"),
        )

        productSelectionManager.switchActiveProductAfterSetup(baby)

        coVerify(exactly = 1) { productSelectionManager.selectProduct(baby) }
        verify(exactly = 1) { productSelectionManager.setSnapshotMode(false) }
    }
}
