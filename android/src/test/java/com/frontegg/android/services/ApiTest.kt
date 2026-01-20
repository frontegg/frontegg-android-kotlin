package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.exceptions.FailedToAuthenticateException
import com.frontegg.android.exceptions.FailedToRegisterWebAuthnDevice
import org.junit.jupiter.api.assertThrows
import com.frontegg.android.fixtures.authResponseJson
import com.frontegg.android.fixtures.getAuthResponse
import com.frontegg.android.fixtures.getUser
import com.frontegg.android.models.WebAuthnAssertionRequest
import com.frontegg.android.models.WebAuthnRegistrationRequest
import com.frontegg.android.utils.CredentialKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test


class ApiTest {
    private lateinit var api: Api
    private lateinit var mockkCredentialManager: CredentialManager
    private lateinit var mockWebServer: MockWebServer
    private val mockStorage = mockk<FronteggInnerStorage>()


    private val userJson =
        "{\"tenantId\":\"d230e118-8e56-4837-b70b-92943e567911\",\"verified\":true,\"activatedForTenant\":true,\"tenantIds\":[\"d230e118-8e56-4837-b70b-92943e567911\"],\"roles\":[{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"12c36cb2-67a7-4468-ad8f-c1a0e8128234\",\"isDefault\":true,\"updatedAt\":\"2024-05-13T11:59:55.000Z\",\"createdAt\":\"2024-05-13T11:59:55.000Z\",\"permissions\":[\"0e8c0103-feb1-4ae0-8230-00de5fd0f8566\",\"502b112e-50fd-4e8d-875e-3abda628d955\",\"da015508-7cb1-4dcd-9436-d0518a2ecd44\"],\"name\":\"Admin\",\"key\":\"Admin\"}],\"name\":\"Test User\",\"mfaEnrolled\":false,\"profilePictureUrl\":\"https://lh3.googleusercontent.com/a/ACg8ocKc8DKSMBDaSp83L-7jJXvfHT0YdZ9w4_KnqLpvFhETmQsH_A=s96-c\",\"permissions\":[{\"fePermission\":true,\"id\":\"0e8c0103-feb1-4ae0-8230-00de5fd0f822\",\"categoryId\":\"0c587ef6-eb9e-4a10-b888-66ec4bcb1548\",\"updatedAt\":\"2024-03-21T07:27:46.000Z\",\"createdAt\":\"2024-03-21T07:27:46.000Z\",\"description\":\"View all applications in the account\",\"name\":\"Read application\",\"key\":\"fe.account-settings.read.app\"},{\"fePermission\":true,\"id\":\"502b112e-50fd-4e8d-875e-3abda628d921\",\"categoryId\":\"5c326535-c73b-4926-937e-170d6ad5c9bz\",\"key\":\"fe.connectivity.*\",\"description\":\"all connectivity permissions\",\"createdAt\":\"2021-02-11T10:58:31.000Z\",\"updatedAt\":\"2021-02-11T10:58:31.000Z\",\"name\":\" Connectivity general\"},{\"fePermission\":true,\"id\":\"502b112e-50fd-4e8d-822e-3abda628d921\",\"categoryId\":\"684202ce-2345-48f0-8d67-4c05fe6a4d9a\",\"key\":\"fe.secure.*\",\"description\":\"all secure access permissions\",\"createdAt\":\"2020-12-08T08:59:25.000Z\",\"updatedAt\":\"2020-12-08T08:59:25.000Z\",\"name\":\"Secure general\"}],\"email\":\"test@mail.com\",\"id\":\"d89330b3-f581-493c-bcd7-0c4b1dff1111\",\"superUser\":false}"
    private val tenantsJson =
        "{\"tenants\":[{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"}],\"activeTenant\":{\"vendorId\":\"392b348b-a37c-471f-8f1b-2c35d23aa7e6\",\"id\":\"9ca34b3c-8ab6-40b9-a582-bd8badb571cb\",\"creatorName\":\"Test User\",\"creatorEmail\":\"test@mail.com\",\"tenantId\":\"d130e118-8e56-4837-b70b-92943e567976\",\"updatedAt\":\"2024-05-13T13:03:31.533Z\",\"createdAt\":\"2024-05-13T13:03:31.533Z\",\"metadata\":\"{}\",\"isReseller\":false,\"name\":\"Test User account\"}}"

    @Before
    fun setUp() {

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.clientId }.returns("TestClientId")
        every { mockStorage.applicationId }.returns("TestApplicationId")
        every { mockStorage.regions }.returns(listOf())

        mockkCredentialManager = mockkClass(CredentialManager::class)

        mockWebServer = MockWebServer()
        mockWebServer.start()
        val url = mockWebServer.url("")
        every { mockStorage.baseUrl }.returns(url.toString())

        api = Api(
            mockkCredentialManager
        )
        every { mockkCredentialManager.get(CredentialKeys.ACCESS_TOKEN) }.returns("Test Access Token")

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun dispose() {
        mockWebServer.shutdown()
    }

    @Test
    fun `me should return valid model`() {
        mockWebServer.enqueue(MockResponse().setBody(userJson))
        mockWebServer.enqueue(MockResponse().setBody(tenantsJson))

        val user = api.me()

        assert(user == getUser())
    }

    @Test
    fun `me should return null if response not success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val user = api.me()

        assert(user == null)
    }

    @Test
    fun `refreshToken should return valid model`() {
        mockWebServer.enqueue(MockResponse().setBody(authResponseJson))

        val response = api.refreshToken("Test Token")

        assert(response == getAuthResponse())
    }

    @Test
    fun `refreshToken should throw exception if response not success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        assertThrows<FailedToAuthenticateException> {
            api.refreshToken("Test Token")
        }
    }

    @Test
    fun `exchangeToken should return valid model`() {
        mockWebServer.enqueue(MockResponse().setBody(authResponseJson))

        val response = api.exchangeToken("Test Code", "Test Redirect Url", "Test Code Verifier")

        assert(response == getAuthResponse())
    }

    @Test
    fun `exchangeToken should return null if response not success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val response = api.exchangeToken("Test Code", "Test Redirect Url", "Test Code Verifier")

        assert(response == null)
    }

    @Test
    fun `logout should log debug if success`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        api.logout("TestRefreshToken")

        verify { Log.d(any(), any()) }
    }

    @Test
    fun `webAuthnPrelogin should return valid model`() {
        val webAuthnAssertionRequestJson =
            "{\"cookie\": \"fe_webauthn_test Coolie\",\"jsonChallenge\": \"{}\"}"

        val getWebAuthnAssertionRequest = WebAuthnAssertionRequest(
            cookie = "fe_webauthn_test Coolie",
            jsonChallenge = "null"
        )

        mockWebServer.enqueue(
            MockResponse().setBody(webAuthnAssertionRequestJson)
                .setHeader("set-cookie", "fe_webauthn_test Coolie")
        )

        val response = api.webAuthnPrelogin()

        assert(response == getWebAuthnAssertionRequest)
    }

    @Test
    fun `webAuthnPrelogin should throw FailedToAuthenticateException if failure response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        try {
            api.webAuthnPrelogin()
            assert(false)
        } catch (e: FailedToAuthenticateException) {
            assert(true)
        }
    }

    @Test
    fun `webAuthnPostlogin should return valid model`() {
        mockWebServer.enqueue(
            MockResponse().setBody(authResponseJson)
                .addHeaderLenient(
                    "set-cookie",
                    "fe_device_test Coolie"
                )
                .addHeaderLenient(
                    "set-cookie",
                    "fe_refresh_test Coolie",
                )
        )

        mockWebServer.enqueue(MockResponse().setBody(authResponseJson))
        val response = api.webAuthnPostlogin("Test Session Coolie", "{}")

        assert(response == getAuthResponse())
    }

    @Test
    fun `webAuthnPostlogin should throw FailedToAuthenticateException if failure response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        try {
            api.webAuthnPrelogin()
            assert(false)
        } catch (e: FailedToAuthenticateException) {
            assert(true)
        }
    }


    @Test
    fun `getWebAuthnRegisterChallenge should return valid model`() {
        mockWebServer.enqueue(
            MockResponse().setBody("{\"options\":{}}")
                .addHeader(
                    "set-cookie",
                    "fe_webauthn_test Coolie"
                )
        )

        val response = api.getWebAuthnRegisterChallenge()

        val webAuthnRegistrationRequest = WebAuthnRegistrationRequest(
            "fe_webauthn_test Coolie",
            "{}"
        )
        assert(response == webAuthnRegistrationRequest)
    }

    @Test
    fun `getWebAuthnRegisterChallenge should throw FailedToAuthenticateException if failure response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        try {
            api.getWebAuthnRegisterChallenge()
            assert(false)
        } catch (ignore: FailedToAuthenticateException) {
            assert(true)
        }
    }


    @Test
    fun `verifyWebAuthnDevice should not throw Exception if success`() {
        mockWebServer.enqueue(MockResponse().setBody(""))
        try {
            api.verifyWebAuthnDevice("fe_webauthn_test Coolie", "{}")
            assert(true)
        } catch (ignore: Exception) {
            assert(false)
        }
    }

    @Test
    fun `verifyWebAuthnDevice should throw FailedToRegisterWebAuthnDevice if failure response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        try {
            api.verifyWebAuthnDevice("fe_webauthn_test Coolie", "{}")
            assert(false)
        } catch (ignore: FailedToRegisterWebAuthnDevice) {
            assert(true)
        }
    }
}