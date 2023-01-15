package com.frontegg.android

import android.util.Log
import com.frontegg.android.models.User
import com.frontegg.android.services.Api
import com.frontegg.android.services.CredentialManager
import com.frontegg.android.utils.CredentialKeys
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject


class ObservableValue<T>(value: T) {
    private class NullableObject<K>(var innerValue: K)

    val observable: PublishSubject<T> = PublishSubject.create()

    private var nullableObject: NullableObject<T>
    var value: T
        set(newValue) {
            nullableObject.innerValue = newValue
            observable.onNext(newValue)
        }
        get() {
            return nullableObject.innerValue
        }

    init {
        this.nullableObject = NullableObject(value)
    }

    fun subscribe(onNext: Consumer<T>): Disposable {
        return observable.subscribe(onNext)
    }
}

class FronteggAuth(
    val baseUrl: String,
    val clientId: String,
    val api: Api,
    val credentialManager: CredentialManager
) {

    companion object {
        private val TAG = FronteggAuth::class.java.simpleName

        val instance: FronteggAuth
            get() {
                return FronteggApp.getInstance().auth
            }
    }

    var accessToken: ObservableValue<String?> = ObservableValue(null)
    var refreshToken: ObservableValue<String?> = ObservableValue(null)
    val user: ObservableValue<User?> = ObservableValue(null)
    val isAuthenticated: ObservableValue<Boolean> = ObservableValue(false)
    val isLoading: ObservableValue<Boolean> = ObservableValue(true)
    val initializing: ObservableValue<Boolean> = ObservableValue(true)
    val showLoader: ObservableValue<Boolean> = ObservableValue(true)
    val externalLink: ObservableValue<Boolean> = ObservableValue(false);
    val pendingAppLink: String? = null

    init {

        Observable.merge(
            isLoading.observable,
            isAuthenticated.observable,
            initializing.observable,
        ).subscribe {
            showLoader.value = initializing.value || (!isAuthenticated.value && isLoading.value)
            Log.d(TAG, "showLoader subscription, ")
        }


        val accessToken = credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)
        val refreshToken = credentialManager.getOrNull(CredentialKeys.REFRESH_TOKEN)

        if (accessToken != null && refreshToken != null) {
            this.accessToken.value = accessToken
            this.refreshToken.value = refreshToken
            this.isLoading.value = false


            this.refreshTokenIfNeeded()
        } else {
            this.isLoading.value = false
            this.initializing.value = true
        }
    }

    private fun refreshTokenIfNeeded() {
        val refreshToken = this.refreshToken.value ?: return


        val data = api.refreshToken(refreshToken)

        Log.d("TEXT", "data: $data")
    }


}
