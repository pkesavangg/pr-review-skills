package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NotificationPayloadTest {

  @Test
  fun `from parses all data keys`() {
    val payload = NotificationPayload.from(
      data = mapOf(
        "accountId" to "acc-1",
        "destination" to "weight_scale",
        "measurement" to "149.2 lb",
        "monthKey" to "2026-06",
        "babyId" to "baby-1",
      ),
      notificationTitle = "Weight Gurus",
      notificationBody = "Body",
    )

    assertThat(payload.accountId).isEqualTo("acc-1")
    assertThat(payload.destination).isEqualTo("weight_scale")
    assertThat(payload.measurement).isEqualTo("149.2 lb")
    assertThat(payload.monthKey).isEqualTo("2026-06")
    assertThat(payload.babyId).isEqualTo("baby-1")
    assertThat(payload.fallbackTitle).isEqualTo("Weight Gurus")
    assertThat(payload.fallbackBody).isEqualTo("Body")
  }

  @Test
  fun `from treats blank values as null`() {
    val payload = NotificationPayload.from(
      data = mapOf("accountId" to "  ", "destination" to ""),
      notificationTitle = null,
      notificationBody = null,
    )

    assertThat(payload.accountId).isNull()
    assertThat(payload.destination).isNull()
  }

  @Test
  fun `from with empty data yields nulls`() {
    val payload = NotificationPayload.from(emptyMap(), null, null)

    assertThat(payload.accountId).isNull()
    assertThat(payload.destination).isNull()
    assertThat(payload.measurement).isNull()
    assertThat(payload.productType).isNull()
  }

  @Test
  fun `productType resolves known destination`() {
    val payload = NotificationPayload.from(mapOf("destination" to "blood_pressure"), null, null)
    assertThat(payload.productType).isEqualTo(ProductType.BLOOD_PRESSURE)
  }

  @Test
  fun `productType is null for unknown destination`() {
    val payload = NotificationPayload.from(mapOf("destination" to "spaceship"), null, null)
    assertThat(payload.productType).isNull()
  }
}
