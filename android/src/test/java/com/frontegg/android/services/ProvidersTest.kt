package com.frontegg.android.services

import androidx.test.core.app.ApplicationProvider
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProvidersTest {

    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()
    }

    @Test
    fun `StorageProvider getInnerStorage returns non-null`() {
        val storage = StorageProvider.getInnerStorage()
        assertNotNull(storage)
    }

    @Test
    fun `ScopeProvider mainScope returns non-null`() {
        val scope = ScopeProvider.mainScope
        assertNotNull(scope)
    }

    @Test
    fun `ApiProvider getApi returns non-null when given CredentialManager`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val credentialManager = CredentialManager(context)
        val api = ApiProvider.getApi(credentialManager)
        assertNotNull(api)
    }

    @Test
    fun `MultiFactorAuthenticatorProvider getMultiFactorAuthenticator returns non-null`() {
        val mfa = MultiFactorAuthenticatorProvider.getMultiFactorAuthenticator()
        assertNotNull(mfa)
    }

    @Test
    fun `StepUpAuthenticatorProvider getStepUpAuthenticator returns non-null`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val credentialManager = CredentialManager(context)
        val stepUp = StepUpAuthenticatorProvider.getStepUpAuthenticator(credentialManager)
        assertNotNull(stepUp)
    }

    @Test
    fun `AuthorizeUrlGeneratorProvider getAuthorizeUrlGenerator returns non-null`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val generator = AuthorizeUrlGeneratorProvider.getAuthorizeUrlGenerator(context)
        assertNotNull(generator)
    }
}
