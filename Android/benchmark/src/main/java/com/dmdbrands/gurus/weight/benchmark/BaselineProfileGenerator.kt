package com.dmdbrands.gurus.weight.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.dmdbrands.gurus.weight",
    ) {
        pressHome()
        startActivityAndWait() // Cold startup

        // Login flow
        device.findObject(By.res("email_field"))?.text = "test@test.com"
        device.findObject(By.res("password_field"))?.text = "password"
        device.findObject(By.res("login_button"))?.click()
        waitForIdle()

        // Dashboard load
        waitForIdle()

        // Entry creation
        device.findObject(By.res("add_entry_button"))?.click()
        waitForIdle()
    }
}
