package com.frontegg.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.frontegg.android.FronteggAuth
import com.frontegg.android.utils.NullableObject
import com.frontegg.demo.databinding.ActivityNavigationBinding
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer

class NavigationActivity : AppCompatActivity() {

    companion object {
        private val TAG = NavigationActivity::class.java.canonicalName
    }

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var navController: NavController


    private val authenticatedTabs = AppBarConfiguration(
        setOf(
            R.id.navigation_home, R.id.navigation_tenants
        )
    )
    private val nonAuthTabs = AppBarConfiguration(
        setOf(
            R.id.navigation_login
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        navController = findNavController(R.id.nav_host_fragment_activity_navigation)

        navView.setupWithNavController(navController)
        setupActionBarWithNavController(navController, nonAuthTabs)

        navController.setGraph(R.navigation.not_auth_navigation)

    }

    private val disposables: ArrayList<Disposable> = arrayListOf()
    override fun onResume() {
        super.onResume()

        val fronteggAuth = FronteggAuth.instance

        disposables.add(fronteggAuth.showLoader.subscribe(this.onShowLoaderChange))
        disposables.add(fronteggAuth.isAuthenticated.subscribe(this.onIsAuthenticatedChange))


        if(fronteggAuth.isMultiRegion && fronteggAuth.selectedRegion == null){
            startActivity(Intent( this, RegionSelectionActivity::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        disposables.forEach {
            it.dispose()
        }
    }


    private var lastVisibilityState = View.VISIBLE
    private val onShowLoaderChange: Consumer<NullableObject<Boolean>> = Consumer {

        Log.d(TAG, "showLoader: ${it.value}")
        runOnUiThread {
            if (it.value) {
                supportActionBar?.hide()
                binding.container.visibility = View.GONE
            } else {
                binding.container.visibility = View.VISIBLE
                if (lastVisibilityState == View.VISIBLE) {
                    supportActionBar?.show()
                }
            }
        }
    }

    private val onIsAuthenticatedChange: Consumer<NullableObject<Boolean>> = Consumer {
        Log.d(TAG, "isAuthenticated: ${it.value}")
        runOnUiThread {
            if (it.value) {
                setupActionBarWithNavController(navController, authenticatedTabs)
                navController.setGraph(R.navigation.navigation)
                binding.navView.visibility = View.VISIBLE
                setToolbarVisibility(true)
            } else {
                setupActionBarWithNavController(navController, nonAuthTabs)
                navController.setGraph(R.navigation.not_auth_navigation)
                binding.navView.visibility = View.GONE
                setToolbarVisibility(false)
            }
        }
    }


    private fun setToolbarVisibility(visible: Boolean) {
        lastVisibilityState = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            supportActionBar?.show()
        } else {
            supportActionBar?.hide()
        }
    }


}