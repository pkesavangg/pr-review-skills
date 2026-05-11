package com.dmdbrands.gurus.weight.features.signup.model

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations.weightValidator
import com.dmdbrands.gurus.weight.features.common.helper.form.Validator
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.domain.enums.Gender
import com.dmdbrands.gurus.weight.features.signup.strings.PickDeviceStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * All form controls for the signup process in a single FormGroup.
 */
data class SignupFormControls(
  val device: FormControl<String>,
  val firstName: FormControl<String>,
  val lastName: FormControl<String>,
  val email: FormControl<String>,
  val password: FormControl<String>,
  val confirmPassword: FormControl<String>,
  val zipcode: FormControl<String>,
  val birthday: FormControl<DateTimeValue>,
  val sex: FormControl<String>,
  val height: FormControl<HeightInput>,
  val goalType: FormControl<String>,
  val currentWeight: FormControl<String>,
  val goalWeight: FormControl<String>,
  val useMetric: FormControl<Boolean>,
) {
  companion object {
    /**
     * Creates confirm password validator for password matching
     * Only the confirm password field should show mismatch errors
     */
    private fun createConfirmPasswordValidator(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
      FormValidations.confirmPasswordMatch(formGroup)

    /**
     * Creates a dynamic weight validator that updates based on metric setting
     */
    private fun createDynamicWeightValidator(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
      { value ->
        val isMetric = formGroup().controls.useMetric.value
        val weightUnit = if (isMetric) WeightUnit.KG else WeightUnit.LB
        weightValidator(weightUnit).invoke(value)
      }

    /**
     * Requires current weight unless goal type is MAINTAIN. When MAINTAIN, the field may be
     * hidden/disabled and should not block validation/submission.
     */
    private fun createRequiredCurrentWeightValidator(
      formGroup: () -> FormGroup<SignupFormControls>,
    ): Validator<String> = { value ->
      val currentGoalType = formGroup().controls.goalType.value
      if (currentGoalType == GoalType.MAINTAIN.value) {
        null
      } else {
        FormValidations.required(SignupStrings.Error.required).invoke(value)
      }
    }

    /**
     * Creates a new instance of SignupFormControls with default values and validations.
     */
    fun create(): SignupFormControls {
      val signupData = SignupData()
      // Create the SignupFormControls first
      val controls =
        SignupFormControls(
          device =
            FormControl.create(
              "",
              listOf(FormValidations.required()),
            ),
          firstName =
            FormControl.create(
              signupData.firstName,
              listOf(
                FormValidations.required(SignupStrings.Error.blank),
                FormValidations.noWhiteSpace(),
                FormValidations.maxLength(
                  AppValidatorConfig.Name.MAX_LENGTH,
                  customMessage = SignupStrings.Error.maxName,
                ),
              ),
            ),
          lastName =
            FormControl.create(
              signupData.lastName,
              listOf(
                FormValidations.required(SignupStrings.Error.blank),
                FormValidations.noWhiteSpace(),
                FormValidations.maxLength(
                  AppValidatorConfig.Name.MAX_LENGTH,
                  customMessage = SignupStrings.Error.maxName,
                ),
              ),
            ),
          email =
            FormControl.create(
              signupData.email,
              listOf(
                FormValidations.required(LoginStrings.Errors.emailBlank),
                FormValidations.email(),
                FormValidations.maxLength(
                  AppValidatorConfig.Email.MAX_LENGTH,
                  customMessage = LoginStrings.Errors.maxLengthEmail,
                ),
              ),
            ),
          password =
            FormControl.create(
              signupData.password,
              listOf(
                FormValidations.required(LoginStrings.Errors.emailBlank),
                FormValidations.minLength(
                  AppValidatorConfig.Password.MIN_LENGTH,
                  "password",
                  customMessage = LoginStrings.Errors.passwordlen,
                  allowSpaces = true,
                ),
                FormValidations.maxLength(
                  AppValidatorConfig.Password.MAX_LENGTH,
                  customMessage = LoginStrings.Errors.maxLengthPassword,
                  allowSpaces = true,
                ),
              ),
            ),
          confirmPassword =
            FormControl.create(
              signupData.confirmPassword,
              listOf(
                FormValidations.required(LoginStrings.Errors.emailBlank),
                FormValidations.minLength(
                  AppValidatorConfig.Password.MIN_LENGTH,
                  "confirmPassword",
                  customMessage = LoginStrings.Errors.passwordlen,
                  allowSpaces = true,
                ),
                FormValidations.maxLength(
                  AppValidatorConfig.Password.MAX_LENGTH,
                  customMessage = LoginStrings.Errors.maxLengthPassword,
                  allowSpaces = true,
                ),
              ),
            ),
          zipcode =
            FormControl.create(
              signupData.zipcode,
              listOf(
                FormValidations.required(LoginStrings.Errors.emailBlank),
                FormValidations.noWhiteSpace(),
                FormValidations.maxLength(
                  AppValidatorConfig.ZipCode.MAX_LENGTH,
                  customMessage = SignupStrings.Error.maxZipcode,
                ),
              ),
            ),
          birthday =
            FormControl.create(
              DateTimeValue.Date(
                DateTimeValue.getEpochMillisFromDateString(
                  AppValidatorConfig.DateOfBirth.DEFAULT_VALUE,
                ),
              ),
              listOf(),
            ),
          sex =
            FormControl.create(
              signupData.sex,
              listOf(FormValidations.required()),
            ),
          height =
            FormControl.create(
              signupData.height, // Default height 170 cm
              emptyList(),
            ),
          goalType =
            FormControl.create(
              signupData.goalType,
              listOf(FormValidations.required()),
            ),
          currentWeight =
            FormControl.create(
              signupData.currentWeight,
              emptyList(), // Dynamic validators will be added after formGroup creation
            ),
          goalWeight =
            FormControl.create(
              signupData.goalWeight,
              listOf(FormValidations.required(SignupStrings.Error.required)), // Dynamic validator will be added after formGroup creation
            ),
          useMetric =
            FormControl.create(
              false,
              emptyList(),
            ),
        )

      val formGroup = FormGroup(controls)

      // Add dynamic weight validators that update based on metric setting
      controls.currentWeight.addValidator(createDynamicWeightValidator { formGroup })
      controls.currentWeight.addValidator(createRequiredCurrentWeightValidator { formGroup })
      controls.goalWeight.addValidator(createDynamicWeightValidator { formGroup })

      // Add weight match validator for lose/gain goal types
      controls.goalWeight.addValidator(
        FormValidations.weightMatchValidator(
          currentWeightControl = controls.currentWeight,
          goalTypeControl = controls.goalType,
        ),
      )

      // Add password matching validation only to confirm password field
      controls.confirmPassword.addValidator(createConfirmPasswordValidator { formGroup })
      // Set up validation trigger - when password changes, validate confirm password
      controls.password.onValueChangeListener { _, _ ->
        // When password changes, trigger validation on confirm password if it has a value
        if (controls.confirmPassword.value.isNotEmpty()) {
          controls.confirmPassword.validate()
        }
      }

      // Set up validation triggers for weight match validator
      // When goal type changes, validate goal weight
      controls.goalType.onValueChangeListener { _, _ ->
        if (controls.goalWeight.value.isNotEmpty() && controls.currentWeight.value.isNotEmpty()) {
          controls.goalWeight.validate()
        }
      }

      // Track original entered weight values to prevent rounding loss on unit toggle round-trips.
      // Each pair stores (rawValue, wasMetric): the user's typed value and the unit it was entered in.
      // Toggling back to the original unit restores the exact input instead of re-converting.
      // NOTE: These closure-local vars are invisible to MVI state — not observable, serializable,
      // or restorable on process death. After a process kill, toggling units will re-convert with
      // potential rounding loss. Acceptable for transient signup form state.
      var originalCurrentWeight: Pair<String, Boolean>? = null
      var originalGoalWeight: Pair<String, Boolean>? = null
      var isUnitToggleUpdate = false

      // When current weight changes, validate goal weight (for lose/gain goals)
      controls.currentWeight.onValueChangeListener { _, _ ->
        if (!isUnitToggleUpdate) {
          originalCurrentWeight = null
        }
        val goalType = controls.goalType.value
        if ((goalType == GoalType.LOSE.value || goalType == GoalType.GAIN.value || goalType == GoalType.LOSE_GAIN.value) &&
          controls.goalWeight.value.isNotEmpty()
        ) {
          controls.goalWeight.validate()
        }
      }

      controls.goalWeight.onValueChangeListener { _, _ ->
        if (!isUnitToggleUpdate) {
          originalGoalWeight = null
        }
      }

      // Set up metric toggle validation trigger
      controls.useMetric.onValueChangeListener { oldValue, newValue ->
        isUnitToggleUpdate = true
        try {
          // Convert current weight when switching units
          if (controls.currentWeight.value.isNotEmpty()) {
            val orig = originalCurrentWeight
            if (orig != null && orig.second == newValue) {
              // Toggling back to the unit the user originally typed — restore exact value
              controls.currentWeight.onValueChange(orig.first)
              originalCurrentWeight = null
            } else {
              if (orig == null) {
                originalCurrentWeight = Pair(controls.currentWeight.value, oldValue)
              }
              val convertedCurrentWeight =
                convertWeightValue(controls.currentWeight.value, fromMetric = oldValue, toMetric = newValue)
              controls.currentWeight.onValueChange(convertedCurrentWeight)
            }
            controls.currentWeight.validate()
          } else {
            controls.currentWeight.validate()
          }

          // Convert goal weight when switching units
          if (controls.goalWeight.value.isNotEmpty()) {
            val orig = originalGoalWeight
            if (orig != null && orig.second == newValue) {
              controls.goalWeight.onValueChange(orig.first)
              originalGoalWeight = null
            } else {
              if (orig == null) {
                originalGoalWeight = Pair(controls.goalWeight.value, oldValue)
              }
              val convertedGoalWeight =
                convertWeightValue(controls.goalWeight.value, fromMetric = oldValue, toMetric = newValue)
              controls.goalWeight.onValueChange(convertedGoalWeight)
            }
            controls.goalWeight.validate()
          } else {
            controls.goalWeight.validate()
          }
        } finally {
          isUnitToggleUpdate = false
        }

        // Update height input based on metric setting
        // Use proper rounding to preserve precision and prevent accumulation errors
        val currentHeight = controls.height.value
        val newHeight =
          if (newValue) {
            // Convert to metric (cm)
            when (currentHeight) {
              is HeightInput.FtIn -> {
                val totalInches = (currentHeight.feet * 12) + currentHeight.inches
                // Use roundToInt() instead of toInt() to round to nearest instead of truncating
                val cm = (totalInches * 2.54).roundToInt()
                HeightInput.Cm(cm)
              }

              is HeightInput.Cm -> currentHeight // Already metric
            }
          } else {
            // Convert to imperial (ft/in)
            when (currentHeight) {
              is HeightInput.Cm -> {
                // Use roundToInt() for proper rounding
                val totalInches = (currentHeight.value / 2.54).roundToInt()
                val feet = totalInches / 12
                val inches = totalInches % 12
                HeightInput.FtIn(feet, inches)
              }

              is HeightInput.FtIn -> currentHeight // Already imperial
            }
          }
        controls.height.onValueChange(newHeight)
      }

      return controls
    }
  }
}

/**
 * Baby-specific state, null when not on baby path.
 */
@Stable
data class BabyState(
  val babies: List<BabyProfile> = emptyList(),
  val babyForm: BabyFormControls = BabyFormControls.create(),
  val editingBabyId: String? = null,
)

/**
 * State for Signup screen with dynamic step list based on device selection.
 */
@Stable
data class SignupState(
  val form: FormGroup<SignupFormControls>,
  val steps: ImmutableList<SignupStep> = COMMON_STEPS,
  val currentStep: SignupStep = SignupStep.NAME,
  val isLoading: Boolean = false,
  val error: String? = null,
  val goalSkipped: Boolean = false,
  val babyState: BabyState? = null,
  val registeredDevices: Set<ProductType> = emptySet(),
) : IReducer.State {
  val currentStepIndex: Int get() = steps.indexOf(currentStep).coerceAtLeast(0)
  val isFirstStep: Boolean get() = currentStepIndex == 0
  val isLastStep: Boolean get() = currentStepIndex == steps.size - 1
  val accountCreated: Boolean get() = registeredDevices.isNotEmpty()

  /**
   * True when the next step in [steps] is a Ready terminal screen — i.e.
   * the user has reached the form-completion step for this device pass.
   * The reducer short-circuits Next on this step; SignupViewModel runs the
   * signup/side-effect work and dispatches RegisterDevice on success.
   */
  val isFinalDataStep: Boolean
    get() {
      val next = steps.getOrNull(currentStepIndex + 1)
      return next == SignupStep.DEVICE_READY || next == SignupStep.ALL_DEVICES_READY
    }
  val showSkipButton: Boolean
    get() = currentStep == SignupStep.GOAL
      || currentStep == SignupStep.ADD_BABY
      || (currentStep == SignupStep.BABY_ADDED && babyState?.babies.isNullOrEmpty())
  val progress: Float get() = (currentStepIndex + 1f) / steps.size

  val isCurrentStepValid: Boolean
    get() =
      when (currentStep) {
        SignupStep.NAME ->
          form.controls.firstName.isValueValid() && form.controls.lastName.isValueValid()

        SignupStep.EMAIL ->
          form.controls.email.isValueValid()

        SignupStep.BIRTHDAY ->
          form.controls.birthday.isValueValid()

        SignupStep.PICK_DEVICE ->
          form.controls.device.isValueValid()

        SignupStep.GENDER ->
          form.controls.sex.isValueValid()

        SignupStep.HEIGHT -> true

        SignupStep.GOAL -> {
          if (goalSkipped) {
            true
          } else {
            val controls = form.controls
            val goalTypeValid = controls.goalType.isValueValid()
            val goalWeightValid = controls.goalWeight.isValueValid()
            val currentWeightValid =
              if (controls.goalType.value == GoalType.MAINTAIN.value) true
              else controls.currentWeight.isValueValid()
            goalTypeValid && goalWeightValid && currentWeightValid
          }
        }

        SignupStep.ADD_BABY -> {
          babyState?.let { bs ->
            bs.babyForm.name.isValueValid()
              && bs.babyForm.birthday.isValueValid()
              && bs.babyForm.biologicalSex.isValueValid()
          } ?: false
        }

        SignupStep.BABY_ADDED ->
          babyState?.babies?.isNotEmpty() == true

        SignupStep.PASSWORD ->
          form.controls.password.isValueValid()
            && form.controls.confirmPassword.isValueValid()
            && form.controls.zipcode.isValueValid()

        SignupStep.DEVICE_READY,
        SignupStep.ALL_DEVICES_READY -> true
      }

  companion object {
    val COMMON_STEPS = persistentListOf(
      SignupStep.NAME, SignupStep.EMAIL,
      SignupStep.BIRTHDAY, SignupStep.PICK_DEVICE,
      SignupStep.PASSWORD,
    )

    /**
     * Builds the ordered step list for the current signup pass.
     *
     * On the first pass (registeredDevices is empty), the full common head
     * (NAME → EMAIL → BIRTHDAY → PICK_DEVICE) and PASSWORD are included.
     * On subsequent passes, the head collapses to just PICK_DEVICE and
     * PASSWORD is omitted because the account already exists. The
     * device-specific path also omits any slide whose data was already
     * captured by a previously-registered device. The terminal step is
     * ALL_DEVICES_READY when this pass will register the final device.
     */
    fun stepsForDevice(
      device: ProductType?,
      registeredDevices: Set<ProductType> = emptySet(),
    ): ImmutableList<SignupStep> {
      // Loop placeholder: user tapped Connect Another but hasn't picked yet.
      if (device == null) return persistentListOf(SignupStep.PICK_DEVICE)

      val isFirstPass = registeredDevices.isEmpty()
      val isLastDevice = registeredDevices.size + 1 == ProductType.ALL.size
      val terminalStep =
        if (isLastDevice) SignupStep.ALL_DEVICES_READY else SignupStep.DEVICE_READY

      val sexCaptured =
        ProductType.MY_WEIGHT in registeredDevices
          || ProductType.BLOOD_PRESSURE in registeredDevices
      val heightCaptured = ProductType.MY_WEIGHT in registeredDevices
      val goalCaptured = ProductType.MY_WEIGHT in registeredDevices

      val head =
        if (isFirstPass) {
          listOf(
            SignupStep.NAME, SignupStep.EMAIL,
            SignupStep.BIRTHDAY, SignupStep.PICK_DEVICE,
          )
        } else {
          listOf(SignupStep.PICK_DEVICE)
        }

      val pathSteps = when (device) {
        ProductType.BABY -> listOf(SignupStep.ADD_BABY, SignupStep.BABY_ADDED)
        ProductType.BLOOD_PRESSURE -> buildList {
          if (!sexCaptured) add(SignupStep.GENDER)
        }
        ProductType.MY_WEIGHT -> buildList {
          if (!sexCaptured) add(SignupStep.GENDER)
          if (!heightCaptured) add(SignupStep.HEIGHT)
          if (!goalCaptured) add(SignupStep.GOAL)
        }
      }

      val tail = if (isFirstPass) listOf(SignupStep.PASSWORD, terminalStep) else listOf(terminalStep)

      return (head + pathSteps + tail).toPersistentList()
    }
  }
}

/**
 * Intents for Signup screen actions.
 */
sealed class SignupIntent : IReducer.Intent {
  object Next : SignupIntent()
  object Back : SignupIntent()
  object OnRequestBack : SignupIntent()
  object OpenHelpModal : SignupIntent()
  data class OpenURL(val url: String) : SignupIntent()
  object Skip : SignupIntent()
  data class ToggleMetric(val useMetric: Boolean) : SignupIntent()
  data class Error(val message: String) : SignupIntent()
  object Success : SignupIntent()
  data class UpdateGoalSkipped(val skipped: Boolean) : SignupIntent()

  /** Device selection — rebuilds step list and resets abandoned path data. */
  data class SelectDevice(val device: String) : SignupIntent()

  /** Baby flow intents */
  data class DeleteBaby(val babyId: String) : SignupIntent()
  data class EditBaby(val baby: BabyProfile) : SignupIntent()
  object AddAnotherBaby : SignupIntent()

  /** Multi-device loop intents (MA-3825) */
  object ConnectAnotherDevice : SignupIntent()
  object FinishSignup : SignupIntent()

  /**
   * Dispatched by SignupViewModel only after signup (first pass) or
   * device-specific side effects (loop pass) succeed. Reducer adds the
   * current device to [SignupState.registeredDevices] and advances to
   * the terminal Ready step.
   */
  object RegisterDevice : SignupIntent()
}

/**
 * Reducer for Signup screen state transitions.
 */
class SignupReducer : IReducer<SignupState, SignupIntent> {
  override fun reduce(
    state: SignupState,
    intent: SignupIntent,
  ): SignupState =
    when (intent) {
      is SignupIntent.Next -> {
        when {
          // Last data step before a Ready terminal — advancement is gated on
          // SignupViewModel dispatching RegisterDevice once the signup API
          // call (or device-specific side effects) completes successfully.
          state.isFinalDataStep -> state.copy(error = null)

          // On ADD_BABY: save baby and go to BABY_ADDED
          state.currentStep == SignupStep.ADD_BABY -> {
            val bs = state.babyState ?: return state
            val baby = BabyProfile(
              id = bs.editingBabyId ?: java.util.UUID.randomUUID().toString(),
              name = bs.babyForm.name.value,
              birthday = bs.babyForm.birthday.value,
              biologicalSex = Gender.entries.firstOrNull {
                it.value.equals(bs.babyForm.biologicalSex.value, ignoreCase = true)
              },
              birthLength = bs.babyForm.birthLength.value,
              birthWeight = bs.babyForm.birthWeight.value,
            )
            val updatedBabies = if (bs.editingBabyId != null) {
              bs.babies.map { if (it.id == bs.editingBabyId) baby else it }
            } else {
              bs.babies + baby
            }
            state.copy(
              babyState = BabyState(babies = updatedBabies),
              currentStep = SignupStep.BABY_ADDED,
            )
          }
          !state.isLastStep -> {
            val updatedState =
              if (state.currentStep == SignupStep.GOAL) state.copy(goalSkipped = false)
              else state
            val nextIndex = (updatedState.currentStepIndex + 1)
              .coerceAtMost(updatedState.steps.lastIndex)
            updatedState.copy(currentStep = updatedState.steps[nextIndex], error = null)
          }
          else -> state.copy(isLoading = false, error = null)
        }
      }

      is SignupIntent.Back -> {
        var updatedState = if (state.currentStep == SignupStep.GOAL) {
          state.copy(goalSkipped = false)
        } else {
          state
        }
        // Clear stale editingBabyId when leaving ADD_BABY via Back
        if (state.currentStep == SignupStep.ADD_BABY && state.babyState?.editingBabyId != null) {
          updatedState = updatedState.copy(
            babyState = updatedState.babyState?.copy(
              babyForm = BabyFormControls.create(),
              editingBabyId = null,
            ),
          )
        }
        // Strictly linear back navigation
        val prevIndex = (updatedState.currentStepIndex - 1).coerceAtLeast(0)
        updatedState.copy(currentStep = updatedState.steps[prevIndex], error = null)
      }

      is SignupIntent.Skip -> {
        when {
          state.currentStep == SignupStep.GOAL -> {
            state.form.controls.currentWeight.reset()
            state.form.controls.goalWeight.reset()
            state.form.controls.goalType.reset()
            if (state.registeredDevices.isEmpty()) {
              // First pass: advance to PASSWORD (next step in steps list).
              val nextIndex = (state.currentStepIndex + 1).coerceAtMost(state.steps.lastIndex)
              state.copy(currentStep = state.steps[nextIndex], goalSkipped = true, error = null)
            } else {
              // Loop pass: no goal to create, no other side effects needed.
              // Register the device immediately and jump to the terminal.
              val device = ProductType.fromId(state.form.controls.device.value)
              if (device == null) {
                state.copy(goalSkipped = true, error = null)
              } else {
                state.copy(
                  registeredDevices = state.registeredDevices + device,
                  currentStep = state.steps.last(),
                  goalSkipped = true,
                  error = null,
                )
              }
            }
          }
          state.currentStep == SignupStep.ADD_BABY
            || state.currentStep == SignupStep.BABY_ADDED -> {
            if (state.registeredDevices.isEmpty()) {
              // First pass: skip past baby steps to PASSWORD.
              state.copy(
                babyState = BabyState(),
                currentStep = SignupStep.PASSWORD,
                error = null,
              )
            } else {
              // Loop pass: no babies to persist, no other side effects.
              // Register the device immediately and jump to the terminal.
              val device = ProductType.fromId(state.form.controls.device.value)
              if (device == null) {
                state.copy(babyState = BabyState(), error = null)
              } else {
                state.copy(
                  babyState = BabyState(),
                  registeredDevices = state.registeredDevices + device,
                  currentStep = state.steps.last(),
                  error = null,
                )
              }
            }
          }
          else -> state
        }
      }

      is SignupIntent.SelectDevice -> {
        val productType = ProductType.fromId(intent.device)
        val newSteps = SignupState.stepsForDevice(productType, state.registeredDevices)
        val newBabyState = if (intent.device == PickDeviceStrings.Devices.BABY_SCALE)
          BabyState() else null

        // Reset abandoned path data only on the first pass — on loop iterations
        // the previously-captured shared data must be preserved.
        if (state.registeredDevices.isEmpty()
          && state.form.controls.device.value != intent.device
        ) {
          state.form.controls.sex.reset()
          state.form.controls.height.onValueChange(HeightInput.FtIn(5, 10))
          state.form.controls.currentWeight.reset()
          state.form.controls.goalWeight.reset()
          state.form.controls.goalType.onValueChange(GoalType.LOSE_GAIN.value)
        }

        state.form.controls.device.onValueChange(intent.device)
        state.copy(
          steps = newSteps,
          babyState = newBabyState,
          goalSkipped = false,
          error = null,
        )
      }

      is SignupIntent.DeleteBaby -> {
        val bs = state.babyState ?: return state
        state.copy(babyState = bs.copy(babies = bs.babies.filter { it.id != intent.babyId }))
      }

      is SignupIntent.EditBaby -> {
        val bs = state.babyState ?: return state
        val baby = intent.baby
        val newForm = BabyFormControls.create()
        newForm.name.onValueChange(baby.name)
        if (baby.birthday != null) newForm.birthday.onValueChange(baby.birthday)
        val sexValue = baby.biologicalSex?.value?.replaceFirstChar { it.uppercase() } ?: ""
        if (sexValue.isNotEmpty()) newForm.biologicalSex.onValueChange(sexValue)
        if (baby.birthLength.isNotEmpty()) newForm.birthLength.onValueChange(baby.birthLength)
        if (baby.birthWeight.isNotEmpty()) newForm.birthWeight.onValueChange(baby.birthWeight)
        state.copy(
          babyState = bs.copy(babyForm = newForm, editingBabyId = baby.id),
          currentStep = SignupStep.ADD_BABY,
        )
      }

      is SignupIntent.AddAnotherBaby -> {
        val bs = state.babyState ?: return state
        state.copy(
          babyState = bs.copy(babyForm = BabyFormControls.create(), editingBabyId = null),
          currentStep = SignupStep.ADD_BABY,
        )
      }

      is SignupIntent.ConnectAnotherDevice -> {
        // Reset the device control so the loop pass starts unselected.
        // Shared form data (sex, height, goal) is intentionally preserved.
        state.form.controls.device.reset()
        state.copy(
          currentStep = SignupStep.PICK_DEVICE,
          steps = SignupState.stepsForDevice(
            device = null,
            registeredDevices = state.registeredDevices,
          ),
          error = null,
        )
      }

      is SignupIntent.RegisterDevice -> {
        val device = ProductType.fromId(state.form.controls.device.value)
          ?: return state.copy(error = "Missing device")
        val newRegistered = state.registeredDevices + device
        // Always jump to the terminal Ready step — the user may dispatch
        // RegisterDevice from the final data step (Next on PASSWORD/GOAL/
        // BABY_ADDED) or from earlier (Skip on ADD_BABY in a loop pass).
        state.copy(
          registeredDevices = newRegistered,
          currentStep = state.steps.last(),
          isLoading = false,
          error = null,
        )
      }

      is SignupIntent.FinishSignup -> state.copy(isLoading = false, error = null)

      is SignupIntent.OnRequestBack -> state.copy(isLoading = false, error = null)
      is SignupIntent.OpenHelpModal -> state.copy(isLoading = false, error = null)
      is SignupIntent.Error -> state.copy(isLoading = false, error = intent.message)
      is SignupIntent.Success -> state.copy(isLoading = false, error = null)
      is SignupIntent.UpdateGoalSkipped -> state.copy(goalSkipped = intent.skipped)

      is SignupIntent.ToggleMetric -> {
        state.form.controls.useMetric.onValueChange(intent.useMetric)
        state.copy(error = null)
      }

      else -> state
    }
}

/**
 * Extension functions for SignupFormControls to support metric conversions
 */

/**
 * Converts weight value between units when metric setting changes.
 * Preserves one decimal place precision to prevent accumulation errors.
 *
 * Note: Weight values are stored as integers where the last digit represents the decimal place.
 * For example, "605" represents 60.5, "550" represents 55.0.
 */
fun convertWeightValue(
  value: String,
  fromMetric: Boolean,
  toMetric: Boolean,
): String {
  if (value.isBlank()) return value

  return try {
    // Convert integer format (e.g., "605") to decimal format (e.g., "60.5")
    val decimalValue = if (value.length > 1) {
      value.dropLast(1) + "." + value.takeLast(1)
    } else {
      "0." + value
    }

    val numericValue = decimalValue.toDoubleOrNull() ?: return value

    val convertedValue =
      when {
        // Converting FROM kg TO lbs: multiply by conversion factor
        fromMetric && !toMetric -> numericValue * 2.20462
        // Converting FROM lbs TO kg: divide by conversion factor
        !fromMetric && toMetric -> numericValue / 2.20462
        // No conversion needed
        else -> numericValue
      }

    // Round to one decimal place to preserve precision and prevent accumulation errors
    val roundedValue = round(convertedValue * 10) / 10.0

    // Convert back to integer format (e.g., 60.5 -> "605", 133.4 -> "1334")
    // Format as string with one decimal place first
    val formatted = String.format("%.1f", roundedValue)
    // Remove decimal point to get integer format (e.g., "60.5" -> "605")
    formatted.replace(".", "")
  } catch (e: Exception) {
    value
  }
}
