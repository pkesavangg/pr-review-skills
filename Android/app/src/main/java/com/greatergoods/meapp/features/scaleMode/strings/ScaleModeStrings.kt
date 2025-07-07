package com.greatergoods.meapp.features.scaleMode.strings

object ScaleModeStrings {
    const val Title = "Mode"
    const val BioimpedanceTitle = "bioelectrical impedance analysis"
    const val BioimpedanceDescription =
        "This scale utilizes %s (BIA) to measure all body metrics beyond weight. Selecting Weight Only disables the scale’s BIA function for all users."
    const val AllBodyMetrics = "ALL BODY METRICS"
    const val WeightOnly = "WEIGHT ONLY"

    fun HeartRate(isHeartRateOn: Boolean) = "Heart Rate: ${if (isHeartRateOn) "ON" else "OFF"}"

    const val Note = "NOTE: "
    const val HeartRateDescription =
        "This metric takes additional time to collect. When off, the scale will only collect weight and body composition."
    const val NoteMedical =
        "If you have certain medical conditions — like implanted medical devices or you are pregnant — you should not use All Body Metrics Mode without first consulting your doctor."
    const val NoteOtherUsers = "Other users can temporarily enable All Body Metrics for one session via Weight Gurus."
    const val WeightOnlyIndicator = "indicates Weight Only Mode is on"
    const val BodyMetricsOff = "BODY METRICS OFF"
    const val Save = "SAVE"

    object BiaModalStrings {
        const val Title = "What’s BIA?"
        const val Messsage =
            "Bioelectrical impedance analysis (BIA) is a commonly used method for measuring key metrics beyond weight—such as body fat, muscle mass, and body water. " +
                "\n\n To accomplish this, the scale sends a small, non-invasive electric current through your body and then compares the current to your height and weight. " +
                "\n\n If you have a cardiac pacemaker, implantable cardioverter-defibrillator (ICD), other implanted electronic devices, are pregnant, or you just aren’t sure whether BIA is right for you, consult with your doctor before using All Body Metrics Mode."
    }
}
