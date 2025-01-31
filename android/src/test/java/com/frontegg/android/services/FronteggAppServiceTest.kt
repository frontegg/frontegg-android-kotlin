package com.frontegg.android.services

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
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
        FakeAndroidKeyStoreProvider.setup()

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(mockStorage)

        mockkConstructor(FronteggAuthService::class)

        every { anyConstructed<FronteggAuthService>().initializeSubscriptions() } just Runs
        every { anyConstructed<FronteggAuthService>().refreshTokenWhenNeeded() } just Runs
        every { anyConstructed<FronteggAuthService>().refreshTokenIfNeeded() }.returns(true)
    }

    @Test
    fun `getInstance should throw FronteggException_FRONTEGG_APP_MUST_BE_INITIALIZED`() {
        try {
            FronteggApp.getInstance()
        } catch (e: FronteggException) {
            assert(e.message == FronteggException.FRONTEGG_APP_MUST_BE_INITIALIZED)
        }
    }

    @Test
    fun `init should setUp instance field`() {
        mockkObject(FronteggApp)
        every { FronteggApp.init(any(), any(), any()) } just Runs
        every { FronteggApp.getInstance() } returns mockk()

        FronteggApp.init(
            fronteggDomain = fronteggDomain,
            clientId = fronteggClientId,
            context = ApplicationProvider.getApplicationContext(),
        )

        try {
            FronteggApp.getInstance()
            assert(true)
        } catch (e: FronteggException) {
            assert(false)
        }

        verify { FronteggApp.init(any(), any(), any()) }
        verify { FronteggApp.getInstance() }

        unmockkAll()
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
                packageName = "com.frontegg.android.test"
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
                packageName = "com.frontegg.android.test"
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

        val fronteggApp = FronteggApp.getInstance()

        fronteggApp.initWithRegion("key 1")
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
            val fronteggApp = FronteggApp.getInstance()
            fronteggApp.initWithRegion("key 1")
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
            val fronteggApp = FronteggApp.getInstance()
            fronteggApp.initWithRegion("key 3")
            assert(false)
        } catch (e: RuntimeException) {
            assert(true)
        }
    }
}