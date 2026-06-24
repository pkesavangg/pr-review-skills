package com.dmdbrands.gurus.weight.data.storage.db.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [JsonConverter] — the Room type converter migrated from Gson to
 * kotlinx.serialization in MOB-394.
 *
 * Coverage:
 *  - round-trip fromList -> fromString / toList
 *  - null handling on the nullable @TypeConverter signatures
 *  - corrupt-input fallback (malformed JSON -> emptyList, never throws)
 *  - Gson-format compatibility (raw ["a","b"] strings written by older builds still decode)
 *  - legacy null-element preservation (["a", null, "b"] -> ["a","b"], not [])
 */
class JsonConverterTest {

  private lateinit var converter: JsonConverter

  @Before
  fun setUp() {
    converter = JsonConverter()
  }

  @Test
  fun `fromList then fromString round-trips the original list`() {
    val original = listOf("kg", "bmi", "bodyFat")

    val encoded = converter.fromList(original)
    val decoded = converter.fromString(encoded)

    assertEquals(original, decoded)
  }

  @Test
  fun `fromList then toList round-trips the original list`() {
    val original = listOf("a", "b", "c")

    val encoded = requireNotNull(converter.fromList(original))
    val decoded = converter.toList(encoded)

    assertEquals(original, decoded)
  }

  @Test
  fun `fromList of empty list round-trips to empty list`() {
    val encoded = requireNotNull(converter.fromList(emptyList()))

    assertEquals(emptyList<String>(), converter.toList(encoded))
  }

  @Test
  fun `fromString returns null for null input`() {
    assertNull(converter.fromString(null))
  }

  @Test
  fun `fromList returns null for null input`() {
    assertNull(converter.fromList(null))
  }

  @Test
  fun `fromString decodes a raw Gson-formatted array for backward compatibility`() {
    // Value as previously written to the DB by the Gson-based converter.
    assertEquals(listOf("a", "b"), converter.fromString("""["a","b"]"""))
  }

  @Test
  fun `toList decodes a raw Gson-formatted array for backward compatibility`() {
    assertEquals(listOf("a", "b"), converter.toList("""["a","b"]"""))
  }

  @Test
  fun `fromString drops null elements from a legacy list instead of discarding the whole list`() {
    // Older Gson builds could persist nulls in the array; we keep the surviving elements.
    assertEquals(listOf("a", "b"), converter.fromString("""["a",null,"b"]"""))
  }

  @Test
  fun `toList drops null elements from a legacy list instead of discarding the whole list`() {
    assertEquals(listOf("a", "b"), converter.toList("""["a",null,"b"]"""))
  }

  @Test
  fun `fromString returns empty list for malformed json instead of throwing`() {
    assertEquals(emptyList<String>(), converter.fromString("not-json"))
  }

  @Test
  fun `toList returns empty list for malformed json instead of throwing`() {
    assertEquals(emptyList<String>(), converter.toList("{"))
  }
}
