package com.frontegg.android

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontegg.android.exceptions.FronteggException
import com.frontegg.android.regions.RegionConfig
import com.frontegg.android.testUtils.FakeAndroidKeyStoreProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FronteggAppTest {
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

        assert(fronteggApp.baseUrl == "https://$fronteggDomain")
        assert(fronteggApp.clientId == fronteggClientId)
        assert(fronteggApp.applicationId == applicationId)
        assert(fronteggApp.useAssetsLinks)
        assert(fronteggApp.useChromeCustomTabs)
        assert(fronteggApp.mainActivityClass == Activity::class.java)
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
        assert(fronteggApp.regions.count() == 2)
        assert(fronteggApp.regions.first().key == "key 1")
        assert(fronteggApp.regions.last().key == "key 2")
        assert(fronteggApp.baseUrl == "")
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
        assert(fronteggApp.baseUrl == "https://frontegg.test.com 1")
        assert(fronteggApp.clientId == "Test Client Id 1")
        assert(fronteggApp.applicationId == "Application Id 1")

        fronteggApp.initWithRegion("key 2")
        assert(fronteggApp.baseUrl == "https://frontegg.test.com 2")
        assert(fronteggApp.clientId == "Test Client Id 2")
        assert(fronteggApp.applicationId == "Application Id 2")
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
}