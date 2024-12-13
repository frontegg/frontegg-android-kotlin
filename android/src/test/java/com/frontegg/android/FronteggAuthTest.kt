package com.frontegg.android

import android.app.Activity
import android.util.Log
import android.webkit.CookieManager
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test


class FronteggAuthTest {

    private val mockActivity = mockk<Activity>()
    private lateinit var auth: FronteggAuth
    private val mockFronteggApp = mockkClass(FronteggApp::class)
    private val mockCredentialManager = mockk<CredentialManager>()

    @Before
    fun setUp() {
        FronteggApp.setTestInstance(mockFronteggApp)
        every { mockCredentialManager.get(any()) }.returns(null)
        mockCredentialManager
        auth = FronteggAuth(
            baseUrl = "",
            clientId = "",
            selectedRegion = null,
            regions = listOf(),
            credentialManager = mockCredentialManager,
            applicationId = null,
        )

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `login via EmbeddedAuthActivity if EmbeddedAuthActivity is enable`() {
        every { mockFronteggApp.isEmbeddedMode }.returns(true)

        mockkObject(EmbeddedAuthActivity.Companion)
        every { EmbeddedAuthActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { EmbeddedAuthActivity.authenticate(any(), any(), any()) }
    }

    @Test
    fun `login via EmbeddedAuthActivity if EmbeddedAuthActivity is disable`() {
        every { mockFronteggApp.isEmbeddedMode }.returns(false)

        mockkObject(AuthenticationActivity.Companion)
        every { AuthenticationActivity.authenticate(any(), any(), any()) }.returns(Unit)
        auth.login(mockActivity, null, null)

        verify { AuthenticationActivity.authenticate(any(), any(), any()) }
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_selectedRegion`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.selectedRegion == regionConfig)
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_baseUrl`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.baseUrl == "https://TestBaseUrl")
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_clientId`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.clientId == "TestClientId")
    }

    @Test
    fun `reinitWithRegion should set up FronteggAuth_applicationId`() {
        val regionConfig = RegionConfig(
            key = "key1",
            baseUrl = "TestBaseUrl",
            clientId = "TestClientId",
            applicationId = "TestApplicationId"
        )
        auth.reinitWithRegion(regionConfig)

        assert(auth.applicationId == "TestApplicationId")
    }

    @Test
    fun `sendRefreshToken should call Api_refreshToken and Api_me`() {
        auth.refreshToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)

        val authResponse = AuthResponse()
        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "TestTokenToken"
        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)


        mockkObject(JWTHelper.Companion)
        val jwt = JWT()
        every { JWTHelper.decode(any()) }.returns(jwt)

        val result = auth.sendRefreshToken()
        verify { apiMock.refreshToken(any()) }
        verify { apiMock.me() }
        assert(result)
    }

    @Test
    fun `sendRefreshToken should call CredentialManager_save`() {
        auth.refreshToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)

        val authResponse = AuthResponse()
        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "TestTokenToken"
        val user = User()

        every { apiMock.refreshToken(any()) }.returns(authResponse)
        every { apiMock.me() }.returns(user)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)


        mockkObject(JWTHelper.Companion)
        val jwt = JWT()
        every { JWTHelper.decode(any()) }.returns(jwt)

        val result = auth.sendRefreshToken()
        verify { mockCredentialManager.save(any(), any()) }
        assert(result)
    }

    @Test
    fun `sendRefreshToken should not call CredentialManager_save if Api_refreshToken returns null`() {
        auth.refreshToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)
        every { apiMock.refreshToken(any()) }.returns(null)
        auth.setApi(apiMock)

        every { mockCredentialManager.save(any(), any()) }.returns(true)

        val result = auth.sendRefreshToken()
        verify(exactly = 0) { mockCredentialManager.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `refreshTokenIfNeeded should return false if Exception`() {
        auth.refreshToken.value = "TestRefreshToken"

        val apiMock = mockkClass(Api::class)
        every { apiMock.refreshToken(any()) }.throws(Exception())
        auth.setApi(apiMock)

        val result = auth.refreshTokenIfNeeded()
        verify(exactly = 0) { mockCredentialManager.save(any(), any()) }
        assert(!result)
    }

    @Test
    fun `logout should refresh FronteggAuth`() {
        auth.refreshToken.value = "TestRefreshToken"
        val mockCookieManager = mockkClass(CookieManager::class)
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() }.returns(mockCookieManager)
        every { mockCookieManager.getCookie(any()) }.returns(null)
        every { mockCredentialManager.clear() }.returns(Unit)

        auth.logout()
        Thread.sleep(1_000)
        assert(auth.accessToken.value == null)
        assert(auth.refreshToken.value == null)
        assert(auth.user.value == null)
        assert(!auth.isAuthenticated.value)
    }
}