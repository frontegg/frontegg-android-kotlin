package com.frontegg.android.services

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.frontegg.android.exceptions.CanceledByUserException
import com.frontegg.android.exceptions.GoogleSignInNotConfiguredException
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.SocialLoginConfig
import com.frontegg.android.models.SocialLoginOption
import com.frontegg.android.models.SocialLoginPostLoginRequest
import com.frontegg.android.models.Tenant
import com.frontegg.android.models.User
import com.frontegg.android.utils.CredentialKeys
import com.frontegg.android.utils.FronteggCallback
import com.frontegg.android.utils.JWT
import com.frontegg.android.utils.JWTHelper
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.utils.RequestQueue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test

class FronteggAuthServiceGoogleSignInTest {
    private val storageMock = mockk<FronteggInnerStorage>()
    private val contextMock = mockk<Context>()
    private val activityMock = mockk<Activity>()
    private val credentialManagerMock = mockk<CredentialManager>()
    private val apiMock = mockk<Api>()
    private val googleIdTokenSource = mockk<GoogleIdTokenSource>()
    private val authResponse = AuthResponse()
    private lateinit var auth: FronteggAuthService

    private val activeGoogleConfig = SocialLoginConfig(
        google = SocialLoginOption(active = true, clientId = "web-client-id")
    )

    @Before
    fun setUp() {
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() } returns storageMock
        every { storageMock.clientId } returns "TestClientId"
        every { storageMock.applicationId } returns null
        every { storageMock.baseUrl } returns "https://base.url.com"
        every { storageMock.regions } returns listOf()
        every { storageMock.enableSessionPerTenant } returns false
        every { storageMock.entitlementsEnabled } returns false

        every { credentialManagerMock.get(any()) } returns null
        every { credentialManagerMock.get(any(), any()) } returns null
        every { credentialManagerMock.context } returns contextMock
        every { credentialManagerMock.setEnableSessionPerTenant(any()) } returns Unit
        every { credentialManagerMock.save(any(), any(), any()) } returns true

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { contextMock.getSharedPreferences(any(), any()) } returns sharedPreferences

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) } returns apiMock
        every { apiMock.getServerUrl() } returns "https://base.url.com"

        mockkObject(NetworkGate)
        every { NetworkGate.setFronteggBaseUrl(any()) } returns Unit

        mockkConstructor(RequestQueue::class)
        mockkConstructor(MutableLiveData::class)
        every { anyConstructed<MutableLiveData<Boolean>>().postValue(any()) } returns Unit

        mockkObject(ScopeProvider)
        every { ScopeProvider.mainScope } returns CoroutineScope(Dispatchers.Unconfined)

        mockkObject(GoogleIdTokenSourceProvider)
        every { GoogleIdTokenSourceProvider.getGoogleIdTokenSource(any()) } returns googleIdTokenSource

        mockkObject(JWTHelper)
        every { JWTHelper.decode(any()) } returns JWT()

        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp } returns FronteggCallback()
        every { appLifecycle.stopApp } returns FronteggCallback()
        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>()
        every { refreshTokenTimer.refreshTokenIfNeeded } returns FronteggCallback()
        every { refreshTokenTimer.cancelLastTimer() } returns Unit

        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
        )

        authResponse.id_token = "TestIdToken"
        authResponse.refresh_token = "TestRefreshToken"
        authResponse.access_token = "TestAccessToken"
        authResponse.token_type = "Bearer"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun signedInUser(): User {
        val tenant = Tenant().apply {
            id = "tenant-1"
            tenantId = "tenant-1"
        }
        return User().apply {
            id = "user-1"
            email = "user@example.com"
            name = "User"
            tenants = listOf(tenant)
            activeTenant = tenant
        }
    }

    private fun loginWithGoogleAndCapture(): Pair<Boolean, Exception?> {
        var called = false
        var error: Exception? = null
        auth.loginWithGoogle(activityMock) {
            called = true
            error = it
        }
        return called to error
    }

    @Test
    fun `loginWithGoogle exchanges the Google ID token and sets credentials`() {
        every { apiMock.getSocialLoginConfig() } returns activeGoogleConfig
        coEvery { googleIdTokenSource.getIdToken("web-client-id") } returns "google-id-token"
        val postLoginRequest = slot<SocialLoginPostLoginRequest>()
        every { apiMock.socialLoginPostLogin("google", capture(postLoginRequest)) } returns authResponse
        every { apiMock.me() } returns signedInUser()

        val (called, error) = loginWithGoogleAndCapture()

        assert(called)
        assert(error == null) { "Expected success, got $error" }
        assert(postLoginRequest.captured.idToken == "google-id-token")
        assert(postLoginRequest.captured.redirectUri == "https://base.url.com/oauth/account/social/success")
        verify { credentialManagerMock.save(CredentialKeys.REFRESH_TOKEN, "TestRefreshToken", null) }
        verify { credentialManagerMock.save(CredentialKeys.ACCESS_TOKEN, "TestAccessToken", null) }
        assert(auth.isAuthenticated.value)
    }

    @Test
    fun `loginWithGoogle reports a cancel without exchanging a token`() {
        every { apiMock.getSocialLoginConfig() } returns activeGoogleConfig
        coEvery { googleIdTokenSource.getIdToken(any()) } throws CanceledByUserException()

        val (called, error) = loginWithGoogleAndCapture()

        assert(called)
        assert(error is CanceledByUserException)
        verify(exactly = 0) { apiMock.socialLoginPostLogin(any(), any()) }
        assert(!auth.isLoading.value)
    }

    @Test
    fun `loginWithGoogle fails when Google social login is inactive`() {
        every { apiMock.getSocialLoginConfig() } returns SocialLoginConfig(
            google = SocialLoginOption(active = false, clientId = "web-client-id")
        )

        val (called, error) = loginWithGoogleAndCapture()

        assert(called)
        assert(error is GoogleSignInNotConfiguredException)
        coVerify(exactly = 0) { googleIdTokenSource.getIdToken(any()) }
    }

    @Test
    fun `loginWithGoogle fails when Google social login has no client ID`() {
        every { apiMock.getSocialLoginConfig() } returns SocialLoginConfig(
            google = SocialLoginOption(active = true, clientId = " ")
        )

        val (_, error) = loginWithGoogleAndCapture()

        assert(error is GoogleSignInNotConfiguredException)
    }

    @Test
    fun `loginWithGoogle fails when Google social login is missing`() {
        every { apiMock.getSocialLoginConfig() } returns SocialLoginConfig()

        val (_, error) = loginWithGoogleAndCapture()

        assert(error is GoogleSignInNotConfiguredException)
    }

    @Test
    fun `loginWithGoogle reports a failed exchange and clears loading`() {
        every { apiMock.getSocialLoginConfig() } returns activeGoogleConfig
        coEvery { googleIdTokenSource.getIdToken(any()) } returns "google-id-token"
        every { apiMock.socialLoginPostLogin(any(), any()) } throws RuntimeException("rejected")

        val (called, error) = loginWithGoogleAndCapture()

        assert(called)
        assert(error?.message == "rejected")
        assert(!auth.isLoading.value)
        assert(!auth.isAuthenticated.value)
    }
}
