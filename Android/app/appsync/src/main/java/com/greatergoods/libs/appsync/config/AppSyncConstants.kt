package com.greatergoods.libs.appsync.config

/**
 * Constants used throughout the AppSync module.
 */
object AppSyncConstants {
    // Camera zoom settings
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 5.0f
    const val ZOOM_STEP = 0.5f
    const val DEFAULT_ZOOM = 1

    // Zoom animation settings
    const val ZOOM_ANIMATION_DURATION = 300L // 300ms animation
    const val ZOOM_ANIMATION_STEPS = 30 // 30 steps for smooth animation
    const val ZOOM_CHANGE_THRESHOLD = 0.01f // Minimum change to trigger animation

    // FS003 protocol constants
    const val HAMMING_BLOCK_SIZE = 7
    const val DATA_ARRAY_SIZE = 10
    const val WEIGHT_SHIFT_1 = 4
    const val WEIGHT_SHIFT_2 = 8
    const val WEIGHT_RIGHT_SHIFT = 1
    const val WEIGHT_DIVISOR = 10f

    const val FAT_SHIFT_1 = 4
    const val FAT_SHIFT_2 = 8
    const val FAT_SHIFT_3 = 12
    const val FAT_RIGHT_SHIFT = 3
    const val FAT_MASK = 0x3ff
    const val FAT_DIVISOR = 10f
    const val FAT_INVALID_VALUE = 0x3ff

    const val MUSCLE_SHIFT_1 = 4
    const val MUSCLE_SHIFT_2 = 8
    const val MUSCLE_RIGHT_SHIFT = 2
    const val MUSCLE_MASK = 0x1ff
    const val MUSCLE_DIVISOR = 10f
    const val MUSCLE_BASE_VALUE = 14.9f
    const val MUSCLE_INVALID_VALUE = 0x1ff

    const val WATER_RIGHT_SHIFT_1 = 1
    const val WATER_SHIFT_1 = 1
    const val WATER_SHIFT_2 = 5
    const val WATER_MASK = 0x7f
    const val WATER_BASE_VALUE = 18f
    const val WATER_DIVISOR = 2f
    const val WATER_INVALID_VALUE = 0x7f

    const val MODE_MASK = 0x1

    // Error thresholds
    const val MAX_ERRORS_FOR_INVALID_SCAN = 77

    // Hamming code constants
    object Hamming {
        val H_MATRIX = intArrayOf(0x55, 0x66, 0x78)
        val EXTRACTION_MATRIX = intArrayOf(0x04, 0x10, 0x20, 0x40)
    }

    // Measurement modes
    object Modes {
        const val KILOGRAMS = "kg"
        const val POUNDS = "lb"
        val VALID_MODES = arrayOf(KILOGRAMS, POUNDS)
    }
}
