package com.frontegg.demo.utils

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.webkit.WebView
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

    fun clickByText(text: String): Boolean {
        val obj = waitForView(By.text(text))
        obj?.click()
        return obj != null
    }

    fun waitForView(
        bySelector: BySelector,
        index: Int = 0,
        timeout: Long? = null
    ): UiObject2? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < (timeout ?: timeoutMillis)) {
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
}