package com.frontegg.demo.ui.tenants

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.Tenant

class TenantsViewModel : ViewModel() {

    // LiveData to hold the list of tenants associated with the authenticated user
    private val _tenants = MutableLiveData<List<Tenant>>().apply {
        FronteggAuth.instance.user.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = (it.value?.tenants ?: listOf())
            }
        }
    }

    // LiveData to hold the currently active tenant for the authenticated user
    private val _activeTenant = MutableLiveData<Tenant?>().apply {
        FronteggAuth.instance.user.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = it.value?.activeTenant
            }
        }
    }

    val tenants: LiveData<List<Tenant>> = _tenants
    val activeTenant: LiveData<Tenant?> = _activeTenant
}
