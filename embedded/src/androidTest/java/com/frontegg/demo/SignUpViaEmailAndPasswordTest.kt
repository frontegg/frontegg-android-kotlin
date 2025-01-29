package com.frontegg.demo

import androidx.test.uiautomator.By
import com.frontegg.demo.utils.Env
import com.frontegg.demo.utils.UiTestInstrumentation
import com.frontegg.demo.utils.delay
import com.frontegg.demo.utils.logout
import com.frontegg.demo.utils.tapLoginButton
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SignUpViaEmailAndPasswordTest {
    private lateinit var instrumentation: UiTestInstrumentation
    private lateinit var email: String

    @Before
    fun setUp() {
        instrumentation = UiTestInstrumentation()
        instrumentation.openApp()

        email = Env.signUpTemplate.replace("{uuid}", UUID.randomUUID().toString())
    }

    @Test
    fun success_Sign_Up_via_Email_and_Password() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, email)
        instrumentation.inputTextByIndex(1, Env.signUpName)
        instrumentation.inputTextByIndex(2, Env.loginPassword)
        instrumentation.inputTextByIndex(3, Env.signUpOrganization)

        instrumentation.clickByText("Sign up")

        instrumentation.logout()
    }

    @Test
    fun failure_Sign_Up_with_existing_User() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, Env.loginEmail)
        instrumentation.inputTextByIndex(1, Env.signUpName)
        instrumentation.inputTextByIndex(2, Env.loginPassword)
        instrumentation.inputTextByIndex(3, Env.signUpOrganization)

        instrumentation.clickByText("Sign up")

        instrumentation.waitForView(By.text("User already exists"))
            ?: throw Exception("Should 'User already exists' warning be visible")
    }

    @Test
    fun failure_Sign_Up_with_invalid_Email_field() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, "s")
        instrumentation.inputTextByIndex(1, Env.signUpName)
        instrumentation.inputTextByIndex(2, Env.loginPassword)
        instrumentation.inputTextByIndex(3, Env.signUpOrganization)

        instrumentation.clickByText("Sign up")

        instrumentation.waitForView(By.text("Must be a valid Email"))
            ?: throw Exception("Should 'Must be a valid Email' warning be visible")
    }

    @Test
    fun failure_Sign_Up_with_empty_Name_field() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, email)
        instrumentation.inputTextByIndex(1, "")
        instrumentation.inputTextByIndex(2, Env.loginPassword)
        instrumentation.inputTextByIndex(3, Env.signUpOrganization)

        instrumentation.clickByText("Sign up")

        instrumentation.waitForView(By.text("Name is required"))
            ?: throw Exception("Should 'Name is required' warning be visible")
    }

    @Test
    fun failure_Sign_Up_with_empty_Password_field() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, email)
        instrumentation.inputTextByIndex(1, Env.signUpName)
        instrumentation.inputTextByIndex(2, "")
        instrumentation.inputTextByIndex(3, Env.signUpOrganization)

        instrumentation.clickByText("Sign up")

        instrumentation.waitForView(By.text("Password is required"))
            ?: throw Exception("Should 'Password is required' warning be visible")
    }

    @Test
    fun failure_Sign_Up_with_empty_CompanyName_field() {
        instrumentation.tapLoginButton()

        instrumentation.clickByText("Sign up")
        instrumentation.waitForView(By.text("Account sign-up"))

        hideKeyboard()

        instrumentation.inputTextByIndex(0, email)
        instrumentation.inputTextByIndex(1, Env.signUpName)
        instrumentation.inputTextByIndex(2, Env.loginPassword)
        instrumentation.inputTextByIndex(3, "")

        instrumentation.clickByText("Sign up")

        instrumentation.waitForView(By.text("Company name is required"))
            ?: throw Exception("Should 'Company name is required' warning be visible")
    }

    private fun hideKeyboard() {
        instrumentation.clickByText("Account sign-up")
        delay()
    }
}