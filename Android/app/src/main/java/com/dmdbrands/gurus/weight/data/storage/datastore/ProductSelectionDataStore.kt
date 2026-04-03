package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.proto.ProductSelectionProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

private val Context.productSelectionDataStore: DataStore<ProductSelectionProto> by dataStore(
    fileName = "product_selection.pb",
    serializer = ProductSelectionProtoSerializer,
)

class ProductSelectionDataStore(
    context: Context,
) : BaseProtoDataStore<ProductSelectionProto>(context.productSelectionDataStore) {

    override fun getDefaultInstance(): ProductSelectionProto =
        ProductSelectionProto.getDefaultInstance()

    fun observeSelectedProductType(): Flow<ProductType> =
        dataFlow.map { proto ->
            val value = proto.selectedProductType
            if (value.isBlank()) {
                ProductType.MY_WEIGHT
            } else {
                runCatching { ProductType.valueOf(value) }.getOrDefault(ProductType.MY_WEIGHT)
            }
        }

    fun observeSelectedBabyProfileId(): Flow<String?> =
        dataFlow.map { proto ->
            proto.selectedBabyProfileId.ifBlank { null }
        }

    suspend fun saveSelectedProductType(productType: ProductType) {
        try {
            updateData { currentData ->
                currentData.toBuilder()
                    .setSelectedProductType(productType.name)
                    .build()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error saving selected product type", e.toString())
        }
    }

    suspend fun saveSelectedBabyProfileId(profileId: String?) {
        try {
            updateData { currentData ->
                currentData.toBuilder()
                    .setSelectedBabyProfileId(profileId.orEmpty())
                    .build()
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error saving selected baby profile ID", e.toString())
        }
    }

    companion object {
        private const val TAG = "ProductSelectionDataStore"
    }
}

private object ProductSelectionProtoSerializer : Serializer<ProductSelectionProto> {
    override val defaultValue: ProductSelectionProto =
        ProductSelectionProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ProductSelectionProto =
        ProductSelectionProto.parseFrom(input)

    override suspend fun writeTo(t: ProductSelectionProto, output: OutputStream) =
        t.writeTo(output)
}
