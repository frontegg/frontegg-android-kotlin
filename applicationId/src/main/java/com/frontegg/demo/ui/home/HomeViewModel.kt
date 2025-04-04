package com.frontegg.demo.ui.home

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.User
import com.frontegg.android.utils.JWTHelper

class HomeViewModel : ViewModel() {

    // LiveData to store the current user object, initially set to null
    private val _user = MutableLiveData<User?>().apply {
        FronteggAuth.instance.user.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = it.value
            }
        }
    }

    // LiveData to store the current access token, initially set to null
    private val _applicationId = MutableLiveData<String?>().apply {
        FronteggAuth.instance.accessToken.subscribe { accessToken ->
            val accessTokenValue = accessToken.value
            if (accessTokenValue != null) {
                val jwt = JWTHelper.decode(accessTokenValue)
                Handler(Looper.getMainLooper()).post {
                    value = jwt.applicationId
                }
            }
        }
    }

    val user: LiveData<User?> = _user
    val applicationId: LiveData<String?> = _applicationId
}