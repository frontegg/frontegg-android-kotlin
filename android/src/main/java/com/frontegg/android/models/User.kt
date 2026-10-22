package com.frontegg.android.models

class User {
    lateinit var id: String
    lateinit var email: String
    var mfaEnrolled: Boolean = false
    lateinit var name: String
    lateinit var profilePictureUrl: String
    var phoneNumber: String? = null
    var profileImage: String? = null
    lateinit var roles: List<UserRole>
    lateinit var permissions: List<UserRolePermission>
    lateinit var tenantId: String
    lateinit var tenantIds: List<String>
    lateinit var tenants: List<Tenant>
    lateinit var activeTenant: Tenant
    var activatedForTenant: Boolean = false
    var metadata: String? = null
    var verified: Boolean = false
    var superUser: Boolean = false

    // Gson leaves omitted lateinit fields unset, so equality must not read them.
    private fun comparedFields(): List<Any?> = listOf(
        if (this::id.isInitialized) id else null,
        if (this::email.isInitialized) email else null,
        mfaEnrolled,
        if (this::name.isInitialized) name else null,
        if (this::profilePictureUrl.isInitialized) profilePictureUrl else null,
        phoneNumber,
        profileImage,
        if (this::roles.isInitialized) roles else null,
        if (this::permissions.isInitialized) permissions else null,
        if (this::tenantId.isInitialized) tenantId else null,
        if (this::tenantIds.isInitialized) tenantIds else null,
        if (this::tenants.isInitialized) tenants else null,
        if (this::activeTenant.isInitialized) activeTenant else null,
        activatedForTenant,
        metadata,
        verified,
        superUser,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return comparedFields() == (other as User).comparedFields()
    }

    override fun hashCode(): Int = comparedFields().hashCode()
}