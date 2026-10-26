package com.frontegg.android.integrity

import android.content.Context
import android.util.Log
import com.frontegg.android.utils.DefaultDispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide Play Integrity attestation, enabled by a positive Google Cloud project number.
 */
internal object DeviceIntegrity {

    @Volatile
    var attestor: DeviceIntegrityAttestor? = null
        private set

    @Volatile
    private var configuredProjectNumber: Long = 0L

    private const val TAG = "DeviceIntegrity"

    private val warmUpFailureHandler = CoroutineExceptionHandler { _, error ->
        Log.w(TAG, "Play Integrity warm-up failed: ${error.javaClass.simpleName}")
    }

    private val warmUpScope = CoroutineScope(SupervisorJob() + DefaultDispatcherProvider.io + warmUpFailureHandler)

    fun configure(context: Context, cloudProjectNumber: Long) {
        configure(cloudProjectNumber) { PlayIntegrityProviderFactory(context.applicationContext, cloudProjectNumber) }
    }

    @Synchronized
    internal fun configure(cloudProjectNumber: Long, providerFactory: () -> IntegrityProviderFactory) {
        if (cloudProjectNumber <= 0L) {
            attestor = null
            configuredProjectNumber = 0L
            return
        }
        if (attestor != null && configuredProjectNumber == cloudProjectNumber) return

        val newAttestor = DeviceIntegrityAttestor(providerFactory()) { task -> warmUpScope.launch { task() } }
        attestor = newAttestor
        configuredProjectNumber = cloudProjectNumber
        newAttestor.warmUp()
    }
}
