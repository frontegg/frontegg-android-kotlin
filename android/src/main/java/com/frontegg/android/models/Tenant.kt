package com.frontegg.android.models

class Tenant {
    public lateinit var id: String
    public lateinit var name: String
    public var creatorEmail: String? = null
    public var creatorName: String? = null
    public lateinit var tenantId: String
    public lateinit var createdAt: String
    public lateinit var updatedAt: String
    public var isReseller: Boolean = false
    public lateinit var metadata: String
    public lateinit var vendorId: String
    public var website: String? = null
}
