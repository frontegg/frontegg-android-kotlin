package com.frontegg.android.integrity

import android.content.Context
import com.frontegg.android.utils.DefaultDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide Play Integrity attestation, enabled by a non-zero Google Cloud project number.
 */
internal object DeviceIntegrity {

    @Volatile
    var attestor: DeviceIntegrityAttestor? = null
        private set

    @Volatile
    private var configuredProjectNumber: Long = 0L

    private val warmUpScope = CoroutineScope(SupervisorJob() + DefaultDispatcherProvider.io)

    fun configure(context: Context, cloudProjectNumber: Long) {
        configure(cloudProjectNumber) { PlayIntegrityProviderFactory(context.applicationContext, cloudProjectNumber) }
    }

    @Synchronized
    internal fun configure(cloudProjectNumber: Long, providerFactory: () -> IntegrityProviderFactory) {
        if (cloudProjectNumber == 0L) {
            attestor = null
            configuredProjectNumber = 0L
            return
        }
        if (attestor != null && configuredProjectNumber == cloudProjectNumber) return

        val newAttestor = DeviceIntegrityAttestor(providerFactory())
        attestor = newAttestor
        configuredProjectNumber = cloudProjectNumber
        warmUpScope.launch { newAttestor.warmUp() }
    }
}
