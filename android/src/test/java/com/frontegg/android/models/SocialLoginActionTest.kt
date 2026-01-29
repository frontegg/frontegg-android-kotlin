package com.frontegg.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialLoginActionTest {

    @Test
    fun `fromString returns correct action for each value`() {
        assertEquals(SocialLoginAction.LOGIN, SocialLoginAction.fromString("login"))
        assertEquals(SocialLoginAction.SIGNUP, SocialLoginAction.fromString("signUp"))
    }

    @Test
    fun `fromString returns null for unknown value`() {
        assertNull(SocialLoginAction.fromString("unknown"))
        assertNull(SocialLoginAction.fromString(""))
        assertNull(SocialLoginAction.fromString("LOGIN"))
    }

    @Test
    fun `value property matches expected`() {
        assertEquals("login", SocialLoginAction.LOGIN.value)
        assertEquals("signUp", SocialLoginAction.SIGNUP.value)
    }
}
