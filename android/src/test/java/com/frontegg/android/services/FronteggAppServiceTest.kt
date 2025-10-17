package com.frontegg.android.services

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.android.FronteggApp
import com.frontegg.android.fronteggApp
import com.frontegg.android.fronteggAuth
import com.frontegg.android.models.FronteggConstants
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import com.frontegg.android.utils.FronteggConstantsProvider
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FronteggAppServiceTest {
    private val fronteggDomain = "frontegg.test.com"
    private val fronteggClientId = "Test Client Id"
    private val applicationId = "Application Id"
    private val mainActivityClass = Activity::class.java

    private val mockStorage = mockk<FronteggInnerStorage>()

    @Before
    fun setUp() {
        clearAllMocks()
        FakeAndroidKeyStoreProvider.setup()

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)
        every { mockStorage.baseUrl }.returns("https://test.frontegg.com")

        mockkConstructor(FronteggAuthService::class)

        every { anyConstructed<FronteggAuthService>().initializeSubscriptions() } just Runs
        every { anyConstructed<FronteggAuthService>().refreshTokenWhenNeeded() } just Runs
        every { anyConstructed<FronteggAuthService>().refreshTokenIfNeeded() }.returns(true)
        every { anyConstructed<FronteggAuthService>().processQueuedRequests() } just Runs
    }

    @Test
    fun `init should setUp instance field`() {
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(listOf())

        FronteggApp.init(
            fronteggDomain = fronteggDomain,
            clientId = fronteggClientId,
            context = ApplicationProvider.getApplicationContext(),
        )

        assert(FronteggApp.instance != null)
    }

    @Test
    fun `if instance is null getter fronteggApp should call init method`() {
        val mockContext = mockk<Context>()
        mockkObject(FronteggApp)
        mockkObject(FronteggConstantsProvider)
        val mockConstants = mockk<FronteggConstants>()
        every { mockConstants.toMap() } returns mapOf()
        every { mockConstants.baseUrl } returns ""
        every { mockConstants.clientId } returns ""
        every { mockConstants.applicationId } returns null
        every { mockConstants.useAssetsLinks } returns false
        every { mockConstants.useChromeCustomTabs } returns false
        every { mockConstants.deepLinkScheme } returns null
        every { mockConstants.useDiskCacheWebview } returns false
        every { mockConstants.mainActivityClass } returns null
        every { mockConstants.disableAutoRefresh } returns false
        every { FronteggApp.instance } returns null
        every {
            FronteggApp.init(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        every { FronteggConstantsProvider.fronteggConstants(any()) } returns mockConstants

        try {
            mockContext.fronteggApp
        } catch (e: Exception) {
            // ignore
        }

        verify { FronteggApp.init(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        unmockkObject(FronteggApp)
    }

    @Test
    fun `if instance is null getter fronteggAuth should call init method`() {
        val mockContext = mockk<Context>()
        mockkObject(FronteggApp)
        mockkObject(FronteggConstantsProvider)
        val mockConstants = mockk<FronteggConstants>()
        every { mockConstants.toMap() } returns mapOf()
        every { mockConstants.baseUrl } returns ""
        every { mockConstants.clientId } returns ""
        every { mockConstants.applicationId } returns null
        every { mockConstants.useAssetsLinks } returns false
        every { mockConstants.useChromeCustomTabs } returns false
        every { mockConstants.deepLinkScheme } returns null
        every { mockConstants.useDiskCacheWebview } returns false
        every { mockConstants.mainActivityClass } returns null
        every { mockConstants.disableAutoRefresh } returns false
        every { FronteggApp.instance } returns null
        every {
            FronteggApp.init(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        every { FronteggConstantsProvider.fronteggConstants(any()) } returns mockConstants

        try {
            mockContext.fronteggAuth
        } catch (e: Exception) {
            // ignore
        }

        verify { FronteggApp.init(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        unmockkObject(FronteggApp)
    }

    @Test
    fun `init should initialize all FronteggApp fields`() {
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(listOf())

        FronteggApp.init(
            fronteggDomain = fronteggDomain,
            clientId = fronteggClientId,
            context = ApplicationProvider.getApplicationContext(),
            applicationId = applicationId,
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        verify {
            mockStorage.fill(
                "https://$fronteggDomain",
                fronteggClientId,
                applicationId,
                true,
                listOf(),
                null,
                handleLoginWithSocialLogin = true,
                handleLoginWithSocialLoginProvider = true,
                handleLoginWithCustomSocialLoginProvider = true,
                customUserAgent = null,
                handleLoginWithSSO = false,
                shouldPromptSocialLoginConsent = true,
                useAssetsLinks = true,
                useChromeCustomTabs = true,
                mainActivityClass = mainActivityClass,
                packageName = "com.frontegg.android.test",
                deepLinkScheme = null,
                useDiskCacheWebview = false
            )
        }
    }

    @Test
    fun `initWithRegions should initialize regions`() {
        val regions = listOf(
            RegionConfig(
                key = "key 1",
                baseUrl = "frontegg.test.com 1",
                clientId = "Test Client Id 1",
                applicationId = "Application Id 1",
            ),
            RegionConfig(
                key = "key 2",
                baseUrl = "frontegg.test.com 2",
                clientId = "Test Client Id 2",
                applicationId = "Application Id 2",
            )
        )
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(regions)
        every { mockStorage.selectedRegion }.returns(null)

        FronteggApp.initWithRegions(
            regions = regions,
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = mainActivityClass
        )

        verify {
            mockStorage.fill(
                "",
                "",
                null,
                true,
                regions,
                null,
                handleLoginWithSocialLogin = true,
                handleLoginWithSocialLoginProvider = true,
                handleLoginWithCustomSocialLoginProvider = true,
                customUserAgent = null,
                handleLoginWithSSO = false,
                shouldPromptSocialLoginConsent = true,
                useAssetsLinks = true,
                useChromeCustomTabs = true,
                mainActivityClass = mainActivityClass,
                packageName = "com.frontegg.android.test",
                deepLinkScheme = null,
                useDiskCacheWebview = false
            )
        }
    }

    @Test
    fun `initWithRegion should initialize baseUrl, clientId, and applicationId`() {
        val regions = listOf(
            RegionConfig(
                key = "key 1",
                baseUrl = "frontegg.test.com 1",
                clientId = "Test Client Id 1",
                applicationId = "Application Id 1",
            ),
            RegionConfig(
                key = "key 2",
                baseUrl = "frontegg.test.com 2",
                clientId = "Test Client Id 2",
                applicationId = "Application Id 2",
            )
        )
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(regions)
        every { mockStorage.selectedRegion }.returns(null)


        FronteggApp.initWithRegions(
            regions = regions,
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        val fronteggApp = FronteggApp.instance

        fronteggApp!!.initWithRegion("key 1")
        verify {
            mockStorage.fill(
                "https://frontegg.test.com 1",
                "Test Client Id 1",
                "Application Id 1",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }

        fronteggApp.initWithRegion("key 2")
        verify {
            mockStorage.fill(
                "https://frontegg.test.com 2",
                "Test Client Id 2",
                "Application Id 2",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `initWithRegion should throw RuntimeException if regions is empty`() {
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(listOf())
        every { mockStorage.selectedRegion }.returns(null)

        FronteggApp.initWithRegions(
            regions = listOf(),
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        try {
            val fronteggApp = FronteggApp.instance
            fronteggApp!!.initWithRegion("key 1")
            assert(false)
        } catch (e: RuntimeException) {
            assert(true)
        }
    }

    @Test
    fun `initWithRegion should throw RuntimeException if regionKey is not exists in regions`() {
        val regions = listOf(
            RegionConfig(
                key = "key 1",
                baseUrl = "frontegg.test.com 1",
                clientId = "Test Client Id 1",
                applicationId = "Application Id 1",
            ),
            RegionConfig(
                key = "key 2",
                baseUrl = "frontegg.test.com 2",
                clientId = "Test Client Id 2",
                applicationId = "Application Id 2",
            )
        )
        every {
            mockStorage.fill(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.returns(Unit)

        every { mockStorage.clientId }.returns(fronteggClientId)
        every { mockStorage.applicationId }.returns(applicationId)
        every { mockStorage.regions }.returns(regions)
        every { mockStorage.selectedRegion }.returns(null)

        FronteggApp.initWithRegions(
            regions = regions,
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        try {
            val fronteggApp = FronteggApp.instance
            fronteggApp!!.initWithRegion("key 3")
            assert(false)
        } catch (e: RuntimeException) {
            assert(true)
        }
    }
}