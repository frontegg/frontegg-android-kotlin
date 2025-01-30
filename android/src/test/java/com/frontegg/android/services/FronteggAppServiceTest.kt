package com.frontegg.android.services

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.android.FronteggApp
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FronteggAppServiceTest {
    private val fronteggDomain = "frontegg.test.com"
    private val fronteggClientId = "Test Client Id"
    private val applicationId = "Application Id"

    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()
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
    }

    @Test
    fun `init should initialize all FronteggApp fields`() {
        FronteggApp.init(
            fronteggDomain = fronteggDomain,
            clientId = fronteggClientId,
            context = ApplicationProvider.getApplicationContext(),
            applicationId = applicationId,
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        val fronteggApp = FronteggApp.getInstance()

        assert(fronteggApp.auth.baseUrl == "https://$fronteggDomain")
        assert(fronteggApp.auth.clientId == fronteggClientId)
        assert(fronteggApp.auth.applicationId == applicationId)
        assert(fronteggApp.auth.useAssetsLinks)
        assert(fronteggApp.auth.useChromeCustomTabs)
        assert(fronteggApp.auth.mainActivityClass == Activity::class.java)
    }

    @Test
    fun `initWithRegions should initialize regions`() {
        FronteggApp.initWithRegions(
            regions = listOf(
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
            ),
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        val fronteggApp = FronteggApp.getInstance()
        assert(fronteggApp.auth.regions.count() == 2)
        assert(fronteggApp.auth.regions.first().key == "key 1")
        assert(fronteggApp.auth.regions.last().key == "key 2")
        assert(fronteggApp.auth.baseUrl == "")
    }

    @Test
    fun `initWithRegion should initialize baseUrl, clientId, and applicationId`() {
        FronteggApp.initWithRegions(
            regions = listOf(
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
            ),
            context = ApplicationProvider.getApplicationContext(),
            useAssetsLinks = true,
            useChromeCustomTabs = true,
            mainActivityClass = Activity::class.java
        )

        val fronteggApp = FronteggApp.getInstance()

        fronteggApp.initWithRegion("key 1")
        assert(fronteggApp.auth.baseUrl == "https://frontegg.test.com 1")
        assert(fronteggApp.auth.clientId == "Test Client Id 1")
        assert(fronteggApp.auth.applicationId == "Application Id 1")

        fronteggApp.initWithRegion("key 2")
        assert(fronteggApp.auth.baseUrl == "https://frontegg.test.com 2")
        assert(fronteggApp.auth.clientId == "Test Client Id 2")
        assert(fronteggApp.auth.applicationId == "Application Id 2")
    }

    @Test
    fun `initWithRegion should throw RuntimeException if regions is empty`() {
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
        FronteggApp.initWithRegions(
            regions = listOf(
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
            ),
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

    //
//    @Test
//    fun `reinitWithRegion should set up FronteggAuth_baseUrl`() {
//        val regionConfig = RegionConfig(
//            key = "key1",
//            baseUrl = "TestBaseUrl",
//            clientId = "TestClientId",
//            applicationId = "TestApplicationId"
//        )
//        auth.reinitWithRegion(regionConfig)
//
//        assert(auth.baseUrl == "https://TestBaseUrl")
//    }
//
//    @Test
//    fun `reinitWithRegion should set up FronteggAuth_clientId`() {
//        val regionConfig = RegionConfig(
//            key = "key1",
//            baseUrl = "TestBaseUrl",
//            clientId = "TestClientId",
//            applicationId = "TestApplicationId"
//        )
//        auth.reinitWithRegion(regionConfig)
//
//        assert(auth.clientId == "TestClientId")
//    }
//
//    @Test
//    fun `reinitWithRegion should set up FronteggAuth_applicationId`() {
//        val regionConfig = RegionConfig(
//            key = "key1",
//            baseUrl = "TestBaseUrl",
//            clientId = "TestClientId",
//            applicationId = "TestApplicationId"
//        )
//        auth.reinitWithRegion(regionConfig)
//
//        assert(auth.applicationId == "TestApplicationId")
//    }
}