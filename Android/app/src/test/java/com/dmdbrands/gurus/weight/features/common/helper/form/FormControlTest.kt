package com.dmdbrands.gurus.weight.features.common.helper.form

import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationError
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FormControlTest {

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has correct defaults`() {
        val control = FormControl.create("hello")

        assertThat(control.value).isEqualTo("hello")
        assertThat(control.dirty).isFalse()
        assertThat(control.touched).isFalse()
        assertThat(control.error).isNull()
        assertThat(control.pending).isFalse()
    }

    // -------------------------------------------------------------------------
    // onValueChange
    // -------------------------------------------------------------------------

    @Test
    fun `onValueChange updates value`() {
        val control = FormControl.create("")

        control.onValueChange("new value")

        assertThat(control.value).isEqualTo("new value")
    }

    @Test
    fun `onValueChange marks control as dirty`() {
        val control = FormControl.create("")

        control.onValueChange("something")

        assertThat(control.dirty).isTrue()
    }

    @Test
    fun `onValueChange triggers validation`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "field is required") else null
            },
        )

        control.onValueChange("")

        assertThat(control.error).isNotNull()
        assertThat(control.error?.type).isEqualTo("required")
    }

    @Test
    fun `onValueChange clears error when value becomes valid`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "field is required") else null
            },
        )
        control.onValueChange("") // set error
        assertThat(control.error).isNotNull()

        control.onValueChange("valid")

        assertThat(control.error).isNull()
    }

    @Test
    fun `onValueChange invokes callback with old and new values`() {
        var capturedOld: String? = null
        var capturedNew: String? = null
        val control = FormControl.create("initial")
        control.onValueChangeListener { old, new ->
            capturedOld = old
            capturedNew = new
        }

        control.onValueChange("updated")

        assertThat(capturedOld).isEqualTo("initial")
        assertThat(capturedNew).isEqualTo("updated")
    }

    // -------------------------------------------------------------------------
    // onBlur
    // -------------------------------------------------------------------------

    @Test
    fun `onBlur marks control as touched`() {
        val control = FormControl.create("")

        control.onBlur()

        assertThat(control.touched).isTrue()
    }

    @Test
    fun `onBlur triggers validation`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "field is required") else null
            },
        )
        control.onValueChange("") // make dirty

        control.onBlur()

        assertThat(control.error).isNotNull()
    }

    @Test
    fun `onBlur with isTouched false does not mark as touched`() {
        val control = FormControl.create("")

        control.onBlur(isTouched = false)

        assertThat(control.touched).isFalse()
    }

    // -------------------------------------------------------------------------
    // validate
    // -------------------------------------------------------------------------

    @Test
    fun `validate skips validation when not touched and not dirty`() {
        val control = FormControl.create(
            "",
            listOf { ValidationError("always", "always fails") },
        )

        val result = control.validate()

        assertThat(result).isTrue()
        assertThat(control.error).isNull()
    }

    @Test
    fun `validate runs validators when dirty`() {
        val control = FormControl.create(
            "",
            listOf { ValidationError("always", "always fails") },
        )
        control.onValueChange("x") // makes dirty, also validates

        assertThat(control.error).isNotNull()
        assertThat(control.error?.type).isEqualTo("always")
    }

    @Test
    fun `validate runs validators when touched`() {
        val control = FormControl.create(
            "",
            listOf { ValidationError("always", "always fails") },
        )
        control.markAsTouched()

        val result = control.validate()

        assertThat(result).isFalse()
        assertThat(control.error).isNotNull()
    }

    @Test
    fun `validate stops at first failing validator`() {
        val control = FormControl.create(
            "",
            listOf(
                { ValidationError("first", "first error") },
                { ValidationError("second", "second error") },
            ),
        )
        control.markAsDirty()

        control.validate()

        assertThat(control.error?.type).isEqualTo("first")
    }

    @Test
    fun `validate clears error when all validators pass`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )
        control.onValueChange("") // error set
        assertThat(control.error).isNotNull()

        control.onValueChange("valid") // error cleared

        assertThat(control.error).isNull()
    }

    // -------------------------------------------------------------------------
    // addValidator / removeValidator
    // -------------------------------------------------------------------------

    @Test
    fun `addValidator adds and triggers validation`() {
        val control = FormControl.create<String>("", emptyList())
        control.onValueChange("") // dirty

        control.addValidator { value ->
            if (value.isEmpty()) ValidationError("required", "required") else null
        }

        assertThat(control.error).isNotNull()
    }

    @Test
    fun `removeValidator removes validator by type`() {
        val control = FormControl.create(
            "",
            listOf { ValidationError("custom", "custom error") },
        )
        control.onValueChange("") // dirty, error set

        control.removeValidator("custom")

        assertThat(control.error).isNull()
    }

    // -------------------------------------------------------------------------
    // isValueValid
    // -------------------------------------------------------------------------

    @Test
    fun `isValueValid returns true when all validators pass`() {
        val control = FormControl.create(
            "hello",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )

        assertThat(control.isValueValid()).isTrue()
    }

    @Test
    fun `isValueValid returns false when a validator fails`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )

        assertThat(control.isValueValid()).isFalse()
    }

    @Test
    fun `isValueValid ignores touched and dirty state`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )
        // Not touched, not dirty — but isValueValid should still check validators
        assertThat(control.touched).isFalse()
        assertThat(control.dirty).isFalse()

        assertThat(control.isValueValid()).isFalse()
    }

    // -------------------------------------------------------------------------
    // setValue
    // -------------------------------------------------------------------------

    @Test
    fun `setValue updates value and marks dirty without triggering callbacks`() {
        var callbackInvoked = false
        val control = FormControl.create("initial")
        control.onValueChangeListener { _, _ -> callbackInvoked = true }

        control.setValue("new")

        assertThat(control.value).isEqualTo("new")
        assertThat(control.dirty).isTrue()
        assertThat(callbackInvoked).isFalse()
    }

    // -------------------------------------------------------------------------
    // markAsTouched / markAsDirty / forceShowError
    // -------------------------------------------------------------------------

    @Test
    fun `markAsTouched sets touched to true`() {
        val control = FormControl.create("")

        control.markAsTouched()

        assertThat(control.touched).isTrue()
    }

    @Test
    fun `markAsDirty sets dirty to true`() {
        val control = FormControl.create("")

        control.markAsDirty()

        assertThat(control.dirty).isTrue()
    }

    @Test
    fun `forceShowError marks control as touched`() {
        val control = FormControl.create("")

        control.forceShowError()

        assertThat(control.touched).isTrue()
    }

    // -------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------

    @Test
    fun `reset restores initial value and clears state`() {
        val control = FormControl.create(
            "initial",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )
        control.onValueChange("")
        control.onBlur()
        assertThat(control.dirty).isTrue()
        assertThat(control.touched).isTrue()
        assertThat(control.error).isNotNull()

        control.reset()

        assertThat(control.value).isEqualTo("initial")
        assertThat(control.dirty).isFalse()
        assertThat(control.touched).isFalse()
        assertThat(control.error).isNull()
        assertThat(control.pending).isFalse()
    }

    @Test
    fun `reset with new initial value uses that value`() {
        val control = FormControl.create("old")

        control.reset("brand new")

        assertThat(control.value).isEqualTo("brand new")
    }

    @Test
    fun `reset suppresses next onBlur to prevent spurious error`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )
        control.onValueChange("valid")
        control.onBlur()

        control.reset()
        control.onBlur() // immediate blur after reset (e.g., composable teardown)

        assertThat(control.touched).isFalse()
        assertThat(control.error).isNull()
    }

    @Test
    fun `reset suppression is consumed after one onBlur`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )
        control.reset()
        control.onBlur() // first blur — suppressed
        assertThat(control.touched).isFalse()

        control.markAsDirty()
        control.onBlur() // second blur — NOT suppressed

        assertThat(control.touched).isTrue()
    }

    // -------------------------------------------------------------------------
    // MA-3128: onValueChange clears suppressNextBlurTouch
    // -------------------------------------------------------------------------

    @Test
    fun `onValueChange after reset clears suppress flag so onBlur validates normally`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value == "9990") ValidationError("max", "value too high") else null
            },
        )

        control.reset() // sets suppressNextBlurTouch = true
        control.onValueChange("9990") // clears suppress flag, sets dirty, error set

        assertThat(control.error).isNotNull()

        control.onBlur() // should NOT suppress — flag was cleared by onValueChange

        assertThat(control.touched).isTrue()
        assertThat(control.error).isNotNull()
        assertThat(control.error?.type).isEqualTo("max")
    }

    @Test
    fun `without value change after reset onBlur still suppresses`() {
        val control = FormControl.create(
            "",
            listOf { value ->
                if (value.isEmpty()) ValidationError("required", "required") else null
            },
        )

        control.reset()
        // No onValueChange — suppress flag remains
        control.onBlur()

        assertThat(control.touched).isFalse()
        assertThat(control.error).isNull()
    }

    // -------------------------------------------------------------------------
    // FormGroup
    // -------------------------------------------------------------------------

    data class TestControls(
        val name: FormControl<String>,
        val email: FormControl<String>,
    )

    @Test
    fun `FormGroup isValid returns true when all controls are valid`() {
        val controls = TestControls(
            name = FormControl.create(
                "John",
                listOf { value ->
                    if (value.isEmpty()) ValidationError("required", "required") else null
                },
            ),
            email = FormControl.create(
                "john@test.com",
                listOf { value ->
                    if (value.isEmpty()) ValidationError("required", "required") else null
                },
            ),
        )
        val group = FormGroup(controls)

        assertThat(group.isValid).isTrue()
    }

    @Test
    fun `FormGroup isValid returns false when any control is invalid`() {
        val controls = TestControls(
            name = FormControl.create("John"),
            email = FormControl.create(
                "",
                listOf { value ->
                    if (value.isEmpty()) ValidationError("required", "required") else null
                },
            ),
        )
        val group = FormGroup(controls)

        assertThat(group.isValid).isFalse()
    }

    @Test
    fun `FormGroup markAllAsTouched touches all controls`() {
        val controls = TestControls(
            name = FormControl.create(""),
            email = FormControl.create(""),
        )
        val group = FormGroup(controls)

        group.markAllAsTouched()

        assertThat(controls.name.touched).isTrue()
        assertThat(controls.email.touched).isTrue()
    }

    @Test
    fun `FormGroup resetForm resets all controls`() {
        val controls = TestControls(
            name = FormControl.create("initial"),
            email = FormControl.create("email@test.com"),
        )
        val group = FormGroup(controls)
        controls.name.onValueChange("changed")
        controls.email.onValueChange("changed@test.com")

        group.resetForm()

        assertThat(controls.name.value).isEqualTo("initial")
        assertThat(controls.email.value).isEqualTo("email@test.com")
        assertThat(controls.name.dirty).isFalse()
        assertThat(controls.email.dirty).isFalse()
    }

    @Test
    fun `FormGroup forceShowAllErrors marks all as touched`() {
        val controls = TestControls(
            name = FormControl.create(""),
            email = FormControl.create(""),
        )
        val group = FormGroup(controls)

        group.forceShowAllErrors()

        assertThat(controls.name.touched).isTrue()
        assertThat(controls.email.touched).isTrue()
    }

    @Test
    fun `FormGroup isDirty returns true when any control is dirty`() {
        val controls = TestControls(
            name = FormControl.create(""),
            email = FormControl.create(""),
        )
        val group = FormGroup(controls)
        controls.name.onValueChange("changed")

        assertThat(group.isDirty).isTrue()
    }

    @Test
    fun `FormGroup isDirty returns false when no controls are dirty`() {
        val controls = TestControls(
            name = FormControl.create(""),
            email = FormControl.create(""),
        )
        val group = FormGroup(controls)

        assertThat(group.isDirty).isFalse()
    }
}
