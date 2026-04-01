package com.dmdbrands.gurus.weight.resources

import com.dmdbrands.gurus.weight.R

object AppIcons {
  object Default {
    val Close = R.drawable.ic_close
    val closeFilled = R.drawable.filled_close
    val Delete = R.drawable.ic_delete
    val EyeOpened = R.drawable.ic_eye_open
    val EyeClosed = R.drawable.ic_eye_close
    val Banner = R.drawable.weight_gurus_banner
    val Logo = R.drawable.ic_logo
    val BrandLogo = R.drawable.brand_logo
    val WgLogo = R.drawable.wg_logo
    val Appsync = R.drawable.ic_appsync
    val Graph = R.drawable.ic_graph
    val History = R.drawable.ic_history
    val Settings = R.drawable.ic_settings
    val Placeholder = R.drawable.me_placeholder
    val ggLogo = R.drawable.gg_logo
    val RightCaret = R.drawable.ic_right_caret
    val ChevronDown = R.drawable.ic_chevron_down
    val profile = R.drawable.ic_profile
    val DragHandler = R.drawable.ic_drag_handle
    val Exclamation = R.drawable.ic_exclamation
    val ErrorIndicator = R.drawable.error_indicator
    val WifiIndicator = R.drawable.wifi_indicator
    val BluetoothIndicator = R.drawable.bluetooth_indicator
    val ModalNumber = R.drawable.modal_number
    val ScalePlaceholder = R.raw.placeholder_scale
    val WeightOnlyMode = R.drawable.ic_weight_only
    val WeightOnlyModeScale = R.drawable.scale_0412_weight_only
    val Export = R.drawable.export_info
    val Plus = R.drawable.ic_plus
    val Minus = R.drawable.ic_minus
  }

  object Outlined {
    val Help = R.drawable.ic_info
    val Close = R.drawable.ic_close_outlined
    val PlusCircle = R.drawable.ic_plus_outlined
    val CheckedCircle = R.drawable.ic_circle_check_outlined
    val MinusCircle = R.drawable.ic_circle_minus_outlined
  }

  object Filled {
    val Plus = R.drawable.ic_plus_selected
    val Graph = R.drawable.ic_graph_selected
    val History = R.drawable.ic_history_selected
    val Settings = R.drawable.ic_settings_selected
    val Close = R.drawable.ic_popup_close
    val CaretDown = R.drawable.ic_filled_caret_down
    val AddCircle = R.drawable.ic_plus_circle_filled
    val MinusCircle = R.drawable.ic_minus_circle_filled
  }

  object Selection {
    val CircleUnselected = R.drawable.ic_circle_outline
    val CircleSelected = R.drawable.ic_check_circle_filled
    val CircleClosed = R.drawable.ic_circle_close_filled
  }

  object Metrics {
    val BodyFat = R.drawable.ic_body_fat
    val MuscleMass = R.drawable.ic_muscle_mass
    val Water = R.drawable.ic_water
    val Bmi = R.drawable.ic_bmi
    val Bmr = R.drawable.ic_bmr
    val MetabolicAge = R.drawable.ic_metabolic_age
    val Protein = R.drawable.ic_protein
    val Pulse = R.drawable.ic_pulse
    val SkeletalMusclePercent = R.drawable.ic_skeletal_muscle
    val SubcutaneousFat = R.drawable.ic_subcutaneous_fat
    val VisceralFat = R.drawable.ic_visceral_fat
    val BoneMass = R.drawable.ic_bone_mass
  }

  object Connection {
    val Bluetooth = R.drawable.ic_bluetooth
    val Wifi = R.drawable.ic_wifi
    val BluetoothWifi = R.drawable.ic_bluetooth_wifi
    val AppSync = R.drawable.ic_app_sync
  }

  object Milestone {
    val Streak = R.drawable.streak
    val Bolt = R.drawable.bolt
  }

  object Integrations {
    val Fitbit = R.drawable.ic_fitbit_logo
    val My_Fitness_Pal = R.drawable.ic_my_fitnesspal_logo
    val Health_Connect_Logo = R.drawable.health_connect_logo
    val Health_Connect_Off = R.drawable.health_connect_off
    val No_Permission = R.drawable.no_permission
    val Full_Permission = R.drawable.full_permission
    val HC_Homepage = R.drawable.hc_homepage
    val User_Conflict = R.drawable.user_conflict
    val Permission_Failed = R.drawable.integration_failed
  }

  object Setup {
    val Accuchecked = R.drawable.accuchecked
    val AccucheckLogo = R.drawable.accucheck_logo
    val StepOnGif = R.raw.step_on
    fun StepOnGif(sku: String): Int {
      return when (sku) {
        "0375", "0376", "0380", "0382" -> R.raw.step_on_scale
        else -> R.raw.step_on_scale
      }
    }

    fun PairModeGif(sku: String): Int {
      return when (sku) {
        "0375" -> R.raw.start_pair_mode_0375
        "0376" -> R.raw.start_pair_mode_0376
        "0380" -> R.raw.start_pair_mode_0380
        "0382" -> R.raw.start_pair_mode_0382
        else -> R.raw.start_pair_mode_0375
      }
    }

    fun SetUserGif(sku: String): Int {
      return when (sku) {
        "0375" -> R.raw.set_user_number_0375
        "0376" -> R.raw.set_user_number_0376
        "0380" -> R.raw.set_user_number_0380
        "0382" -> R.raw.set_user_number_0382
        else -> R.raw.set_user_number_0375
      }
    }

    fun ShowWifiModeFilled(userNumber: Int): Int {
      return when (userNumber) {
        1 -> R.raw.u1_filled_0384
        2 -> R.raw.u2_filled_0384
        3 -> R.raw.u3_filled_0384
        4 -> R.raw.u4_filled_0384
        5 -> R.raw.u5_filled_0384
        6 -> R.raw.u6_filled_0384
        7 -> R.raw.u7_filled_0384
        8 -> R.raw.u8_filled_0384
        else -> R.raw.u1_filled_0384
      }
    }

    fun ShowWifiModeOutlined(userNumber: Int): Int {
      return when (userNumber) {
        1 -> R.raw.u1_outlined_0384
        2 -> R.raw.u2_outlined_0384
        3 -> R.raw.u3_outlined_0384
        4 -> R.raw.u4_outlined_0384
        5 -> R.raw.u5_outlined_0384
        6 -> R.raw.u6_outlined_0384
        7 -> R.raw.u7_outlined_0384
        8 -> R.raw.u8_outlined_0384
        else -> R.raw.u1_outlined_0384
      }
    }

    val MetricCard = R.drawable.ic_card_grid
    val Graph = R.drawable.ic_graph_bar
    val Scale = R.drawable.ic_scale
    val UserNameScale = R.drawable.scale_0412_user_name
    val AppSyncNavBar = R.drawable.app_bottom_bar
    val SetupCompleteCheck = R.drawable.setup_complete_check
    val WifiAPMode = R.drawable.wifi_ap_mode
    val WifiSmartConnect = R.drawable.wifi_smart_connect
    val WifiAPModeSelected = R.drawable.wifi_ap_mode_filled
    val WifiSmartConnectSelected = R.drawable.wifi_smart_connect_filled
    val WifiPair = R.raw.pair_0384
    val WifiPair0396 = R.raw.set_pair_mode_0396
    val WifiStepOnApMode = R.raw.step_on_scale
    val WifiStepOn = R.raw.step_on
    val WifiCountOn = R.raw.scale_counts_wifi
    val wifiAPModeStepOn = R.drawable.stepon_filled_0396
    val WifiAPModeFilled0384 = R.drawable.ap_filled_0384
    val WifiAPModeOutlined0384 = R.drawable.ap_outlined_0384

    val BabyScale = R.drawable.ic_baby_scale
    val BpmScale = R.drawable.ic_bpm_scale
    val WeightScale = R.drawable.ic_weight_scale
  }
}
