package com.frontegg.android.models

import com.frontegg.android.fixtures.getUser
import com.frontegg.android.fixtures.userJson
import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class UserTest {
    private lateinit var user: User

    @Before
    fun setUp() {
        user = getUser()
    }

    @Test
    fun `should return valid model`() {
        val userModel = Gson().fromJson(userJson, User::class.java)

        assert(userModel == user)
    }
}