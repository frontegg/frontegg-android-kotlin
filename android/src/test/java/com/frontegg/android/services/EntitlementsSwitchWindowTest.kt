package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.entitlements.FeatureDetail
import com.frontegg.android.entitlements.Plan
import com.frontegg.android.entitlements.Treatment
import com.frontegg.android.entitlements.UserEntitlementsContext
import com.frontegg.android.models.Entitlement
import com.frontegg.android.models.EntitlementState
import com.frontegg.android.models.NotEntitledJustification
import com.frontegg.android.utils.FronteggCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Reproduces the tenant-switch "transient not-loaded" window that surfaced in
 * FR-24821 QA (Pavel): after a tenant switch the demo rendered the feature as
 * NOT entitled regardless of the new tenant, because the demo's auto-refresh
 * called `loadEntitlements(forceRefresh = false)` and read the verdict during
 * the window where the SDK had already cleared the previous tenant's cache but
 * not yet finished loading the new tenant's.
 *
 * These tests run on REAL threads (a dedicated single-thread "main" dispatcher
 * + `Dispatchers.IO`), not `Dispatchers.Unconfined`, because the bug is a
 * cross-thread ordering issue that `Unconfined` (which runs everything inline)
 * cannot reproduce.
 *
 * The exact ordering inside `updateStateWithCredentials` (the method
 * `switchTenant` ends up in) is:
 *   1. `user.value = newTenant`   ← fires the demo's user observer
 *   2. `entitlements.clear()`     ← cache emptied, hasLoadedOnce = false
 *   3. `loadEntitlements(forceRefresh = true)` ← async reload of the new tenant
 *
 * The demo observer (step 1) calls `loadEntitlements(forceRefresh = false)`.
 * That short-circuits on the still-true `hasLoadedOnce` and POSTS its completion
 * to the main dispatcher. By the time that completion runs and reads
 * `getFeatureEntitlements`, step 2 has emptied the cache and step 3's reload is
 * still in flight — so the read returns ENTITLEMENTS_NOT_LOADED / not-entitled.
 *
 * [forceRefresh_false_readsTransientNotLoadedDuringSwitchWindow] proves the
 * broken behavior; [forceRefresh_true_waitsForReloadAndReadsCorrectVerdict]
 * proves that the demo's fix (use forceRefresh = true on tenant change) reads
 * the correct verdict because its completion only fires after a real reload.
 */
class EntitlementsSwitchWindowTest {

    private val storageMock = mockk<FronteggInnerStorage>()
    private val credentialManagerMock = mockk<CredentialManager>(relaxed = true)
    private val apiMock = mockk<Api>()
    private lateinit var auth: FronteggAuthService
    private lateinit var mainExecutor: java.util.concurrent.ExecutorService

    private val invitebugToken = "invitebug-access-token"
    private val alphaToken = "alpha-access-token"

    /** invitebug: feature_a linked to a plan with defaultTreatment=false → NOT entitled. */
    private val invitebugState = EntitlementState(
        featureKeys = setOf("feature_a"),
        permissionKeys = emptySet(),
        context = UserEntitlementsContext(
            features = mapOf(
                "feature_a" to FeatureDetail(
                    planIds = listOf("plan-1"),
                    expireTime = null,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = mapOf("plan-1" to Plan(defaultTreatment = Treatment.FALSE, rules = null)),
            permissions = emptyMap()
        )
    )

    /** alpha: feature_a directly assigned (expireTime = -1 / NO_EXPIRATION_TIME) → entitled. */
    private val alphaState = EntitlementState(
        featureKeys = setOf("feature_a"),
        permissionKeys = emptySet(),
        context = UserEntitlementsContext(
            features = mapOf(
                "feature_a" to FeatureDetail(
                    planIds = listOf("plan-1"),
                    expireTime = FeatureDetail.NO_EXPIRATION_TIME,
                    linkedPermissions = emptyList(),
                    featureFlag = null
                )
            ),
            plans = mapOf("plan-1" to Plan(defaultTreatment = Treatment.FALSE, rules = null)),
            permissions = emptyMap()
        )
    )

    @Before
    fun setUp() {
        FronteggState.accessToken.value = null
        FronteggState.refreshToken.value = null
        FronteggState.user.value = null
        FronteggState.isAuthenticated.value = false
        FronteggState.isLoading.value = false

        mockkObject(StorageProvider)
        every { StorageProvider.getInnerStorage() }.returns(storageMock)
        every { storageMock.clientId }.returns("TestClientId")
        every { storageMock.applicationId }.returns("TestApplicationId")
        every { storageMock.baseUrl }.returns("https://base.url.com")
        every { storageMock.regions }.returns(listOf())
        every { storageMock.enableSessionPerTenant }.returns(false)
        every { storageMock.entitlementsEnabled }.returns(true)
        every { storageMock.tenantResolver }.returns(null)

        mockkObject(ApiProvider)
        every { ApiProvider.getApi(any()) }.returns(apiMock)
        every { apiMock.getServerUrl() }.returns("https://test.frontegg.com")

        val appLifecycle = mockk<FronteggAppLifecycle>()
        every { appLifecycle.startApp }.returns(FronteggCallback())
        every { appLifecycle.stopApp }.returns(FronteggCallback())
        val refreshTokenTimer = mockk<FronteggRefreshTokenTimer>(relaxed = true)
        every { refreshTokenTimer.refreshTokenIfNeeded }.returns(FronteggCallback())

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // REAL threads — a dedicated single-thread "main" dispatcher + IO. This is
        // what makes the cross-thread window observable; Unconfined would hide it.
        mainExecutor = Executors.newSingleThreadExecutor()
        auth = FronteggAuthService(
            credentialManager = credentialManagerMock,
            appLifecycle = appLifecycle,
            refreshTokenTimer = refreshTokenTimer,
            ioDispatcher = Dispatchers.IO,
            mainDispatcher = mainExecutor.asCoroutineDispatcher(),
            disableAutoRefresh = false
        )
        auth.isAuthenticated.value = true
    }

    @After
    fun tearDown() {
        mainExecutor.shutdownNow()
        io.mockk.unmockkAll()
    }

    /**
     * Primes the in-memory cache with invitebug's entitlements (feature_a NOT
     * entitled), exactly as a login on invitebug would.
     */
    private fun primeWithInvitebug() {
        auth.accessToken.value = invitebugToken
        every { apiMock.getUserEntitlements(accessTokenOverride = invitebugToken) }.returns(invitebugState)
        assertTrue(auth.entitlements.load(invitebugToken))
        assertTrue(auth.entitlements.hasLoadedOnce)
        assertFalse(
            "precondition: invitebug feature_a must be NOT entitled",
            auth.entitlements.checkFeature("feature_a").isEntitled
        )
    }

    /**
     * Mimics the SDK-internal ordering of `updateStateWithCredentials` during a
     * switch to alpha, run on a separate thread the way switchTenant does:
     *   user.value = alpha  →  entitlements.clear()  →  loadEntitlements(forceRefresh = true)
     *
     * The alpha entitlements load blocks on [releaseAlphaReload] so the test can
     * hold the reload "in flight" while it inspects what the demo observer read.
     */
    private fun simulateSwitchToAlphaInternals(releaseAlphaReload: CountDownLatch) {
        every { apiMock.getUserEntitlements(accessTokenOverride = alphaToken) }.answers {
            // Block until the test allows the reload to complete.
            releaseAlphaReload.await(5, TimeUnit.SECONDS)
            alphaState
        }
        Thread {
            // Order copied verbatim from updateStateWithCredentials.
            auth.accessToken.value = alphaToken
            auth.user.value = makeUser("alpha-tenant-id")   // fires the demo observer
            auth.entitlements.clear()
            auth.loadEntitlements(forceRefresh = true)
        }.start()
    }

    private fun makeUser(tenantId: String): com.frontegg.android.models.User {
        val tenant = com.frontegg.android.models.Tenant().apply {
            id = tenantId
            this.tenantId = tenantId
            name = tenantId
        }
        return com.frontegg.android.models.User().apply {
            id = "user-1"
            email = "user@test.com"
            name = "User"
            tenants = listOf(tenant)
            activeTenant = tenant
        }
    }

    @Test
    fun `forceRefresh_false reads transient not-loaded during switch window (reproduces FR-24821 demo bug)`() {
        primeWithInvitebug()

        val releaseAlphaReload = CountDownLatch(1)
        val captured = AtomicReference<Entitlement>()
        val demoRead = CountDownLatch(1)

        // The demo's CURRENT behavior: when the active tenant changes, call
        // loadEntitlements(forceRefresh = false) and read the verdict in the
        // completion.
        var lastTenantId: String? = "invitebug-tenant-id"
        auth.user.subscribe { nullable ->
            val u = nullable.value ?: return@subscribe
            val tid = u.activeTenant.tenantId
            if (lastTenantId != null && lastTenantId != tid) {
                auth.loadEntitlements(forceRefresh = false) {
                    captured.set(auth.getFeatureEntitlements("feature_a"))
                    demoRead.countDown()
                }
            }
            lastTenantId = tid
        }

        simulateSwitchToAlphaInternals(releaseAlphaReload)

        // The demo's read completes while alpha's reload is still blocked.
        assertTrue("demo read did not happen", demoRead.await(5, TimeUnit.SECONDS))

        val verdict = captured.get()
        // BUG: the demo observed feature_a as NOT entitled even though we are
        // switching TO the tenant that has it — because it read the cache during
        // the cleared-but-not-yet-reloaded window.
        assertFalse(
            "Reproduces FR-24821: forceRefresh=false read the transient empty cache; verdict was $verdict",
            verdict.isEntitled
        )
        assertEquals(NotEntitledJustification.ENTITLEMENTS_NOT_LOADED, verdict.justification)

        // Prove the data itself is fine: once the reload completes, the verdict is
        // correct. Only the demo's read TIMING was wrong.
        releaseAlphaReload.countDown()
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline &&
            !auth.getFeatureEntitlements("feature_a").isEntitled
        ) {
            Thread.sleep(20)
        }
        assertTrue(
            "after the reload finishes, alpha's feature_a must be entitled",
            auth.getFeatureEntitlements("feature_a").isEntitled
        )
    }

    @Test
    fun `forceRefresh_true waits for reload and reads the correct new-tenant verdict (demo fix)`() {
        primeWithInvitebug()

        // forceRefresh=true does not block on a server round-trip in this harness
        // (apiMock returns immediately once released), so release up-front; the
        // point is that loadEntitlements(forceRefresh=true) does NOT short-circuit
        // and its completion only fires after a real (re)load populates the cache.
        val releaseAlphaReload = CountDownLatch(0)
        val captured = AtomicReference<Entitlement>()
        val demoRead = CountDownLatch(1)

        var lastTenantId: String? = "invitebug-tenant-id"
        auth.user.subscribe { nullable ->
            val u = nullable.value ?: return@subscribe
            val tid = u.activeTenant.tenantId
            if (lastTenantId != null && lastTenantId != tid) {
                // The FIX: forceRefresh = true.
                auth.loadEntitlements(forceRefresh = true) {
                    captured.set(auth.getFeatureEntitlements("feature_a"))
                    demoRead.countDown()
                }
            }
            lastTenantId = tid
        }

        simulateSwitchToAlphaInternals(releaseAlphaReload)

        assertTrue("demo read did not happen", demoRead.await(5, TimeUnit.SECONDS))

        val verdict = captured.get()
        assertTrue(
            "With forceRefresh=true the demo read alpha's real verdict; was $verdict",
            verdict.isEntitled
        )
        assertEquals(null, verdict.justification)
    }
}
