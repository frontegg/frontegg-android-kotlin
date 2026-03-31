package com.frontegg.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.frontegg.android.fronteggAuth
import com.frontegg.android.utils.NetworkGate
import com.frontegg.android.utils.NullableObject
import com.frontegg.demo.databinding.ActivityNavigationBinding
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class NavigationActivity : AppCompatActivity() {

    companion object {
        private val TAG = NavigationActivity::class.java.canonicalName
    }

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var navController: NavController
    private var e2eOverlay: View? = null
    private val e2eHandler = Handler(Looper.getMainLooper())
    private var e2eTicker: Runnable? = null
    private var e2eBadNetworkSinceMs: Long = 0L
    /** Require this many consecutive failed probes before treating the network as bad for the overlay (transient single-probe flakes on CI). */
    private val e2eConsecutiveNetFails = AtomicInteger(0)
    private val e2eNetProbeRunning = AtomicBoolean(false)
    private val e2eNetLastProbeMs = AtomicLong(0L)
    private val e2eProbeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "e2e-net-probe").apply { isDaemon = true }
    }

    /**
     * Configuration for authenticated users.
     * Defines the set of destinations that should be considered top-level when authenticated.
     */
    private val authenticatedTabs = AppBarConfiguration(
        setOf(
            R.id.navigation_home,
        )
    )

    /**
     * Configuration for non-authenticated users.
     * Defines the set of destinations that should be considered top-level when not authenticated.
     */
    private val nonAuthTabs = AppBarConfiguration(
        setOf(
            R.id.navigation_login
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_navigation) as NavHostFragment

        // Get the NavController
        navController = navHostFragment.navController


        // Set default navigation graph for non-authenticated users
        setupActionBarWithNavController(navController, nonAuthTabs)

        // Set the initial navigation graph
        if (savedInstanceState == null) {
            navController.setGraph(R.navigation.not_auth_navigation)
        }

        setToolbarVisibility(false)

        if (DemoEmbeddedTestMode.isEnabled) {
            val root = binding.root as ViewGroup
            e2eOverlay = layoutInflater.inflate(R.layout.e2e_no_connection_overlay, root, false)
            root.addView(
                e2eOverlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            e2eOverlay?.elevation = 100f
            e2eTicker = object : Runnable {
                override fun run() {
                    tickE2EUi()
                    e2eHandler.postDelayed(this, 400)
                }
            }
            e2eOverlay?.findViewById<View>(R.id.e2e_retry_connection_button)?.setOnClickListener {
                e2eBadNetworkSinceMs = 0L
                e2eConsecutiveNetFails.set(0)
                tickE2EUi()
            }
            e2eHandler.post(e2eTicker!!)
        }
    }

    private fun directPing(): Boolean {
        val baseUrl = try { fronteggAuth.baseUrl } catch (_: Exception) { return true }
        if (baseUrl.isBlank()) return true
        val probeUrl = "${baseUrl.trimEnd('/')}/test"
        return try {
            val conn = URL(probeUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.instanceFollowRedirects = false
            try {
                conn.responseCode in 200..499
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun scheduleNetProbeIfNeeded() {
        if (e2eNetProbeRunning.get()) return
        val now = System.currentTimeMillis()
        if (now - e2eNetLastProbeMs.get() < 2_000) return
        e2eNetProbeRunning.set(true)
        e2eProbeExecutor.execute {
            val good = try {
                val forceOff = NetworkGate.isE2eForceNetworkPathOffline()
                !forceOff && directPing()
            } catch (_: Exception) {
                true
            }
            if (good) {
                e2eConsecutiveNetFails.set(0)
            } else {
                e2eConsecutiveNetFails.incrementAndGet()
            }
            e2eNetLastProbeMs.set(System.currentTimeMillis())
            e2eNetProbeRunning.set(false)
        }
    }

    private fun tickE2EUi() {
        val overlay = e2eOverlay ?: return
        val initializing = try {
            fronteggAuth.initializing.value == true
        } catch (_: Exception) {
            false
        }
        if (initializing) {
            overlay.visibility = View.GONE
            return
        }
        val authenticated = try {
            fronteggAuth.isAuthenticated.value == true
        } catch (_: Exception) {
            false
        }
        scheduleNetProbeIfNeeded()
        val hardNetBad = e2eConsecutiveNetFails.get() >= 3
        val offlineFeatureOn = DemoEmbeddedTestMode.isOfflineModeFeatureEnabled(this)
        val now = System.currentTimeMillis()
        if (!hardNetBad) {
            e2eBadNetworkSinceMs = 0L
        } else {
            if (e2eBadNetworkSinceMs == 0L) e2eBadNetworkSinceMs = now
        }
        val debounceMs = if (authenticated) 2000L else 6000L
        val sustainedBad = e2eBadNetworkSinceMs > 0 && now - e2eBadNetworkSinceMs > debounceMs

        if (!authenticated && offlineFeatureOn && sustainedBad && hardNetBad) {
            overlay.visibility = View.VISIBLE
            overlay.findViewById<View>(R.id.e2e_no_connection_seen_ever)?.visibility = View.VISIBLE
        } else {
            overlay.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        e2eTicker?.let { e2eHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    /**
     * List of active RxJava subscriptions to be disposed of when the activity is paused.
     */
    private val disposables: ArrayList<Disposable> = arrayListOf()

    override fun onResume() {
        super.onResume()
        // Subscribe to Frontegg authentication events
        disposables.add(fronteggAuth.showLoader.subscribe(this.onShowLoaderChange))
        disposables.add(fronteggAuth.isAuthenticated.subscribe(this.onIsAuthenticatedChange))
    }

    override fun onPause() {
        super.onPause()
        // Dispose of subscriptions to avoid memory leaks
        disposables.forEach {
            it.dispose()
        }
    }

    /**
     * Keeps track of the last visibility state of the toolbar.
     */
    private var lastVisibilityState = View.VISIBLE

    /**
     * Observer for the "show loader" event.
     * Controls UI visibility while authentication is in progress.
     */
    private val onShowLoaderChange: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "showLoader: ${it.value}, initializing: ${fronteggAuth.initializing.value}")

        runOnUiThread {
            if (it.value) {
                // Hide toolbar and main container when loading
                supportActionBar?.hide()
                binding.container.visibility = View.GONE
            } else {
                // Show the container when loading is finished
                binding.container.visibility = View.VISIBLE
                if (lastVisibilityState == View.VISIBLE) {
                    supportActionBar?.show()
                }
            }
        }
    }

    /**
     * Observer for authentication status changes.
     * Updates the UI and navigation graph based on authentication state.
     */
    private val onIsAuthenticatedChange: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticated: ${it.value}")

        runOnUiThread {
            if (it.value) {
                // User is authenticated: switch to authenticated navigation graph
                setupActionBarWithNavController(navController, authenticatedTabs)
                navController.setGraph(R.navigation.navigation)

            } else {
                // User is not authenticated: switch to non-authenticated navigation graph
                setupActionBarWithNavController(navController, nonAuthTabs)
                navController.setGraph(R.navigation.not_auth_navigation)

            }
        }
    }

    /**
     * Controls the visibility of the toolbar based on authentication state.
     *
     * @param visible If `true`, the toolbar is shown; otherwise, it is hidden.
     */
    private fun setToolbarVisibility(visible: Boolean) {
        lastVisibilityState = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            supportActionBar?.show()
        } else {
            supportActionBar?.hide()
        }
    }
}