package com.frontegg.android.models

import com.frontegg.android.fixtures.getUser
import com.frontegg.android.fixtures.userJson
import com.frontegg.android.utils.ObservableValue
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

    @Test
    fun `users with fields missing from the response can be compared`() {
        val partial = """{"id":"user-1","email":"user@example.com","roles":[{"id":"role-1"}]}"""
        val first = Gson().fromJson(partial, User::class.java)
        val second = Gson().fromJson(partial, User::class.java)
        val state = ObservableValue<User?>(first)

        state.value = second

        assert(first == second)
        assert(first.hashCode() == second.hashCode())
        assert(state.stateFlow.value === first)
    }
}
