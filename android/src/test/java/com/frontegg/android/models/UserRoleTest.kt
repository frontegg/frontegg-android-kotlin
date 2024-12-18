package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class UserRoleTest {
    private val userRoleJson =
        "{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"12c36cb2-67a7-4468-ad8f-c1a0e8128234\",\"isDefault\":true,\"updatedAt\":\"2024-05-13T11:59:55.000Z\",\"createdAt\":\"2024-05-13T11:59:55.000Z\",\"permissions\":[\"0e8c0103-feb1-4ae0-8230-00de5fd0f8566\",\"502b112e-50fd-4e8d-875e-3abda628d955\",\"da015508-7cb1-4dcd-9436-d0518a2ecd44\"],\"name\":\"Admin\",\"key\":\"Admin\"}"
    private lateinit var userRole: UserRole

    @Before
    fun setUp() {
        userRole = UserRole()
        userRole.vendorId = "392b348b-a37c-471f-8f1b-2c35d23aa7e6"
        userRole.id = "12c36cb2-67a7-4468-ad8f-c1a0e8128234"
        userRole.isDefault = true
        userRole.updatedAt = "2024-05-13T11:59:55.000Z"
        userRole.createdAt = "2024-05-13T11:59:55.000Z"
        userRole.permissions = listOf(
            "0e8c0103-feb1-4ae0-8230-00de5fd0f8566",
            "502b112e-50fd-4e8d-875e-3abda628d955",
            "da015508-7cb1-4dcd-9436-d0518a2ecd44",
        )
        userRole.name = "Admin"
        userRole.key = "Admin"
    }

    @Test
    fun `should return valid model`() {
        val userRoleModel = Gson().fromJson(userRoleJson, UserRole::class.java)

        assert(userRoleModel == userRole)
    }
}