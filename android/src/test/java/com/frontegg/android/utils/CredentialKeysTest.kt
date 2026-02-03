package com.frontegg.android.utils

import org.junit.Test

class CredentialKeysTest {

    @Test
    fun `ACCESS_TOKEN enum exists`() {
        assert(CredentialKeys.ACCESS_TOKEN != null)
    }

    @Test
    fun `REFRESH_TOKEN enum exists`() {
        assert(CredentialKeys.REFRESH_TOKEN != null)
    }

    @Test
    fun `CODE_VERIFIER enum exists`() {
        assert(CredentialKeys.CODE_VERIFIER != null)
    }

    @Test
    fun `SELECTED_REGION enum exists`() {
        assert(CredentialKeys.SELECTED_REGION != null)
    }

    @Test
    fun `CURRENT_TENANT_ID enum exists`() {
        assert(CredentialKeys.CURRENT_TENANT_ID != null)
    }

    @Test
    fun `all credential keys are unique enum values`() {
        val keys = CredentialKeys.values()
        
        val uniqueKeys = keys.toSet()
        
        assert(keys.size == uniqueKeys.size) { "Credential keys must be unique" }
    }

    @Test
    fun `enum has expected number of values`() {
        // ACCESS_TOKEN, REFRESH_TOKEN, CODE_VERIFIER, SELECTED_REGION, CURRENT_TENANT_ID
        assert(CredentialKeys.values().size == 5)
    }

    @Test
    fun `enum values have meaningful names`() {
        val keyNames = CredentialKeys.values().map { it.name }
        
        assert(keyNames.contains("ACCESS_TOKEN"))
        assert(keyNames.contains("REFRESH_TOKEN"))
        assert(keyNames.contains("CODE_VERIFIER"))
        assert(keyNames.contains("SELECTED_REGION"))
        assert(keyNames.contains("CURRENT_TENANT_ID"))
    }

    @Test
    fun `enum values can be retrieved by name`() {
        assert(CredentialKeys.valueOf("ACCESS_TOKEN") == CredentialKeys.ACCESS_TOKEN)
        assert(CredentialKeys.valueOf("REFRESH_TOKEN") == CredentialKeys.REFRESH_TOKEN)
        assert(CredentialKeys.valueOf("CODE_VERIFIER") == CredentialKeys.CODE_VERIFIER)
    }
}
