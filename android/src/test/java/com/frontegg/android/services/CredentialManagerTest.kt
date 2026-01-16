package com.frontegg.android.services

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import com.frontegg.android.utils.CredentialKeys
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class CredentialManagerTest {
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var credentialManagerWithMockSharedPreferences: CredentialManager
    private lateinit var credentialManager: CredentialManager

    @Before
    fun setUp() {
        clearAllMocks()
        FakeAndroidKeyStoreProvider.setup()
        credentialManager = CredentialManager(ApplicationProvider.getApplicationContext())

        mockkStatic(EncryptedSharedPreferences::class)

        mockSharedPreferences = mockkClass(EncryptedSharedPreferences::class)
        mockSharedPreferencesEditor = mockkClass(SharedPreferences.Editor::class)
        every { mockSharedPreferencesEditor.apply() }.returns(Unit)
        every { mockSharedPreferencesEditor.commit() }.returns(true)

        every { mockSharedPreferences.edit() }.returns(mockSharedPreferencesEditor)
        every {
            mockSharedPreferences.getString(
                CredentialKeys.CURRENT_TENANT_ID.toString(),
                null
            )
        }.returns(null)

        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(mockSharedPreferences)

        credentialManagerWithMockSharedPreferences =
            CredentialManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `save should call putString, apply, and commit`() {
        every { mockSharedPreferencesEditor.putString(any(), any()) }.returns(
            mockSharedPreferencesEditor
        )

        credentialManagerWithMockSharedPreferences.save(CredentialKeys.CODE_VERIFIER, "")

        verify { mockSharedPreferencesEditor.putString(any(), any()) }
        verify { mockSharedPreferencesEditor.apply() }
        verify { mockSharedPreferencesEditor.commit() }
    }

    @Test
    fun `save should call return true if save`() {
        every { mockSharedPreferencesEditor.putString(any(), any()) }.returns(
            mockSharedPreferencesEditor
        )
        every { mockSharedPreferencesEditor.commit() }.returns(true)

        val saved =
            credentialManagerWithMockSharedPreferences.save(CredentialKeys.CODE_VERIFIER, "")

        assert(saved)
    }

    @Test
    fun `save should call return true if not save`() {
        every { mockSharedPreferencesEditor.putString(any(), any()) }.returns(
            mockSharedPreferencesEditor
        )
        every { mockSharedPreferencesEditor.commit() }.returns(false)

        val saved =
            credentialManagerWithMockSharedPreferences.save(CredentialKeys.CODE_VERIFIER, "")

        assert(!saved)
    }

    @Test
    fun `get should call getString`() {
        every { mockSharedPreferences.getString(any(), any()) }.returns("")

        credentialManagerWithMockSharedPreferences.get(CredentialKeys.CODE_VERIFIER)

        verify { mockSharedPreferences.getString(any(), any()) }
    }

    @Test
    fun `get should return null if was not save before`() {
        every { mockSharedPreferences.getString(any(), any()) }.returns(null)

        val result = credentialManagerWithMockSharedPreferences.get(CredentialKeys.CODE_VERIFIER)

        assert(result == null)
    }

    @Test
    fun `get should return str`() {
        every { mockSharedPreferences.getString(any(), any()) }.returns("")

        val result = credentialManagerWithMockSharedPreferences.get(CredentialKeys.CODE_VERIFIER)

        assert(result == "")
    }

    @Test
    fun `clear should call remove, apply, and commit`() {
        every { mockSharedPreferencesEditor.remove(any()) }.returns(mockSharedPreferencesEditor)
        every { mockSharedPreferences.getString(any(), any()) }.returns(null)

        credentialManagerWithMockSharedPreferences.clear()

        verify { mockSharedPreferencesEditor.remove(any()) }
        verify { mockSharedPreferencesEditor.apply() }
        verify { mockSharedPreferencesEditor.commit() }
    }

    @Test
    fun `getCodeVerifier should return null if not saved`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }.returns(null)

        val result = credentialManagerWithMockSharedPreferences.getCodeVerifier()

        assert(result == null)
    }

    @Test
    fun `getCodeVerifier should return string`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }.returns("")

        val result = credentialManagerWithMockSharedPreferences.getCodeVerifier()

        assert(result == "")
    }

    @Test
    fun `getCodeVerifier should call getString with CredentialKeys_CODE_VERIFIER`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }.returns("")

        credentialManagerWithMockSharedPreferences.getCodeVerifier()

        verify {
            mockSharedPreferences.getString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }
    }

    @Test
    fun `saveCodeVerifier should call putString with CredentialKeys_CODE_VERIFIER`() {
        every {
            mockSharedPreferencesEditor.putString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }.returns(mockSharedPreferencesEditor)

        credentialManagerWithMockSharedPreferences.saveCodeVerifier("")

        verify {
            mockSharedPreferencesEditor.putString(
                CredentialKeys.CODE_VERIFIER.toString(),
                any()
            )
        }
    }


    @Test
    fun `getSelectedRegion should return null if not saved`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.SELECTED_REGION.toString(),
                null
            )
        }.returns(null)

        val result = credentialManagerWithMockSharedPreferences.getSelectedRegion()

        assert(result == null)
    }

    @Test
    fun `getSelectedRegion should return string`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.SELECTED_REGION.toString(),
                null
            )
        }.returns("")

        val result = credentialManagerWithMockSharedPreferences.getSelectedRegion()

        assert(result == "")
    }

    @Test
    fun `getSelectedRegion should call getString with CredentialKeys_CODE_VERIFIER`() {
        every {
            mockSharedPreferences.getString(
                CredentialKeys.SELECTED_REGION.toString(),
                null
            )
        }.returns("")

        credentialManagerWithMockSharedPreferences.getSelectedRegion()

        verify {
            mockSharedPreferences.getString(
                CredentialKeys.SELECTED_REGION.toString(),
                null
            )
        }
    }

    @Test
    fun `saveSelectedRegion should call putString with CredentialKeys_CODE_VERIFIER`() {
        every {
            mockSharedPreferencesEditor.putString(
                CredentialKeys.SELECTED_REGION.toString(),
                any()
            )
        }.returns(mockSharedPreferencesEditor)

        credentialManagerWithMockSharedPreferences.saveSelectedRegion("")

        verify {
            mockSharedPreferencesEditor.putString(
                CredentialKeys.SELECTED_REGION.toString(),
                any()
            )
        }
    }

    @Test
    fun `getCodeVerifier should return null if saveCodeVerifier was not called before`() {
        val result = credentialManager.getCodeVerifier()

        assert(result == null)
    }

    @Test
    fun `getCodeVerifier should return string if saveCodeVerifier was called before`() {
        credentialManager.saveCodeVerifier("Test Code Verifier")

        val result = credentialManager.getCodeVerifier()

        assert(result == "Test Code Verifier")
        credentialManager.clear()
    }

    @Test
    fun `getCodeVerifier should return null if clear was called before`() {
        credentialManager.saveCodeVerifier("Test Code Verifier")
        credentialManager.clear()

        val result = credentialManager.getCodeVerifier()

        assert(result == null)
    }

    @Test
    fun `getSelectedRegion should return null if saveCodeVerifier was not called before`() {
        val result = credentialManager.getSelectedRegion()

        assert(result == null)
    }

    @Test
    fun `getSelectedRegion should return string if saveCodeVerifier was called before`() {
        credentialManager.saveSelectedRegion("Test Selected Region")

        val result = credentialManager.getSelectedRegion()

        assert(result == "Test Selected Region")
        credentialManager.clear()
    }

    @Test
    fun `getSelectedRegion should return string if clear was called before`() {
        credentialManager.saveSelectedRegion("Test Code Verifier")
        credentialManager.clear()

        val result = credentialManager.getSelectedRegion()

        assert(result == "Test Code Verifier")
    }


    @Test
    fun `get should return null if saveCodeVerifier was not called before`() {
        val result = credentialManager.get(CredentialKeys.ACCESS_TOKEN)

        assert(result == null)
    }

    @Test
    fun `get should return string if saveCodeVerifier was called before`() {
        credentialManager.save(CredentialKeys.ACCESS_TOKEN, "Test Access Token")

        val result = credentialManager.get(CredentialKeys.ACCESS_TOKEN)

        assert(result == "Test Access Token")
        credentialManager.clear()
    }

    @Test
    fun `get_CredentialKeys_ACCESS_TOKEN should return null if clear was called before`() {
        credentialManager.save(CredentialKeys.ACCESS_TOKEN, "Test Access Token")
        credentialManager.clear()

        val result = credentialManager.get(CredentialKeys.ACCESS_TOKEN)

        assert(result == null)
    }

    @Test
    fun `get_CredentialKeys_CODE_VERIFIER should return null if clear was called before`() {
        credentialManager.save(CredentialKeys.CODE_VERIFIER, "Test CODE_VERIFIER")
        credentialManager.clear()

        val result = credentialManager.get(CredentialKeys.CODE_VERIFIER)

        assert(result == null)
    }

    @Test
    fun `get_CredentialKeys_REFRESH_TOKEN should return null if clear was called before`() {
        credentialManager.save(CredentialKeys.REFRESH_TOKEN, "Test REFRESH_TOKEN")
        credentialManager.clear()

        val result = credentialManager.get(CredentialKeys.REFRESH_TOKEN)

        assert(result == null)
    }
}