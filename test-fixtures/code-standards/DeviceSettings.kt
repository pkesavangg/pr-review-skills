// Fixture for references/code-standards/kotlin.md. Deliberate triggers — do not fix.
//
// Expected findings:
//   P1  stringly-typed value where an enum class belongs
//   P1  `when` over an enum closed with `else ->`
//   P1  sealed interface not used for a closed set of variants (nullable-field state bag)
//   P2  wrong class kind (plain class as data, bare primitives instead of value class)
//   P2  Kotlin coding-convention naming (m- prefix, I- interface, camelCase @Composable)
//   P2  var-where-val and a publicly mutable StateFlow
//   P2  magic numbers with no const val
//   P2  missing visibility modifiers

package com.dmdbrands.meapp.feature.devices

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow

// P2 — `I` prefix is a Java habit, not a Kotlin convention.
interface IDeviceStore {
    fun save(record: DeviceRecord)
}

// P2 — only vals and no behaviour: should be a `data class`, so `==` compares
//      values and assertEquals works the way tests expect.
class DeviceRecord(
    // P2 — `m` prefix; and the type is repeated in the name.
    val mNameString: String,
    // P2 — bare Double for a domain quantity: should be a @JvmInline value class.
    val weight: Double,
)

enum class UnitSystem { IMPERIAL, METRIC }

// P1 — `else ->` disables exhaustiveness: add a variant and this returns "" forever.
fun unitLabel(unit: UnitSystem): String = when (unit) {
    UnitSystem.IMPERIAL -> "lb"
    UnitSystem.METRIC -> "kg"
    else -> ""
}

// P1 — mutually exclusive states modelled as nullable fields: "loading with an
//      error and data" is representable but meaningless. Wants a sealed interface.
data class DeviceScreenState(
    val isLoading: Boolean,
    val devices: List<DeviceRecord>?,
    val errorMessage: String?,
)

class DeviceViewModel(private val store: IDeviceStore) {

    // P2 — publicly mutable: any caller can emit. Wants a private _state + StateFlow.
    val state = MutableStateFlow(DeviceScreenState(true, null, null))

    // P2 — var never reassigned.
    var retryLimit = 3

    // P1 — stringly-typed: "paierd" compiles and the branch silently never runs.
    fun updateStatus(status: String) {
        if (status == "pairing") {
            // P2 — magic number with no `private const val`.
            Thread.sleep(250)
        }
    }

    // P2 — `get` prefix on what should be a property or a noun-named function.
    fun getDeviceCount(): Int = state.value.devices?.size ?: 0
}

// P2 — a @Composable emits UI and is named like the thing it emits: DeviceStatusCard.
@Composable
fun deviceStatusCard(record: DeviceRecord) {
    // …
}
