package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NotificationContentFormatterTest {

  private fun payload(
    measurement: String? = null,
    fallbackBody: String? = null,
  ) = NotificationPayload(
    accountId = null,
    destination = null,
    measurement = measurement,
    monthKey = null,
    babyId = null,
    fallbackTitle = null,
    fallbackBody = fallbackBody,
  )

  @Test
  fun `title is constant me_App brand`() {
    assertThat(NotificationContentFormatter.title()).isEqualTo("me.App")
  }

  @Test
  fun `capName leaves short names unchanged`() {
    assertThat(NotificationContentFormatter.capName("John")).isEqualTo("John")
  }

  @Test
  fun `capName at exactly 20 chars is unchanged`() {
    val name = "a".repeat(20)
    assertThat(NotificationContentFormatter.capName(name)).isEqualTo(name)
  }

  @Test
  fun `capName truncates names over 20 chars with ellipsis`() {
    val result = NotificationContentFormatter.capName("a".repeat(25))
    assertThat(result).isEqualTo("a".repeat(20) + "…")
  }

  @Test
  fun `body includes measurement and name`() {
    val result = NotificationContentFormatter.body(payload(measurement = "149.2 lb"), "John")
    assertThat(result).isEqualTo("New entry of 149.2 lb has been synced to John's account")
  }

  @Test
  fun `body with measurement but no name uses your account`() {
    val result = NotificationContentFormatter.body(payload(measurement = "120/80 mmHg"), null)
    assertThat(result).isEqualTo("New entry of 120/80 mmHg has been synced to your account")
  }

  @Test
  fun `body with name but no measurement omits the value`() {
    val result = NotificationContentFormatter.body(payload(), "Tammy")
    assertThat(result).isEqualTo("New entry has been synced to Tammy's account")
  }

  @Test
  fun `body falls back to server body when nothing resolvable`() {
    val result = NotificationContentFormatter.body(payload(fallbackBody = "Server text"), null)
    assertThat(result).isEqualTo("Server text")
  }

  @Test
  fun `body uses generic default when no fallback body`() {
    val result = NotificationContentFormatter.body(payload(), null)
    assertThat(result).isEqualTo("New entry has been synced to your account")
  }

  @Test
  fun `body treats blank name as unknown`() {
    val result = NotificationContentFormatter.body(payload(measurement = "10 lb"), "   ")
    assertThat(result).isEqualTo("New entry of 10 lb has been synced to your account")
  }

  @Test
  fun `body caps long name`() {
    val result = NotificationContentFormatter.body(payload(measurement = "10 lb"), "a".repeat(25))
    assertThat(result).isEqualTo("New entry of 10 lb has been synced to ${"a".repeat(20)}…'s account")
  }
}
