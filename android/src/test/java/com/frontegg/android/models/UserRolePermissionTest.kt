package com.frontegg.android.models

import com.google.gson.Gson
import org.junit.Before
import org.junit.Test

class UserRolePermissionTest {
    private val userRolePermissionJson =
        "{\"fePermission\":true,\"id\":\"0e8c0103-feb1-4ae0-8230-00de5fd0f857\",\"categoryId\":\"0c587ef6-eb9e-4a10-b888-66ec4bcb1548\",\"updatedAt\":\"2024-03-21T07:27:46.000Z\",\"description\":\"View all applications in the account\",\"createdAt\":\"2024-03-21T07:27:46.000Z\",\"key\":\"fe.account-settings.read.app\",\"name\":\"Read application\"}"
    private lateinit var userRolePermission: UserRolePermission

    @Before
    fun setUp() {
        userRolePermission = UserRolePermission()
        userRolePermission.fePermission = true
        userRolePermission.id = "0e8c0103-feb1-4ae0-8230-00de5fd0f857"
        userRolePermission.categoryId = "0c587ef6-eb9e-4a10-b888-66ec4bcb1548"
        userRolePermission.updatedAt = "2024-03-21T07:27:46.000Z"
        userRolePermission.description = "View all applications in the account"
        userRolePermission.createdAt = "2024-03-21T07:27:46.000Z"
        userRolePermission.key = "fe.account-settings.read.app"
        userRolePermission.name = "Read application"
    }

    @Test
    fun `should return valid model`() {
        val userRolePermissionModel = Gson().fromJson(userRolePermissionJson, UserRolePermission::class.java)

        assert(userRolePermissionModel == userRolePermission)
    }
}