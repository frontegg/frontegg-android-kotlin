package com.frontegg.android.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialKeysTest {

    @Test
    fun `all CredentialKeys enum values exist`() {
        val values = CredentialKeys.values()
        assertEquals(5, values.size)
    }

    @Test
    fun `CredentialKeys enum contains expected keys`() {
        val names = CredentialKeys.values().map { it.name }
        assert(names.contains("ACCESS_TOKEN"))
        assert(names.contains("REFRESH_TOKEN"))
        assert(names.contains("CODE_VERIFIER"))
        assert(names.contains("SELECTED_REGION"))
        assert(names.contains("CURRENT_TENANT_ID"))
    }
}
