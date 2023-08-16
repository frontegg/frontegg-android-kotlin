package com.frontegg.android.models

class User {
    public lateinit var id: String
    public lateinit var email: String
    public var mfaEnrolled: Boolean = false
    public lateinit var name: String
    public lateinit var profilePictureUrl: String
    public var phoneNumber: String? = null
    public var profileImage: String? = null
    public lateinit var roles: List<UserRole>
    public lateinit var permissions: List<UserRolePermission>
    public lateinit var tenantId: String
    public lateinit var tenantIds: List<String>
    public lateinit var tenants: List<Tenant>
    public lateinit var activeTenant: Tenant
    public var activatedForTenant: Boolean = false
    public var metadata: String? = null
    public var verified: Boolean = false
    public var superUser: Boolean = false
}