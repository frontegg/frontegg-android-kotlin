package com.frontegg.android.models

class UserRole {
    public lateinit var id: String
    public lateinit var key: String
    public var isDefault: Boolean = false
    public lateinit var name: String
    public var description: String? = null
    public lateinit var permissions: List<String>
    public var tenantId: String? = null
    public lateinit var vendorId: String
    public lateinit var createdAt: String
    public lateinit var updatedAt: String
}
