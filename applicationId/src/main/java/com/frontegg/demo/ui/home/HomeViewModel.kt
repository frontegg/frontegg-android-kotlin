package com.frontegg.demo.ui.home

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.User

class HomeFragmentFactory(private val fronteggAuth: FronteggAuth) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(fronteggAuth) as T
    }
}

class HomeViewModel(
    private val fronteggAuth: FronteggAuth
) : ViewModel() {

    // LiveData to store the current user object, initially set to null
    private val _user = MutableLiveData<User?>().apply {
        fronteggAuth.user.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = it.value
            }
        }
    }

    // LiveData to store the current access token, initially set to null
    private val _accessToken = MutableLiveData<String?>().apply {
        fronteggAuth.accessToken.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = it.value
            }
        }
    }


    val user: LiveData<User?> = _user
}