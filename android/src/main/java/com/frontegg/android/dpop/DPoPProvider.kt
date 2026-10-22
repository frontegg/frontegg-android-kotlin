package com.frontegg.android.dpop

/** Process-wide DPoP state, enabled from SDK initialization. */
internal object DPoPProvider {

    @Volatile
    var isEnabled: Boolean = false
        private set

    val proofGenerator: DPoPProofGenerator by lazy {
        DPoPProofGenerator(AndroidKeyStoreDPoPKeyProvider())
    }

    fun configure(enabled: Boolean) {
        isEnabled = enabled
    }

    fun rotateKeyIfEnabled() {
        if (isEnabled) {
            proofGenerator.rotateKey()
        }
    }
}
