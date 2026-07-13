package com.dmdbrands.gurus.weight.domain.model.api.entry

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EntryCategory.fromProductType] — the mapping used by the history
 * export (MOB-1230) to send each product's own data to `GET /v3/entries/csv?category=`.
 */
class EntryCategoryTest {

    @Test
    fun `weight product maps to weight category`() {
        assertThat(EntryCategory.fromProductType(ProductType.MY_WEIGHT)).isEqualTo(EntryCategory.WEIGHT)
        assertThat(EntryCategory.fromProductType(ProductType.MY_WEIGHT).value).isEqualTo("weight")
    }

    @Test
    fun `blood pressure product maps to bp category not blood_pressure`() {
        // The entries API uses the short "bp" category, NOT ProductType.apiValue ("blood_pressure").
        assertThat(EntryCategory.fromProductType(ProductType.BLOOD_PRESSURE)).isEqualTo(EntryCategory.BP)
        assertThat(EntryCategory.fromProductType(ProductType.BLOOD_PRESSURE).value).isEqualTo("bp")
    }

    @Test
    fun `baby product maps to baby category`() {
        assertThat(EntryCategory.fromProductType(ProductType.BABY)).isEqualTo(EntryCategory.BABY)
        assertThat(EntryCategory.fromProductType(ProductType.BABY).value).isEqualTo("baby")
    }

    @Test
    fun `every product type maps to a distinct category`() {
        val categories = ProductType.entries.map { EntryCategory.fromProductType(it) }
        assertThat(categories).containsNoDuplicates()
    }
}
