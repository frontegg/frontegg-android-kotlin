package com.frontegg.android

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.ObservableField
import androidx.databinding.Observable
import com.frontegg.android.models.User
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager

class FronteggAuth(
    val baseUrl: String,
    val clientId: String,
    val api: Api,
    val credentialManager: CredentialManager
) : BaseObservable() {

    @get:Bindable
    val accessToken: ObservableField<String?> = ObservableField()
    val refreshToken: ObservableField<String?> = ObservableField()
    val user: ObservableField<User?> = ObservableField()
    val isAuthenticated: ObservableField<Boolean> = ObservableField(false)
    val isLoading: ObservableField<Boolean> = ObservableField(true)
    val initializing: ObservableField<Boolean> = ObservableField(true)
    val showLoader: ObservableField<Boolean> = ObservableField(true)
    val externalLink: ObservableField<Boolean> = ObservableField(false);
    val pendingAppLink: String? = null

    init {

    }
}

fun <T : Observable> T.addOnPropertyChanged(callback: (T) -> Unit): () -> Unit = {

    val changeCallback = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(observable: Observable?, i: Int) =
            callback(observable as T)
    }
    addOnPropertyChangedCallback(changeCallback)

    val unsubscribe: () -> Unit = {
        removeOnPropertyChangedCallback(changeCallback)
    }

    @Suppress("UNUSED_EXPRESSION")
    unsubscribe
}