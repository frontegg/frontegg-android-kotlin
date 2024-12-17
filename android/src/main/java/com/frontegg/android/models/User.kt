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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (email != other.email) return false
        if (mfaEnrolled != other.mfaEnrolled) return false
        if (name != other.name) return false
        if (profilePictureUrl != other.profilePictureUrl) return false
        if (phoneNumber != other.phoneNumber) return false
        if (profileImage != other.profileImage) return false
        if (roles != other.roles) return false
        if (permissions != other.permissions) return false
        if (tenantId != other.tenantId) return false
        if (tenantIds != other.tenantIds) return false
        if (tenants != other.tenants) return false
        if (activeTenant != other.activeTenant) return false
        if (activatedForTenant != other.activatedForTenant) return false
        if (metadata != other.metadata) return false
        if (verified != other.verified) return false
        if (superUser != other.superUser) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + mfaEnrolled.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + profilePictureUrl.hashCode()
        result = 31 * result + (phoneNumber?.hashCode() ?: 0)
        result = 31 * result + (profileImage?.hashCode() ?: 0)
        result = 31 * result + roles.hashCode()
        result = 31 * result + permissions.hashCode()
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + tenantIds.hashCode()
        result = 31 * result + tenants.hashCode()
        result = 31 * result + activeTenant.hashCode()
        result = 31 * result + activatedForTenant.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + verified.hashCode()
        result = 31 * result + superUser.hashCode()
        return result
    }
}