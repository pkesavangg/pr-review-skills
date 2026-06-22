package com.dmdbrands.gurus.weight.features.export.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ExportStrings.exportDialogTitle].
 *
 * Pure function — no mocking or coroutines needed. Guards against the MOB-673
 * regression where the export confirmation alert always read "Download Weight
 * History" regardless of the active history type.
 */
class ExportStringsTest {

    @Test
    fun `weight product returns weight history title`() {
        assertThat(ExportStrings.exportDialogTitle(ProductType.MY_WEIGHT))
            .isEqualTo("Download Weight History")
    }

    @Test
    fun `blood pressure product returns bp history title`() {
        assertThat(ExportStrings.exportDialogTitle(ProductType.BLOOD_PRESSURE))
            .isEqualTo("Download BP History")
    }

    @Test
    fun `baby product returns baby history title`() {
        assertThat(ExportStrings.exportDialogTitle(ProductType.BABY))
            .isEqualTo("Download Baby History")
    }

    @Test
    fun `each product type maps to a distinct title`() {
        val titles = ProductType.entries.map { ExportStrings.exportDialogTitle(it) }
        assertThat(titles).containsNoDuplicates()
    }
}
