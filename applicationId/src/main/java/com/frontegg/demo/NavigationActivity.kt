package com.frontegg.demo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.frontegg.android.FronteggAuth
import com.frontegg.android.utils.NullableObject
import com.frontegg.demo.databinding.ActivityNavigationBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer

class NavigationActivity : AppCompatActivity() {

    companion object {
        private val TAG = NavigationActivity::class.java.canonicalName
    }

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var navController: NavController

    /**
     * Configuration for authenticated users.
     * Defines the set of destinations that should be considered top-level when authenticated.
     */
    private val authenticatedTabs = AppBarConfiguration(
        setOf(
            R.id.navigation_home,
            R.id.navigation_tenants,
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

        // Setup bottom navigation view and navigation controller
        val navView: BottomNavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_activity_navigation)

        // Attach navigation view to the navController
        navView.setupWithNavController(navController)

        // Set default navigation graph for non-authenticated users
        setupActionBarWithNavController(navController, nonAuthTabs)
        navController.setGraph(R.navigation.not_auth_navigation)
    }

    /**
     * List of active RxJava subscriptions to be disposed of when the activity is paused.
     */
    private val disposables: ArrayList<Disposable> = arrayListOf()

    override fun onResume() {
        super.onResume()
        // Subscribe to Frontegg authentication events
        disposables.add(FronteggAuth.instance.showLoader.subscribe(this.onShowLoaderChange))
        disposables.add(FronteggAuth.instance.isAuthenticated.subscribe(this.onIsAuthenticatedChange))
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
        Log.d(
            TAG,
            "showLoader: ${it.value}, initializing: ${FronteggAuth.instance.initializing.value}"
        )

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
                binding.navView.visibility = View.VISIBLE
                setToolbarVisibility(true)
            } else {
                // User is not authenticated: switch to non-authenticated navigation graph
                setupActionBarWithNavController(navController, nonAuthTabs)
                navController.setGraph(R.navigation.not_auth_navigation)
                binding.navView.visibility = View.GONE
                setToolbarVisibility(false)
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
