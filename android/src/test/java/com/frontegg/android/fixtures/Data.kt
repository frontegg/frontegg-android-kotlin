package com.frontegg.android.fixtures

import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.models.UserRole
import com.frontegg.android.models.UserRolePermission
import com.frontegg.android.models.WebAuthnAssertionRequest

const val authResponseJson =
    "{\"id_token\":\"Test Id Token\",\"refresh_token\":\"Test Refresh Token\",\"access_token\":\"Test Access Token\",\"token_type\":\"Test Token Type\"}"

fun getAuthResponse(): AuthResponse {
    val authResponse = AuthResponse()
    authResponse.id_token = "Test Id Token"
    authResponse.refresh_token = "Test Refresh Token"
    authResponse.access_token = "Test Access Token"
    authResponse.token_type = "Test Token Type"
    return authResponse
}

const val tenantJson =
    "{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"}"

fun getTenant(): Tenant {
    val tenant = Tenant()
    tenant.vendorId = "392b348b-a37c-471f-8f1b-2c35d23aa7e6"
    tenant.id = "9ca34b3c-8ab6-40b9-a582-bd8badb571cb"
    tenant.creatorName = "Test User"
    tenant.creatorEmail = "test@mail.com"
    tenant.tenantId = "d130e118-8e56-4837-b70b-92943e567976"
    tenant.updatedAt = "2024-05-13T13:03:31.533Z"
    tenant.createdAt = "2024-05-13T13:03:31.533Z"
    tenant.metadata = "{}"
    tenant.isReseller = false
    tenant.name = "Test User account"

    return tenant
}

const val userJson =
    "{\"tenantId\":\"d230e118-8e56-4837-b70b-92943e567911\",\"verified\":true,\"activatedForTenant\":true,\"tenantIds\":[\"d230e118-8e56-4837-b70b-92943e567911\"],\"roles\":[{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"12c36cb2-67a7-4468-ad8f-c1a0e8128234\",\"isDefault\":true,\"updatedAt\":\"2024-05-13T11:59:55.000Z\",\"createdAt\":\"2024-05-13T11:59:55.000Z\",\"permissions\":[\"0e8c0103-feb1-4ae0-8230-00de5fd0f8566\",\"502b112e-50fd-4e8d-875e-3abda628d955\",\"da015508-7cb1-4dcd-9436-d0518a2ecd44\"],\"name\":\"Admin\",\"key\":\"Admin\"}],\"tenants\":[{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"}],\"name\":\"Test User\",\"mfaEnrolled\":false,\"profilePictureUrl\":\"https://lh3.googleusercontent.com/a/ACg8ocKc8DKSMBDaSp83L-7jJXvfHT0YdZ9w4_KnqLpvFhETmQsH_A=s96-c\",\"activeTenant\":{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"},\"permissions\":[{\"fePermission\":true,\"id\":\"0e8c0103-feb1-4ae0-8230-00de5fd0f822\",\"categoryId\":\"0c587ef6-eb9e-4a10-b888-66ec4bcb1548\",\"updatedAt\":\"2024-03-21T07:27:46.000Z\",\"createdAt\":\"2024-03-21T07:27:46.000Z\",\"description\":\"View all applications in the account\",\"name\":\"Read application\",\"key\":\"fe.account-settings.read.app\"},{\"fePermission\":true,\"id\":\"502b112e-50fd-4e8d-875e-3abda628d921\",\"categoryId\":\"5c326535-c73b-4926-937e-170d6ad5c9bz\",\"key\":\"fe.connectivity.*\",\"description\":\"all connectivity permissions\",\"createdAt\":\"2021-02-11T10:58:31.000Z\",\"updatedAt\":\"2021-02-11T10:58:31.000Z\",\"name\":\" Connectivity general\"},{\"fePermission\":true,\"id\":\"502b112e-50fd-4e8d-822e-3abda628d921\",\"categoryId\":\"684202ce-2345-48f0-8d67-4c05fe6a4d9a\",\"key\":\"fe.secure.*\",\"description\":\"all secure access permissions\",\"createdAt\":\"2020-12-08T08:59:25.000Z\",\"updatedAt\":\"2020-12-08T08:59:25.000Z\",\"name\":\"Secure general\"}],\"email\":\"test@mail.com\",\"id\":\"d89330b3-f581-493c-bcd7-0c4b1dff1111\",\"superUser\":false}"


fun getUser(): User {
    val userRole = UserRole()
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

    val activeTenant = Tenant()
    activeTenant.vendorId = "392b348b-a37c-471f-8f1b-2c35d23aa7e6"
    activeTenant.id = "9ca34b3c-8ab6-40b9-a582-bd8badb571cb"
    activeTenant.creatorName = "Test User"
    activeTenant.creatorEmail = "test@mail.com"
    activeTenant.tenantId = "d130e118-8e56-4837-b70b-92943e567976"
    activeTenant.updatedAt = "2024-05-13T13:03:31.533Z"
    activeTenant.createdAt = "2024-05-13T13:03:31.533Z"
    activeTenant.metadata = "{}"
    activeTenant.isReseller = false
    activeTenant.name = "Test User account"

    val permission1 = UserRolePermission()
    permission1.fePermission = true
    permission1.id = "0e8c0103-feb1-4ae0-8230-00de5fd0f822"
    permission1.categoryId = "0c587ef6-eb9e-4a10-b888-66ec4bcb1548"
    permission1.updatedAt = "2024-03-21T07:27:46.000Z"
    permission1.createdAt = "2024-03-21T07:27:46.000Z"
    permission1.description = "View all applications in the account"
    permission1.name = "Read application"
    permission1.key = "fe.account-settings.read.app"

    val permission2 = UserRolePermission()
    permission2.fePermission = true
    permission2.id = "502b112e-50fd-4e8d-875e-3abda628d921"
    permission2.categoryId = "5c326535-c73b-4926-937e-170d6ad5c9bz"
    permission2.key = "fe.connectivity.*"
    permission2.description = "all connectivity permissions"
    permission2.createdAt = "2021-02-11T10:58:31.000Z"
    permission2.updatedAt = "2021-02-11T10:58:31.000Z"
    permission2.name = " Connectivity general"

    val permission3 = UserRolePermission()
    permission3.fePermission = true
    permission3.id = "502b112e-50fd-4e8d-822e-3abda628d921"
    permission3.categoryId = "684202ce-2345-48f0-8d67-4c05fe6a4d9a"
    permission3.key = "fe.secure.*"
    permission3.description = "all secure access permissions"
    permission3.createdAt = "2020-12-08T08:59:25.000Z"
    permission3.updatedAt = "2020-12-08T08:59:25.000Z"
    permission3.name = "Secure general"

    val user = User()
    user.tenantId = "d230e118-8e56-4837-b70b-92943e567911"
    user.verified = true
    user.activatedForTenant = true
    user.tenantIds = listOf("d230e118-8e56-4837-b70b-92943e567911")
    user.roles = listOf(userRole)
    user.tenants = listOf(activeTenant)
    user.name = "Test User"
    user.mfaEnrolled = false
    user.profilePictureUrl =
        "https://lh3.googleusercontent.com/a/ACg8ocKc8DKSMBDaSp83L-7jJXvfHT0YdZ9w4_KnqLpvFhETmQsH_A=s96-c"
    user.activeTenant = activeTenant
    user.permissions = listOf(permission1, permission2, permission3)
    user.email = "test@mail.com"
    user.id = "d89330b3-f581-493c-bcd7-0c4b1dff1111"
    user.superUser = false

    return user
}

const val webAuthnAssertionRequestJson = "{\"cookie\": \"Test Cookie\",\"jsonChallenge\": \"{}\"}"

fun getWebAuthnAssertionRequest(): WebAuthnAssertionRequest {
    return WebAuthnAssertionRequest(
        cookie = "Test Cookie",
        jsonChallenge = "{}"
    )
}