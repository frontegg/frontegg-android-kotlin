package com.frontegg.demo.utils

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.frontegg.demo.NavigationActivity

class UiTestInstrumentation {
    private val timeoutMillis: Long = 10_000

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val configurator: Configurator = Configurator.getInstance()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    private val targetContext: Context = instrumentation.targetContext
    private val uiAutomation: UiAutomation = instrumentation.uiAutomation

    init {
        // Config
        configurator.uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
    }

    fun openApp(
        activityName: String = NavigationActivity::class.java.getCanonicalName()!!.toString(),
        applicationPackage: String = "com.frontegg.demo"
    ) {
        // Launch application by activityName
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setClassName(targetContext, activityName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)

        // Wait
        uiDevice.wait(Until.hasObject(By.pkg(applicationPackage).depth(0)), 3000)
    }

    fun scrollToBottom(maxAttempts: Int = 3, swipeSteps: Int = 55): Boolean {
        try {
            val displayHeight = uiDevice.displayHeight
            val displayWidth = uiDevice.displayWidth

            if (displayHeight == 0 || displayWidth == 0) return false

            val startX = displayWidth / 2
            val startY = (displayHeight * 0.8).toInt()
            val endY = (displayHeight * 0.2).toInt()

            repeat(maxAttempts) {
                uiDevice.swipe(startX, startY, startX, endY, swipeSteps)
                Thread.sleep(500)
            }

            return true
        } catch (e: Exception) {
            println("Failed to scroll: ${e.message}")
            return false
        }
    }


    fun clickByText(text: String, timeout: Long = timeoutMillis): Boolean {
        val obj = waitForView(By.text(text), timeout = timeout)
        obj?.click()
        return obj != null
    }

    fun clickByEmptySpace() {
        uiDevice.click(10, uiDevice.displayHeight / 4)
    }

    fun pressBack() {
        uiDevice.pressBack()
    }

    fun clickByDesc(desc: String, timeout: Long = timeoutMillis): Boolean {

        val obj = waitForView(By.desc(desc), timeout = timeout)
        obj?.click()
        return obj != null
    }

    fun clickByResourceId(resourceId: String): Boolean {
        val obj = waitForView(By.res(resourceId))
        obj?.click()
        return obj != null
    }

    fun getAllObjects(): String {
        val enabledObjects = uiDevice.findObjects(By.enabled(true))
        val disabledObjects = uiDevice.findObjects(By.enabled(false))
        val sb = StringBuilder()
        sb.append("Enabled:")
        for (obj in enabledObjects) {
            sb.append(("${obj.resourceName}: ${obj.text}, "))
        }

        sb.append("Disabled:")
        for (obj in disabledObjects) {
            sb.append(("${obj.resourceName}: ${obj.text}, "))
        }

        return sb.toString()
    }

    fun waitForView(
        bySelector: BySelector,
        index: Int = 0,
        timeout: Long = timeoutMillis
    ): UiObject2? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {

            val objects = uiDevice.findObjects(bySelector)
            if (objects.size > index && objects[index] != null) {
                return objects[index]
            }

            delay(ms = 500)
        }

        return null
    }

    fun inputTextByIndex(
        index: Int,
        text: String,
    ): Boolean {
        val obj = waitForView(By.clazz(EditText::class.java), index)

        if (obj != null) {
            obj.text = text
            return true
        }

        return false
    }

    fun inputTextByResourceId(
        resourceId: String,
        text: String
    ): Boolean {
        val obj = waitForView(By.res(resourceId))

        if (obj != null) {
            obj.text = text
            return true
        }

        return false
    }
}