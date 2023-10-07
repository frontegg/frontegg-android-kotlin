package com.frontegg.demo.ui.home

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.frontegg.android.FronteggAuth
import com.frontegg.android.models.User

class HomeViewModel : ViewModel() {

    private val _user = MutableLiveData<User?>().apply {
        FronteggAuth.instance.user.subscribe {
            Handler(Looper.getMainLooper()).post {
                value = it.value
            }
        }
    }


    val user: LiveData<User?> = _user


}